package com.hotel.housekeeping;

import com.hotel.dtos.HousekeepingAssigneeDTO;
import com.hotel.dtos.HousekeepingAssignRequest;
import com.hotel.dtos.HousekeepingCommandRequest;
import com.hotel.dtos.HousekeepingTaskDTO;
import com.hotel.entities.HousekeepingTask;
import com.hotel.entities.Room;
import com.hotel.entities.User;
import com.hotel.repositories.HousekeepingTaskRepository;
import com.hotel.repositories.RoomRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.services.PropertyAccessService;
import com.hotel.services.RoomStatePolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class HousekeepingQueueService {
    private static final Set<String> ACTIVE_STATUSES = Set.of("PENDING", "CLAIMED", "IN_PROGRESS");
    private static final Set<String> MANAGER_ROLES = Set.of(
            "SUPER_ADMIN", "ADMIN", "PROPERTY_OWNER", "HOTEL_ADMIN", "HOTEL_MANAGER", "RECEPTIONIST");

    private final HousekeepingTaskRepository taskRepository;
    private final RoomRepository roomRepository;
    private final UserPropertyRepository userPropertyRepository;
    private final PropertyAccessService propertyAccessService;
    private final Clock clock;
    private final Duration staleAfter;

    @Autowired
    public HousekeepingQueueService(
            HousekeepingTaskRepository taskRepository,
            RoomRepository roomRepository,
            UserPropertyRepository userPropertyRepository,
            PropertyAccessService propertyAccessService,
            @Value("${app.housekeeping.assignment-stale-minutes:30}") long staleMinutes) {
        this(taskRepository, roomRepository, userPropertyRepository, propertyAccessService,
                Clock.systemUTC(), Duration.ofMinutes(Math.max(1, staleMinutes)));
    }

    HousekeepingQueueService(
            HousekeepingTaskRepository taskRepository,
            RoomRepository roomRepository,
            UserPropertyRepository userPropertyRepository,
            PropertyAccessService propertyAccessService,
            Clock clock,
            Duration staleAfter) {
        this.taskRepository = taskRepository;
        this.roomRepository = roomRepository;
        this.userPropertyRepository = userPropertyRepository;
        this.propertyAccessService = propertyAccessService;
        this.clock = clock;
        this.staleAfter = staleAfter;
    }

    @Transactional(readOnly = true)
    public List<HousekeepingTaskDTO> list(Long hotelId, String status) {
        propertyAccessService.requireManagedHotel(hotelId);
        List<String> statuses = statuses(status);
        return taskRepository.findByHotelIdAndStatusInOrderByCreatedAtAsc(hotelId, statuses)
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<HousekeepingAssigneeDTO> assignees(Long hotelId) {
        propertyAccessService.requireManagedHotel(hotelId);
        return userPropertyRepository.findActiveHousekeepingUsers(hotelId).stream()
                .map(user -> new HousekeepingAssigneeDTO(user.getId(), user.getUsername(), user.getFullName()))
                .toList();
    }

    @Transactional
    public HousekeepingTaskDTO claim(Long taskId, HousekeepingCommandRequest request) {
        HousekeepingTask task = lockTask(taskId);
        User actor = propertyAccessService.currentUser();
        requireHousekeepingOperator(actor, task.getHotel().getId());
        checkVersion(task, request == null ? null : request.expectedVersion());
        LocalDateTime now = now();
        if ("COMPLETED".equals(task.getStatus())) {
            throw new IllegalStateException("Housekeeping task is already completed.");
        }
        if ("IN_PROGRESS".equals(task.getStatus()) && sameUser(task.getAssignedTo(), actor)) {
            return toDto(task);
        }
        if ("CLAIMED".equals(task.getStatus()) && sameUser(task.getAssignedTo(), actor)) {
            return toDto(task);
        }
        if (task.getAssignedTo() != null && !isStale(task, now)) {
            throw new IllegalStateException("Housekeeping task is currently assigned to another user.");
        }
        task.setAssignedTo(actor);
        task.setAssignedAt(now);
        task.setStatus("CLAIMED");
        task.setStartedAt(null);
        return toDto(taskRepository.saveAndFlush(task));
    }

    @Transactional
    public HousekeepingTaskDTO assign(Long taskId, HousekeepingAssignRequest request) {
        if (request == null || request.userId() == null) {
            throw new IllegalArgumentException("A housekeeping assignee is required.");
        }
        HousekeepingTask task = lockTask(taskId);
        User actor = propertyAccessService.currentUser();
        propertyAccessService.requireAccessibleOrNotFound(task.getHotel().getId(), "housekeeping task");
        checkVersion(task, request.expectedVersion());
        if (!canManageAssignments(actor) && !request.userId().equals(actor.getId())) {
            throw new IllegalStateException("Only property managers can reassign housekeeping tasks.");
        }
        User assignee = userPropertyRepository.findActiveHousekeepingUsers(task.getHotel().getId()).stream()
                .filter(user -> request.userId().equals(user.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("The assignee is not active for this property."));
        LocalDateTime now = now();
        if ("COMPLETED".equals(task.getStatus()) || "IN_PROGRESS".equals(task.getStatus())) {
            throw new IllegalStateException("Housekeeping task cannot be reassigned after work has started.");
        }
        if (task.getAssignedTo() != null && !sameUser(task.getAssignedTo(), assignee) && !isStale(task, now)) {
            throw new IllegalStateException("Housekeeping task is currently assigned to another user.");
        }
        task.setAssignedTo(assignee);
        task.setAssignedAt(now);
        task.setStartedAt(null);
        task.setStatus("CLAIMED");
        return toDto(taskRepository.saveAndFlush(task));
    }

    @Transactional
    public HousekeepingTaskDTO start(Long taskId, HousekeepingCommandRequest request) {
        HousekeepingTask task = lockTask(taskId);
        User actor = propertyAccessService.currentUser();
        requireHousekeepingOperator(actor, task.getHotel().getId());
        checkVersion(task, request == null ? null : request.expectedVersion());
        if ("IN_PROGRESS".equals(task.getStatus()) && sameUser(task.getAssignedTo(), actor)) {
            return toDto(task);
        }
        if (!"CLAIMED".equals(task.getStatus()) || !sameUser(task.getAssignedTo(), actor)) {
            throw new IllegalStateException("Housekeeping task must be claimed by you before it can start.");
        }
        if (isStale(task, now())) {
            throw new IllegalStateException("Housekeeping claim is stale; claim the task again before starting.");
        }
        Room room = roomRepository.findByIdForUpdate(task.getRoom().getId())
                .orElseThrow(() -> new IllegalStateException("The room for this task is no longer available."));
        propertyAccessService.requireAccessibleOrNotFound(room.getHotel().getId(), "room");
        if (!RoomStatePolicy.DIRTY.equals(room.getHousekeepingStatus())) {
            throw new IllegalStateException("The room is not awaiting housekeeping.");
        }
        room.setHousekeepingStatus(RoomStatePolicy.CLEANING);
        room.setStatus(RoomStatePolicy.CLEANING);
        RoomStatePolicy.validate(room);
        roomRepository.saveAndFlush(room);
        task.setStatus("IN_PROGRESS");
        task.setStartedAt(now());
        return toDto(taskRepository.saveAndFlush(task));
    }

    @Transactional
    public HousekeepingTaskDTO complete(Long taskId, HousekeepingCommandRequest request) {
        HousekeepingTask task = lockTask(taskId);
        User actor = propertyAccessService.currentUser();
        requireHousekeepingOperator(actor, task.getHotel().getId());
        requireCompletionOwner(task, actor);
        if ("COMPLETED".equals(task.getStatus())) {
            return toDto(task);
        }
        if (request == null || request.expectedVersion() == null) {
            throw new IllegalArgumentException("Housekeeping task version is required.");
        }
        checkVersion(task, request.expectedVersion());
        if (!"IN_PROGRESS".equals(task.getStatus())) {
            throw new IllegalStateException("Housekeeping task must be in progress before completion.");
        }
        Room room = roomRepository.findByIdForUpdate(task.getRoom().getId())
                .orElseThrow(() -> new IllegalStateException("The room for this task is no longer available."));
        propertyAccessService.requireAccessibleOrNotFound(room.getHotel().getId(), "room");
        RoomStatePolicy.completeHousekeeping(room);
        roomRepository.saveAndFlush(room);
        task.setRoom(room);
        task.setStatus("COMPLETED");
        task.setCompletedAt(now());
        return toDto(taskRepository.saveAndFlush(task));
    }

    private HousekeepingTask lockTask(Long taskId) {
        if (taskId == null) throw new IllegalArgumentException("Housekeeping task id is required.");
        HousekeepingTask task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new com.hotel.exceptions.ResourceNotFoundException("Housekeeping task not found."));
        propertyAccessService.requireAccessibleOrNotFound(task.getHotel().getId(), "housekeeping task");
        return task;
    }

    private void requireHousekeepingOperator(User actor, Long hotelId) {
        propertyAccessService.requireAccessibleOrNotFound(hotelId, "housekeeping task");
        boolean role = actor.getRoles() != null && actor.getRoles().stream()
                .anyMatch(item -> "HOUSEKEEPING".equalsIgnoreCase(item.getCode()));
        boolean mapping = userPropertyRepository
                .findByUserIdAndHotelIdAndRelationshipType(actor.getId(), hotelId, "HOUSEKEEPING")
                .map(item -> "ACTIVE".equalsIgnoreCase(item.getStatus()))
                .orElse(false);
        if (!role && !mapping && !propertyAccessService.isSystemAdministrator()) {
            throw new IllegalStateException("The current user is not assigned to housekeeping for this property.");
        }
    }

    private boolean canManageAssignments(User actor) {
        if (propertyAccessService.isSystemAdministrator()) return true;
        return actor.getRoles() != null && actor.getRoles().stream()
                .map(role -> role.getCode() == null ? "" : role.getCode().toUpperCase(Locale.ROOT))
                .anyMatch(MANAGER_ROLES::contains);
    }

    private void requireCompletionOwner(HousekeepingTask task, User actor) {
        if (!sameUser(task.getAssignedTo(), actor)) {
            throw new IllegalStateException("Only the assigned housekeeper can complete this task.");
        }
    }

    private boolean isStale(HousekeepingTask task, LocalDateTime now) {
        return "CLAIMED".equals(task.getStatus())
                && task.getAssignedAt() != null
                && task.getAssignedAt().plus(staleAfter).isBefore(now);
    }

    private void checkVersion(HousekeepingTask task, Long expectedVersion) {
        if (expectedVersion != null && !expectedVersion.equals(task.getVersion())) {
            throw new IllegalStateException("Housekeeping task changed; reload before retrying.");
        }
    }

    private boolean sameUser(User left, User right) {
        return left != null && right != null && left.getId() != null && left.getId().equals(right.getId());
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private List<String> statuses(String requested) {
        if (requested == null || requested.isBlank()) return List.copyOf(ACTIVE_STATUSES);
        String normalized = requested.trim().toUpperCase(Locale.ROOT);
        if (!ACTIVE_STATUSES.contains(normalized) && !"COMPLETED".equals(normalized)) {
            throw new IllegalArgumentException("Unsupported housekeeping status.");
        }
        return List.of(normalized);
    }

    private HousekeepingTaskDTO toDto(HousekeepingTask task) {
        User assignee = task.getAssignedTo();
        Room room = task.getRoom();
        return new HousekeepingTaskDTO(
                task.getId(),
                task.getHotel() == null ? null : task.getHotel().getId(),
                room == null ? null : room.getId(),
                room == null ? null : room.getRoomNumber(),
                task.getReservation() == null ? null : task.getReservation().getId(),
                task.getStatus(),
                assignee == null ? null : assignee.getId(),
                assignee == null ? null : assignee.getUsername(),
                assignee == null ? null : assignee.getFullName(),
                task.getAssignedAt(),
                task.getStartedAt(),
                task.getCompletedAt(),
                task.getNote(),
                task.getVersion(),
                isStale(task, now()),
                room == null ? null : room.getStatus(),
                room == null ? null : room.getHousekeepingStatus(),
                room == null ? null : room.getMaintenanceStatus(),
                RoomStatePolicy.isAssignable(room));
    }
}
