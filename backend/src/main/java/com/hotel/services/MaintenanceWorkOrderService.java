package com.hotel.services;

import com.hotel.dtos.MaintenanceWorkOrderDTO;
import com.hotel.entities.MaintenanceWorkOrder;
import com.hotel.entities.MaintenanceWorkOrderHistory;
import com.hotel.entities.Room;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.MaintenanceWorkOrderHistoryRepository;
import com.hotel.repositories.MaintenanceWorkOrderRepository;
import com.hotel.repositories.ReservationRoomRepository;
import com.hotel.repositories.RoomRepository;
import com.hotel.repositories.UserPropertyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MaintenanceWorkOrderService {
    static final String OPEN = "OPEN";
    static final String IN_PROGRESS = "IN_PROGRESS";
    static final String COMPLETED = "COMPLETED";
    static final String CANCELLED = "CANCELLED";
    private static final Set<String> PRIORITIES = Set.of("LOW", "NORMAL", "HIGH", "URGENT");
    private static final List<String> ACTIVE_STATUSES = List.of(OPEN, IN_PROGRESS);

    private final MaintenanceWorkOrderRepository repository;
    private final MaintenanceWorkOrderHistoryRepository historyRepository;
    private final RoomRepository roomRepository;
    private final ReservationRoomRepository reservationRoomRepository;
    private final PropertyAccessService propertyAccessService;
    private final UserPropertyRepository userPropertyRepository;

    @Transactional(readOnly = true)
    public List<MaintenanceWorkOrderDTO> list(Long propertyId, Long roomId) {
        if (propertyId == null) throw new IllegalArgumentException("propertyId is required.");
        propertyAccessService.requireAccessibleOrNotFound(propertyId, "property");
        List<MaintenanceWorkOrder> rows = roomId == null
                ? repository.findByHotelIdOrderByIdDesc(propertyId)
                : repository.findByHotelIdAndRoomIdOrderByIdDesc(propertyId, roomId);
        return rows.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public MaintenanceWorkOrderDTO get(Long id) {
        MaintenanceWorkOrder order = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Maintenance work order not found."));
        requireAccess(order);
        return toDto(order);
    }

    @Transactional
    public MaintenanceWorkOrderDTO create(MaintenanceWorkOrderDTO request) {
        validateRequest(request);
        Room room = roomRepository.findByIdForUpdate(request.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("Room not found."));
        propertyAccessService.requireAccessibleOrNotFound(room.getHotel().getId(), "room");
        if (request.getPropertyId() != null && !request.getPropertyId().equals(room.getHotel().getId())) {
            throw new ResourceNotFoundException("Room not found.");
        }
        if (repository.existsByRoomIdAndStatusIn(room.getId(), ACTIVE_STATUSES)) {
            throw new IllegalStateException("Room already has an active maintenance work order.");
        }
        if (request.getAssigneeUserId() != null && !userPropertyRepository.existsByUserIdAndHotelIdAndStatus(
                request.getAssigneeUserId(), room.getHotel().getId(), "ACTIVE")) {
            throw new IllegalArgumentException("Assignee must have an active assignment at this property.");
        }
        MaintenanceWorkOrder order = new MaintenanceWorkOrder();
        order.setHotel(room.getHotel());
        order.setRoom(room);
        order.setReason(request.getReason().trim());
        order.setPriority(normalizePriority(request.getPriority()));
        order.setAssigneeUserId(request.getAssigneeUserId());
        order.setScheduledStart(request.getScheduledStart());
        order.setScheduledEnd(request.getScheduledEnd());
        order.setStatus(OPEN);
        order.setResolutionNote(clean(request.getResolutionNote()));
        order = repository.save(order);
        appendHistory(order, null, OPEN, "Work order created: " + order.getReason());
        return toDto(order);
    }

    @Transactional
    public MaintenanceWorkOrderDTO start(Long id) {
        MaintenanceWorkOrder order = locked(id);
        requireStatus(order, OPEN);
        if (hasBookingImpact(order.getRoom())) {
            throw new IllegalStateException("Maintenance cannot start while the room has an active booking assignment.");
        }
        Room room = roomRepository.findByIdForUpdate(order.getRoom().getId()).orElseThrow();
        RoomStatePolicy.startMaintenance(room);
        roomRepository.save(room);
        transition(order, IN_PROGRESS, "Maintenance started");
        order.setStartedAt(LocalDateTime.now());
        return toDto(repository.save(order));
    }

    @Transactional
    public MaintenanceWorkOrderDTO complete(Long id, MaintenanceWorkOrderDTO request) {
        MaintenanceWorkOrder order = locked(id);
        requireStatus(order, IN_PROGRESS);
        Room room = roomRepository.findByIdForUpdate(order.getRoom().getId()).orElseThrow();
        RoomStatePolicy.completeMaintenance(room);
        roomRepository.save(room);
        order.setResolutionNote(request == null ? order.getResolutionNote() : clean(request.getResolutionNote()));
        order.setCompletedAt(LocalDateTime.now());
        transition(order, COMPLETED, order.getResolutionNote() == null ? "Maintenance completed" : order.getResolutionNote());
        return toDto(repository.save(order));
    }

    @Transactional
    public MaintenanceWorkOrderDTO reopen(Long id, MaintenanceWorkOrderDTO request) {
        MaintenanceWorkOrder order = locked(id);
        if (!Set.of(COMPLETED, CANCELLED).contains(order.getStatus())) {
            throw new IllegalStateException("Only a completed or cancelled work order can be reopened.");
        }
        String reason = request == null ? null : clean(request.getReason());
        if (reason == null) throw new IllegalArgumentException("A reopen reason is required.");
        order.setReason(reason);
        order.setCompletedAt(null);
        transition(order, OPEN, "Reopened: " + reason);
        return toDto(repository.save(order));
    }

    @Transactional
    public MaintenanceWorkOrderDTO cancel(Long id, MaintenanceWorkOrderDTO request) {
        MaintenanceWorkOrder order = locked(id);
        if (!ACTIVE_STATUSES.contains(order.getStatus())) {
            throw new IllegalStateException("Only an open or in-progress work order can be cancelled.");
        }
        String reason = request == null ? null : clean(request.getReason());
        if (reason == null) throw new IllegalArgumentException("A cancellation reason is required.");
        if (IN_PROGRESS.equals(order.getStatus())) {
            Room room = roomRepository.findByIdForUpdate(order.getRoom().getId()).orElseThrow();
            RoomStatePolicy.completeMaintenance(room);
            roomRepository.save(room);
        }
        transition(order, CANCELLED, "Cancelled: " + reason);
        return toDto(repository.save(order));
    }

    private MaintenanceWorkOrder locked(Long id) {
        MaintenanceWorkOrder order = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Maintenance work order not found."));
        requireAccess(order);
        return order;
    }

    private void transition(MaintenanceWorkOrder order, String to, String reason) {
        String from = order.getStatus();
        order.setStatus(to);
        appendHistory(order, from, to, reason);
    }

    private void appendHistory(MaintenanceWorkOrder order, String from, String to, String reason) {
        MaintenanceWorkOrderHistory history = new MaintenanceWorkOrderHistory();
        history.setHotel(order.getHotel());
        history.setWorkOrder(order);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setReason(reason);
        historyRepository.save(history);
    }

    private void requireAccess(MaintenanceWorkOrder order) {
        propertyAccessService.requireAccessibleOrNotFound(order.getHotel().getId(), "maintenance work order");
    }

    private boolean hasBookingImpact(Room room) {
        return reservationRoomRepository.hasActiveAssignment(
                room.getId(), RoomAvailabilityService.RELEASED_RESERVATION_STATUSES);
    }

    private void requireStatus(MaintenanceWorkOrder order, String expected) {
        if (!expected.equals(order.getStatus())) {
            throw new IllegalStateException("Work order must be " + expected + " for this transition.");
        }
    }

    private void validateRequest(MaintenanceWorkOrderDTO request) {
        if (request == null || request.getRoomId() == null || clean(request.getReason()) == null) {
            throw new IllegalArgumentException("Room and reason are required.");
        }
        normalizePriority(request.getPriority());
        if (request.getScheduledStart() != null && request.getScheduledEnd() != null
                && !request.getScheduledEnd().isAfter(request.getScheduledStart())) {
            throw new IllegalArgumentException("scheduledEnd must be after scheduledStart.");
        }
    }

    private String normalizePriority(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!PRIORITIES.contains(normalized)) throw new IllegalArgumentException("Unsupported maintenance priority.");
        return normalized;
    }

    private String clean(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim();
    }

    private MaintenanceWorkOrderDTO toDto(MaintenanceWorkOrder order) {
        MaintenanceWorkOrderDTO dto = new MaintenanceWorkOrderDTO();
        dto.setId(order.getId());
        dto.setPropertyId(order.getHotel().getId());
        dto.setRoomId(order.getRoom().getId());
        dto.setRoomNumber(order.getRoom().getRoomNumber());
        dto.setReason(order.getReason());
        dto.setPriority(order.getPriority());
        dto.setAssigneeUserId(order.getAssigneeUserId());
        dto.setScheduledStart(order.getScheduledStart());
        dto.setScheduledEnd(order.getScheduledEnd());
        dto.setStatus(order.getStatus());
        dto.setResolutionNote(order.getResolutionNote());
        dto.setBookingImpact(hasBookingImpact(order.getRoom()));
        dto.setVersion(order.getVersion());
        dto.setHistory(historyRepository.findByWorkOrderIdOrderByIdAsc(order.getId()).stream()
                .map(item -> new MaintenanceWorkOrderDTO.HistoryItem(
                        item.getFromStatus(), item.getToStatus(), item.getReason(), item.getCreatedAt()))
                .toList());
        return dto;
    }
}
