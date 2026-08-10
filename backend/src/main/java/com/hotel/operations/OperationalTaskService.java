package com.hotel.operations;

import com.hotel.entities.User;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.UserRepository;
import com.hotel.security.ActionCode;
import com.hotel.security.CustomUserDetails;
import com.hotel.services.PropertyAccessService;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class OperationalTaskService {
    private final OperationalTaskRepository repository;
    private final OperationalTaskHistoryRepository historyRepository;
    private final OperationalTaskHandlerRegistry handlerRegistry;
    private final PropertyAccessService propertyAccessService;
    private final UserRepository userRepository;
    private final Clock clock = Clock.systemUTC();

    @Transactional(readOnly = true)
    public List<OperationalTaskView> list(Long hotelId, OperationalTask.Status status) {
        propertyAccessService.requireTenantAccessibleOrNotFound(hotelId, "tác vụ");
        return (status == null ? repository.findByHotelIdOrderByCreatedAtAsc(hotelId)
                : repository.findByHotelIdAndStatusOrderByCreatedAtAsc(hotelId, status)).stream()
                .map(this::view).toList();
    }

    @Transactional
    public OperationalTaskView claim(Long taskId, long expectedVersion) {
        OperationalTask task = requireTask(taskId, expectedVersion);
        requireTaskPermission(task, ActionCode.TASK_EXECUTE);
        String previous = task.getStatus().name();
        User actor = propertyAccessService.currentUser();
        task.claim(actor, now());
        append(task, previous, "Task claimed", null, actor);
        return view(repository.save(task));
    }

    @Transactional
    public OperationalTaskView execute(Long taskId, ExecuteCommand command) {
        OperationalTask task = requireTask(taskId, command.expectedVersion());
        requireTaskPermission(task, task.getRequiredAction());
        User actor = propertyAccessService.currentUser();
        OperationalTaskHandlerRegistry.Result result = handlerRegistry.execute(task, command.command(), command.payload());
        String previous = task.getStatus().name();
        task.complete(actor, result.reference(), command.reason() == null ? result.note() : command.reason(), now());
        append(task, previous, command.reason(), result.reference(), actor);
        return view(repository.save(task));
    }

    @Transactional
    public OperationalTaskView reassign(Long taskId, ReassignCommand command) {
        OperationalTask task = requireTask(taskId, command.expectedVersion());
        requireTaskPermission(task, ActionCode.APPROVE);
        User actor = propertyAccessService.currentUser();
        User assignee = userRepository.findById(command.assigneeUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người được giao."));
        String previous = task.getStatus().name();
        task.assign(assignee, actor, now());
        append(task, previous, command.reason(), null, actor);
        return view(repository.save(task));
    }

    @Transactional
    public OperationalTaskView block(Long taskId, BlockCommand command) {
        OperationalTask task = requireTask(taskId, command.expectedVersion());
        requireTaskPermission(task, ActionCode.APPROVE);
        User actor = propertyAccessService.currentUser();
        String previous = task.getStatus().name();
        task.block(command.reason());
        append(task, previous, command.reason(), null, actor);
        return view(repository.save(task));
    }

    private OperationalTask requireTask(Long id, long expectedVersion) {
        OperationalTask task = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tác vụ."));
        propertyAccessService.requireTenantAccessibleOrNotFound(task.getHotel().getId(), "tác vụ");
        if (task.getVersion() != expectedVersion) throw new IllegalStateException("TASK_VERSION_CONFLICT");
        return task;
    }

    private void requireTaskPermission(OperationalTask task, int action) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication != null && authentication.getPrincipal() instanceof CustomUserDetails details)) {
            throw new SecurityException("TASK_PERMISSION_REVOKED");
        }
        Integer mask = details.getPermissionMasks().get(task.getFunctionCode());
        if (mask == null || (mask & ActionCode.VIEW) == 0 || (mask & action) != action) {
            throw new SecurityException("TASK_PERMISSION_REVOKED");
        }
    }

    private void append(OperationalTask task, String previous, String reason, String result, User actor) {
        historyRepository.save(new OperationalTaskHistory(task.getId(), previous, task.getStatus().name(), actor.getId(),
                reason, result, MDC.get("correlationId"), now()));
    }

    private LocalDateTime now() { return LocalDateTime.now(clock.withZone(ZoneOffset.UTC)); }
    private OperationalTaskView view(OperationalTask task) {
        return new OperationalTaskView(task.getId(), task.getPublicId(), task.getHotel().getId(), task.getTaskType(),
                task.getFunctionCode().name(), task.getRequiredAction(), task.getAggregateType(), task.getAggregateId(),
                task.getStatus().name(), task.getAssignedTo() == null ? null : task.getAssignedTo().getId(),
                task.getResultReference(), task.getVersion());
    }

    public record ExecuteCommand(long expectedVersion, String command, String reason, Object payload) { }
    public record ReassignCommand(long expectedVersion, Long assigneeUserId, String reason) {
        public ReassignCommand { Objects.requireNonNull(assigneeUserId); }
    }
    public record BlockCommand(long expectedVersion, String reason) { }
    public record OperationalTaskView(Long id, String publicId, Long hotelId, String taskType, String functionCode,
                                      int requiredAction, String aggregateType, String aggregateId, String status,
                                      Long assignedToUserId, String resultReference, long version) { }
}
