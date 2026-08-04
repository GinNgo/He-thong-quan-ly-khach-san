package com.hotel.services;

import com.hotel.domain.lifecycle.ReservationStatus;
import com.hotel.dtos.*;
import com.hotel.entities.*;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.propertycommerce.booking.DepositPolicySnapshot;
import com.hotel.propertycommerce.config.PropertyPaymentConfiguration;
import com.hotel.propertycommerce.config.PropertyPaymentConfigurationRepository;
import com.hotel.propertycommerce.checkout.CheckoutOperationsService;
import com.hotel.propertycommerce.invoice.InvoiceFinalizationService;
import com.hotel.propertycommerce.invoice.PropertyInvoice;
import com.hotel.propertycommerce.stay.CheckInPolicy;
import com.hotel.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ReservationDetailRepository reservationDetailRepository;
    private final ReservationRoomRepository reservationRoomRepository;
    private final RoomRepository roomRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final UserRepository userRepository;
    private final RoomAvailabilityService roomAvailabilityService;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final ReservationHoldService reservationHoldService;
    private final PropertyAccessService propertyAccessService;
    private final PropertyPaymentConfigurationRepository propertyPaymentConfigurationRepository;
    private final InvoiceFinalizationService invoiceFinalizationService;
    private final CheckoutOperationsService checkoutOperationsService;
    private final PublicInventoryEligibilityPolicy publicInventoryEligibilityPolicy;
    private final OperationalAuditService operationalAuditService;
    private final CheckInPolicy checkInPolicy;

    @Transactional
    public ReservationDTO createReservation(String username, ReservationRequest request) {
        return createReservation(username, request, null, null);
    }

    @Transactional
    public ReservationDTO createReservation(String username, ReservationRequest request,
                                            String idempotencyScope, String idempotencyKey) {
        validateReservationRequest(request);

        User user = username == null || username.isBlank()
                ? createGuestUser(request)
                : userRepository.findByUsername(username)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng đặt phòng."));

        RoomType roomType = roomTypeRepository.findByIdForUpdate(request.getRoomTypeId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy loại phòng."));
        publicInventoryEligibilityPolicy.requireSellableRoomTypeForBooking(roomType);
        Hotel hotel = roomType.getHotel();

        int quantity = request.getQuantity() == null ? 1 : request.getQuantity();
        int adults = request.getAdults() != null ? request.getAdults() : request.getGuests();
        int children = request.getChildren() == null ? 0 : request.getChildren();
        roomAvailabilityService.validateCapacity(roomType, quantity, adults, children);

        long available = roomAvailabilityService.countAvailableRooms(
                roomType.getId(), request.getCheckInDate(), request.getCheckOutDate()
        );
        if (available < quantity) {
            throw new IllegalStateException("Chỉ còn " + available + " phòng trống trong khoảng ngày đã chọn.");
        }

        long nights = roomAvailabilityService.getNights(request.getCheckInDate(), request.getCheckOutDate());
        BigDecimal totalAmount = roomAvailabilityService.calculateTotal(roomType.getBasePrice(), nights, quantity);
        PropertyPaymentConfiguration paymentConfiguration = propertyPaymentConfigurationRepository.findByHotelId(hotel.getId())
                .orElseThrow(() -> new FinancialException(
                        FinancialErrorCode.POLICY_NOT_CONFIGURED,
                        "Payment and deposit policy is not configured for this property."));
        if (!paymentConfiguration.isEnabled()) {
            throw new FinancialException(
                    FinancialErrorCode.PAYMENT_ENVIRONMENT_DISABLED,
                    "Property payment is currently disabled.");
        }
        DepositPolicySnapshot depositPolicySnapshot = DepositPolicySnapshot.capture(paymentConfiguration, totalAmount);

        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setRoom(null);
        reservation.setHotel(hotel);
        reservation.setCheckInDate(request.getCheckInDate());
        reservation.setCheckOutDate(request.getCheckOutDate());
        reservation.setGuests(adults + children);
        reservation.setStatus(ReservationStatus.PENDING_PAYMENT.name());
        reservation.setPaymentMethod(request.getPaymentMethod());
        reservation.setSpecialRequests(request.getSpecialRequests());
        reservation.setTotalAmount(totalAmount);
        reservation.setBookingIdempotencyScope(idempotencyScope);
        reservation.setBookingIdempotencyKey(idempotencyKey);
        reservation.captureDepositPolicy(depositPolicySnapshot);
        Reservation savedReservation = reservationRepository.save(reservation);

        ReservationDetail detail = new ReservationDetail();
        detail.setReservation(savedReservation);
        detail.setRoom(null);
        detail.setRoomType(roomType);
        detail.setQuantity(quantity);
        detail.setAdults(adults);
        detail.setChildren(children);
        detail.setPrice(roomType.getBasePrice());
        detail.setUnitPrice(roomType.getBasePrice());
        detail.setSubtotal(roomType.getBasePrice().multiply(BigDecimal.valueOf(nights * quantity)));
        reservationDetailRepository.save(detail);
        reservationHoldService.createHold(
                savedReservation.getId(),
                roomType.getId(),
                quantity,
                "RESERVATION-" + savedReservation.getId());

        appendReservationEvent(
                savedReservation,
                "RESERVATION_CREATED",
                "Đặt phòng được tạo từ báo giá xác thực của hệ thống.",
                null,
                reservationState(savedReservation));

        notificationService.sendSystemNotification(
                "BOOKING",
                "Có đặt phòng mới!",
                "Khách hàng " + user.getFullName() + " vừa đặt " + quantity + " " + roomType.getNameVi()
                        + " từ " + request.getCheckInDate() + " đến " + request.getCheckOutDate()
        );
        return mapToDTO(savedReservation);
    }

    @Transactional(readOnly = true)
    public List<ReservationDTO> getAllReservations() {
        if (propertyAccessService.isSystemAdministrator()) {
            return reservationRepository.findAll().stream().map(this::mapToDTO).toList();
        }
        Set<Long> hotelIds = propertyAccessService.accessibleHotelIds();
        if (hotelIds.isEmpty()) {
            return List.of();
        }
        return reservationRepository.findByHotelIdIn(hotelIds).stream().map(this::mapToDTO).toList();
    }

    @Transactional(readOnly = true)
    public ReservationPageDTO searchReservations(String status, String query, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Specification<Reservation> specification = Specification.where(null);
        if (!propertyAccessService.isSystemAdministrator()) {
            Set<Long> hotelIds = propertyAccessService.accessibleHotelIds();
            if (hotelIds.isEmpty()) {
                return new ReservationPageDTO(List.of(), safePage, safeSize, 0, 0);
            }
            specification = specification.and((root, ignored, cb) -> root.get("hotel").get("id").in(hotelIds));
        }
        specification = reservationFilters(specification, status, query);
        Page<ReservationDTO> result = reservationRepository.findAll(
                        specification,
                        PageRequest.of(safePage, safeSize,
                                Sort.by(Sort.Order.desc("checkInDate"), Sort.Order.desc("id"))))
                .map(reservation -> mapToDTO(reservation, false));
        return ReservationPageDTO.from(result);
    }

    @Transactional(readOnly = true)
    public ReservationDTO getReservationById(Long id) {
        Reservation reservation = findReservation(id);
        authorizeReservationView(reservation);
        return mapToDTO(reservation, true);
    }

    @Transactional(readOnly = true)
    public Optional<ReservationDTO> findByBookingIdempotency(String scope, String key) {
        if (scope == null || scope.isBlank() || key == null || key.isBlank()) {
            return Optional.empty();
        }
        return reservationRepository.findByBookingIdempotencyScopeAndBookingIdempotencyKey(scope, key)
                .map(this::mapToDTO);
    }

    @Transactional(readOnly = true)
    public List<ReservationDTO> getMyReservations(String username) {
        if (username == null) return List.of();
        return reservationRepository.findByUserUsernameOrderByIdDesc(username).stream()
                .map(this::mapToDTO).toList();
    }

    @Transactional(readOnly = true)
    public ReservationPageDTO searchMyReservations(String username, String status, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        if (username == null || username.isBlank()) {
            return new ReservationPageDTO(List.of(), safePage, safeSize, 0, 0);
        }
        Specification<Reservation> specification = (root, ignored, cb) ->
                cb.equal(root.get("user").get("username"), username);
        specification = reservationFilters(specification, status, null);
        Page<ReservationDTO> result = reservationRepository.findAll(
                        specification,
                        PageRequest.of(safePage, safeSize,
                                Sort.by(Sort.Order.desc("checkInDate"), Sort.Order.desc("id"))))
                .map(reservation -> mapToDTO(reservation, false));
        return ReservationPageDTO.from(result);
    }

    @Transactional
    public ReservationDTO assignRooms(Long reservationId, AssignRoomsRequest request) {
        List<Long> roomIds = request == null ? null : request.getRoomIds();
        return mutateRoomAssignment(reservationId, roomIds, "Đã xếp phòng cụ thể cho đặt phòng.", false);
    }

    @Transactional
    public ReservationDTO updateRoomAssignment(Long reservationId, RoomAssignmentMutationRequest request) {
        if (request == null) throw new IllegalArgumentException("Yêu cầu phân phòng là bắt buộc.");
        return mutateRoomAssignment(reservationId, request.roomIds(), requireAssignmentReason(request.reason()), true);
    }

    @Transactional
    public ReservationDTO releaseRoomAssignment(Long reservationId, RoomAssignmentReleaseRequest request) {
        if (request == null) throw new IllegalArgumentException("Yêu cầu giải phóng phòng là bắt buộc.");
        String reason = requireAssignmentReason(request.reason());
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new com.hotel.exceptions.ResourceNotFoundException("Không tìm thấy booking."));
        requireOperationalAccess(reservation);
        requireRoomAssignmentMutable(reservation);

        List<ReservationDetail> details = reservationDetailRepository.findByReservationId(reservationId);
        if (details.size() != 1 || details.get(0).getRoomType() == null) {
            throw new IllegalStateException("Luồng hiện tại chỉ hỗ trợ một loại phòng trong mỗi booking.");
        }
        ReservationDetail detail = details.get(0);
        List<ReservationRoom> currentAssignments = reservationRoomRepository
                .findByReservationDetailIdAndStatusForUpdate(detail.getId(), "ASSIGNED");
        if (currentAssignments.isEmpty()) return mapToDTO(reservation);
        List<Long> currentRoomIds = assignmentRoomIds(currentAssignments);
        Map<Long, Room> lockedRooms = lockAssignmentRooms(reservation, currentRoomIds);
        LocalDateTime releasedAt = LocalDateTime.now();
        currentAssignments.forEach(assignment -> {
            Room room = lockedRooms.get(assignment.getRoom().getId());
            RoomStatePolicy.releaseReservation(room);
            assignment.setRoom(room);
            assignment.setStatus("RELEASED");
            assignment.setReleasedAt(releasedAt);
        });
        roomRepository.saveAllAndFlush(currentRoomIds.stream().map(lockedRooms::get).toList());
        reservationRoomRepository.saveAllAndFlush(currentAssignments);

        detail.setRoom(null);
        reservation.setRoom(null);
        reservationDetailRepository.save(detail);
        reservationRepository.save(reservation);
        appendReservationEvent(
                reservation,
                "ROOMS_RELEASED",
                reason,
                Map.of("roomIds", currentRoomIds),
                Map.of("roomIds", List.of()));
        return mapToDTO(reservation);
    }

    @Transactional(readOnly = true)
    public Optional<ReservationDTO> findRoomAssignmentReplay(Long reservationId, List<Long> requestedRoomIds) {
        Reservation reservation = findReservation(reservationId);
        requireOperationalAccess(reservation);
        List<ReservationDetail> details = reservationDetailRepository.findByReservationId(reservationId);
        if (details.size() != 1) return Optional.empty();
        List<Long> targetRoomIds;
        try {
            targetRoomIds = normalizeAssignmentRoomIds(requestedRoomIds, details.get(0));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
        List<Long> currentRoomIds = assignmentRoomIds(reservationRoomRepository
                .findByReservationDetailIdAndStatus(details.get(0).getId(), "ASSIGNED"));
        return currentRoomIds.equals(targetRoomIds) ? Optional.of(mapToDTO(reservation)) : Optional.empty();
    }

    @Transactional(readOnly = true)
    public Optional<ReservationDTO> findRoomReleaseReplay(Long reservationId) {
        Reservation reservation = findReservation(reservationId);
        requireOperationalAccess(reservation);
        List<ReservationDetail> details = reservationDetailRepository.findByReservationId(reservationId);
        if (details.size() != 1) return Optional.empty();
        boolean released = reservationRoomRepository
                .findByReservationDetailIdAndStatus(details.get(0).getId(), "ASSIGNED")
                .isEmpty();
        return released ? Optional.of(mapToDTO(reservation)) : Optional.empty();
    }

    private ReservationDTO mutateRoomAssignment(
            Long reservationId,
            List<Long> requestedRoomIds,
            String reason,
            boolean allowReassignment) {
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new com.hotel.exceptions.ResourceNotFoundException("Không tìm thấy booking."));
        requireOperationalAccess(reservation);
        requireRoomAssignmentMutable(reservation);

        List<ReservationDetail> details = reservationDetailRepository.findByReservationId(reservationId);
        if (details.size() != 1 || details.get(0).getRoomType() == null) {
            throw new IllegalStateException("Luồng hiện tại chỉ hỗ trợ một loại phòng trong mỗi booking.");
        }
        ReservationDetail detail = details.get(0);
        List<Long> targetRoomIds = normalizeAssignmentRoomIds(requestedRoomIds, detail);
        List<ReservationRoom> currentAssignments = reservationRoomRepository
                .findByReservationDetailIdAndStatusForUpdate(detail.getId(), "ASSIGNED");
        List<Long> currentRoomIds = assignmentRoomIds(currentAssignments);
        if (currentRoomIds.equals(targetRoomIds)) return mapToDTO(reservation);
        if (!allowReassignment && !currentAssignments.isEmpty()) {
            throw new IllegalStateException("Booking đã được gán phòng. Dùng lệnh gán lại có lý do để thay đổi.");
        }

        List<Long> unionRoomIds = java.util.stream.Stream.concat(
                        currentRoomIds.stream(), targetRoomIds.stream())
                .distinct()
                .sorted()
                .toList();
        Map<Long, Room> lockedRooms = lockAssignmentRooms(reservation, unionRoomIds);
        Set<Long> targetSet = new HashSet<>(targetRoomIds);
        Set<Long> currentSet = new HashSet<>(currentRoomIds);
        List<Long> removedRoomIds = currentRoomIds.stream().filter(id -> !targetSet.contains(id)).toList();
        List<Long> addedRoomIds = targetRoomIds.stream().filter(id -> !currentSet.contains(id)).toList();
        LocalDateTime changedAt = LocalDateTime.now();

        for (Long roomId : addedRoomIds) {
            validateAssignableRoom(reservation, detail, lockedRooms.get(roomId));
        }

        List<ReservationRoom> changedAssignments = new java.util.ArrayList<>();
        currentAssignments.stream()
                .filter(assignment -> removedRoomIds.contains(assignment.getRoom().getId()))
                .forEach(assignment -> {
                    Room room = lockedRooms.get(assignment.getRoom().getId());
                    RoomStatePolicy.releaseReservation(room);
                    assignment.setRoom(room);
                    assignment.setStatus("RELEASED");
                    assignment.setReleasedAt(changedAt);
                    changedAssignments.add(assignment);
                });
        for (Long roomId : addedRoomIds) {
            Room room = lockedRooms.get(roomId);
            RoomStatePolicy.reserve(room);
            ReservationRoom assignment = new ReservationRoom();
            assignment.setReservationDetail(detail);
            assignment.setRoom(room);
            assignment.setAssignedAt(changedAt);
            assignment.setStayStartDate(reservation.getCheckInDate());
            assignment.setStayEndDate(reservation.getCheckOutDate());
            assignment.setStatus("ASSIGNED");
            changedAssignments.add(assignment);
        }

        List<Room> changedRooms = java.util.stream.Stream.concat(
                        removedRoomIds.stream(), addedRoomIds.stream())
                .distinct()
                .sorted()
                .map(lockedRooms::get)
                .toList();
        if (!changedRooms.isEmpty()) roomRepository.saveAllAndFlush(changedRooms);
        if (!changedAssignments.isEmpty()) reservationRoomRepository.saveAllAndFlush(changedAssignments);

        Room primaryRoom = lockedRooms.get(targetRoomIds.get(0));
        detail.setRoom(primaryRoom);
        reservation.setRoom(primaryRoom);
        reservationDetailRepository.save(detail);
        reservationRepository.save(reservation);
        appendReservationEvent(
                reservation,
                currentRoomIds.isEmpty() ? "ROOMS_ASSIGNED" : "ROOMS_REASSIGNED",
                reason,
                Map.of("roomIds", currentRoomIds),
                Map.of("roomIds", targetRoomIds));
        return mapToDTO(reservation);
    }

    private List<Long> normalizeAssignmentRoomIds(List<Long> requestedRoomIds, ReservationDetail detail) {
        if (requestedRoomIds == null || requestedRoomIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("Mã phòng vật lý phải là số dương.");
        }
        List<Long> roomIds = requestedRoomIds.stream().sorted().toList();
        int requiredQuantity = detail.getQuantity() == null ? 1 : detail.getQuantity();
        if (requiredQuantity < 1) {
            throw new IllegalStateException("Số lượng phòng của booking không hợp lệ.");
        }
        if (roomIds.size() != requiredQuantity || new HashSet<>(roomIds).size() != roomIds.size()) {
            throw new IllegalArgumentException("Số phòng được gán phải đúng bằng số lượng đã đặt và không được trùng.");
        }
        return roomIds;
    }

    private List<Long> assignmentRoomIds(List<ReservationRoom> assignments) {
        return assignments.stream()
                .map(ReservationRoom::getRoom)
                .map(Room::getId)
                .distinct()
                .sorted()
                .toList();
    }

    private Map<Long, Room> lockAssignmentRooms(Reservation reservation, List<Long> roomIds) {
        if (roomIds.isEmpty()) return Map.of();
        List<Room> rooms = roomRepository.findAllByHotelIdAndIdInForUpdate(
                reservation.getHotel().getId(), roomIds);
        if (rooms.size() != roomIds.size()) {
            throw new FinancialException(
                    FinancialErrorCode.CONCURRENT_MODIFICATION,
                    "Kho phòng đã thay đổi; hãy làm mới và thử lại.");
        }
        return rooms.stream().collect(Collectors.toMap(Room::getId, room -> room));
    }

    private void requireRoomAssignmentMutable(Reservation reservation) {
        if (!Set.of("PENDING", "PENDING_PAYMENT", "CONFIRMED").contains(reservation.getStatus())) {
            throw new IllegalStateException("Chỉ có thể phân phòng trước khi khách check-in.");
        }
    }

    private String requireAssignmentReason(String reason) {
        if (reason == null || reason.trim().length() < 3) {
            throw new IllegalArgumentException("Lý do phân phòng phải có ít nhất 3 ký tự.");
        }
        String normalized = reason.trim();
        if (normalized.length() > 500) {
            throw new IllegalArgumentException("Lý do phân phòng không được vượt quá 500 ký tự.");
        }
        return normalized;
    }

    @Transactional
    public ReservationDTO updateReservationStatus(Long id, String status) {
        Reservation reservation = reservationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new com.hotel.exceptions.ResourceNotFoundException("Không tìm thấy booking."));
        requireOperationalAccess(reservation);
        String normalizedStatus = status == null ? "" : status.trim().toUpperCase();
        if ("CHECKED_IN".equals(normalizedStatus)) {
            throw new IllegalArgumentException(
                    "Use the dedicated check-in endpoint so readiness and idempotency are enforced.");
        }

        if (normalizedStatus.equals(reservation.getStatus())) {
            reconcileReservationHold(id, normalizedStatus, LocalDateTime.now());
            return mapToDTO(reservation);
        }

        String previousStatus = reservation.getStatus();

        List<ReservationRoom> assignments = reservationRoomRepository.findByReservationDetailReservationId(id);

        if ("CHECKED_OUT".equals(normalizedStatus)) {
            completeCheckoutLocked(reservation, null);
            reconcileReservationHold(id, normalizedStatus, LocalDateTime.now());
            return mapToDTO(reservation);
        } else if ("CANCELLED".equals(normalizedStatus)) {
            cancelLockedReservation(reservation, assignments);
        } else if (RoomAvailabilityService.RELEASED_RESERVATION_STATUSES.contains(normalizedStatus)) {
            releaseAssignments(assignments);
        } else if ("CONFIRMED".equals(normalizedStatus) && reservation.getUser().getEmail() != null) {
            emailService.sendBookingConfirmation(
                    reservation.getUser().getEmail(), reservation.getUser().getFullName(), reservation.getId(),
                    reservation.getCheckInDate().toString(), reservation.getCheckOutDate().toString()
            );
        }

        reservation.setStatus(normalizedStatus);
        Reservation saved = reservationRepository.save(reservation);
        reconcileReservationHold(id, normalizedStatus, LocalDateTime.now());
        appendStatusChangedEvent(saved, previousStatus, normalizedStatus, "Trạng thái vận hành đã được cập nhật.");
        return mapToDTO(saved);
    }

    @Transactional(readOnly = true)
    public CheckInReadinessDTO getCheckInReadiness(Long id) {
        Reservation reservation = findReservation(id);
        requireOperationalAccess(reservation);
        List<ReservationDetail> details = reservationDetailRepository.findByReservationId(id);
        List<ReservationRoom> assignments = reservationRoomRepository
                .findByReservationDetailReservationId(id).stream()
                .filter(item -> "ASSIGNED".equals(item.getStatus()))
                .toList();
        List<Room> rooms = assignments.stream()
                .map(ReservationRoom::getRoom)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted(java.util.Comparator.comparing(Room::getId))
                .toList();
        return checkInReadiness(reservation, details, assignments, rooms);
    }

    @Transactional(readOnly = true)
    public Optional<ReservationDTO> findCheckInReplay(Long id) {
        Reservation reservation = findReservation(id);
        requireOperationalAccess(reservation);
        if (!"CHECKED_IN".equals(reservation.getStatus())) return Optional.empty();
        int requiredRooms = reservationDetailRepository.findByReservationId(id).stream()
                .mapToInt(detail -> detail.getQuantity() == null ? 1 : detail.getQuantity())
                .sum();
        List<ReservationRoom> assignments = reservationRoomRepository
                .findByReservationDetailReservationId(id).stream()
                .filter(item -> "ASSIGNED".equals(item.getStatus()))
                .toList();
        long occupiedRooms = assignments.stream()
                .map(ReservationRoom::getRoom)
                .filter(java.util.Objects::nonNull)
                .filter(room -> RoomStatePolicy.OCCUPIED.equals(room.getStatus()))
                .map(Room::getId)
                .distinct()
                .count();
        return requiredRooms > 0 && assignments.size() == requiredRooms && occupiedRooms == requiredRooms
                ? Optional.of(mapToDTO(reservation))
                : Optional.empty();
    }

    @Transactional
    public ReservationDTO checkIn(Long id) {
        Reservation reservation = reservationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new com.hotel.exceptions.ResourceNotFoundException("Không tìm thấy booking."));
        requireOperationalAccess(reservation);
        if ("CHECKED_IN".equals(reservation.getStatus())) {
            return findCheckInReplay(id).orElseThrow(() -> new FinancialException(
                    FinancialErrorCode.INVALID_STATE_TRANSITION,
                    "The checked-in reservation has an incomplete physical-room state.",
                    Map.of("checkInReadiness", "CHECKED_IN_STATE_INCOMPLETE"),
                    reservation.getStatus(),
                    null));
        }

        List<ReservationDetail> details = reservationDetailRepository.findByReservationId(id);
        List<ReservationRoom> assignments = reservationRoomRepository.findAssignedByReservationIdForUpdate(id);
        List<Long> roomIds = assignments.stream()
                .filter(item -> "ASSIGNED".equals(item.getStatus()))
                .map(ReservationRoom::getRoom)
                .filter(java.util.Objects::nonNull)
                .map(Room::getId)
                .distinct()
                .sorted()
                .toList();
        Map<Long, Room> lockedRooms = lockAssignmentRooms(reservation, roomIds);
        List<Room> rooms = roomIds.stream().map(lockedRooms::get).toList();
        CheckInReadinessDTO readiness = checkInReadiness(reservation, details, assignments, rooms);
        if (!readiness.ready()) {
            CheckInReadinessIssueDTO blocker = readiness.blockers().get(0);
            throw new FinancialException(
                    FinancialErrorCode.INVALID_STATE_TRANSITION,
                    blocker.message(),
                    Map.of("checkInReadiness", blocker.code()),
                    reservation.getStatus(),
                    null);
        }

        rooms.forEach(RoomStatePolicy::checkIn);
        roomRepository.saveAllAndFlush(rooms);
        reservation.setStatus("CHECKED_IN");
        Reservation saved = reservationRepository.save(reservation);
        reconcileReservationHold(id, "CHECKED_IN", LocalDateTime.now());
        appendStatusChangedEvent(
                saved,
                "CONFIRMED",
                "CHECKED_IN",
                "Đã xác nhận đủ điều kiện và nhận phòng.");
        return mapToDTO(saved);
    }

    @Transactional
    public ReservationDTO cancelOperational(Long id) {
        return updateReservationStatus(id, "CANCELLED");
    }

    @Transactional
    public ReservationDTO markNoShow(Long id) {
        return updateReservationStatus(id, "NO_SHOW");
    }

    @Transactional
    public ReservationDTO cancelMyReservation(Long id, String username) {
        Reservation reservation = reservationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new com.hotel.exceptions.ResourceNotFoundException("Không tìm thấy booking."));
        if (username == null || reservation.getUser() == null
                || !username.equals(reservation.getUser().getUsername())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Bạn không có quyền hủy booking này.");
        }
        if ("CANCELLED".equals(reservation.getStatus())) {
            reservationHoldService.releaseActiveHold(id, LocalDateTime.now());
            return mapToDTO(reservation);
        }

        cancelLockedReservation(
                reservation,
                reservationRoomRepository.findByReservationDetailReservationId(id));
        String previousStatus = reservation.getStatus();
        reservation.setStatus("CANCELLED");
        Reservation saved = reservationRepository.save(reservation);
        reservationHoldService.releaseActiveHold(id, LocalDateTime.now());
        appendStatusChangedEvent(saved, previousStatus, "CANCELLED", "Khách hàng đã hủy đặt phòng.");
        return mapToDTO(saved);
    }

    private void reconcileReservationHold(Long reservationId, String status, LocalDateTime now) {
        switch (status) {
            case "PENDING", "PENDING_PAYMENT" -> {
                // Pending payment keeps the inventory hold active.
            }
            case "EXPIRED" -> reservationHoldService.expireActiveHold(reservationId, now);
            case "CANCELLED", "REJECTED", "NO_SHOW" -> reservationHoldService.releaseActiveHold(reservationId, now);
            default -> reservationHoldService.consumeActiveHold(reservationId, now);
        }
    }

    private void cancelLockedReservation(Reservation reservation, List<ReservationRoom> assignments) {
        if (Set.of("CHECKED_IN", "CHECKED_OUT", "COMPLETED", "REJECTED", "EXPIRED", "NO_SHOW")
                .contains(reservation.getStatus())) {
            throw new IllegalStateException("Không thể hủy booking đã check-in hoặc kết thúc.");
        }

        paymentService.refundSuccessfulPayments(reservation.getId());
        releaseAssignments(assignments);
    }

    private void releaseAssignments(List<ReservationRoom> assignments) {
        List<ReservationRoom> activeAssignments = assignments.stream()
                .filter(item -> "ASSIGNED".equals(item.getStatus()))
                .toList();
        if (activeAssignments.isEmpty()) return;

        List<Long> roomIds = activeAssignments.stream()
                .map(ReservationRoom::getRoom)
                .map(Room::getId)
                .distinct()
                .sorted()
                .toList();
        java.util.Map<Long, Room> lockedRooms = roomRepository.findAllByIdForUpdate(roomIds).stream()
                .collect(Collectors.toMap(Room::getId, room -> room));
        if (lockedRooms.size() != roomIds.size()) {
            throw new FinancialException(
                    FinancialErrorCode.CONCURRENT_MODIFICATION,
                    "An assigned room changed during release; retry safely.");
        }

        LocalDateTime releasedAt = LocalDateTime.now();
        activeAssignments.forEach(item -> {
            Room lockedRoom = lockedRooms.get(item.getRoom().getId());
            RoomStatePolicy.releaseReservation(lockedRoom);
            item.setRoom(lockedRoom);
            item.setStatus("RELEASED");
            item.setReleasedAt(releasedAt);
        });
        roomRepository.saveAllAndFlush(lockedRooms.values().stream().toList());
        reservationRoomRepository.saveAllAndFlush(activeAssignments);
    }

    @Transactional(readOnly = true)
    public List<RoomDTO> getAvailableRooms(Long reservationId) {
        return getAvailableRoomContext(reservationId).candidates();
    }

    @Transactional(readOnly = true)
    public AvailableRoomContextDTO getAvailableRoomContext(Long reservationId) {
        Reservation reservation = findReservation(reservationId);
        requireOperationalAccess(reservation);
        if (Set.of("CHECKED_OUT", "COMPLETED", "CANCELLED", "REJECTED", "EXPIRED", "NO_SHOW")
                .contains(reservation.getStatus())) {
            throw new IllegalStateException("Không thể chọn phòng cho booking đã kết thúc hoặc bị hủy.");
        }
        List<ReservationDetail> details = reservationDetailRepository.findByReservationId(reservationId);
        if (details.size() != 1 || details.get(0).getRoomType() == null) {
            throw new IllegalStateException("Booking phải có đúng một loại phòng để chọn phòng vật lý.");
        }
        ReservationDetail detail = details.get(0);
        List<RoomDTO> assignedRooms = reservationRoomRepository
                .findByReservationDetailIdAndStatus(detail.getId(), "ASSIGNED").stream()
                .map(ReservationRoom::getRoom)
                .distinct()
                .sorted(java.util.Comparator.comparing(Room::getId))
                .map(this::availableRoomDto)
                .toList();
        List<Long> assignedRoomIds = assignedRooms.stream().map(RoomDTO::getId).toList();
        List<RoomDTO> candidates = roomRepository.findAvailableRoomsByRoomTypeAndDate(
                        reservation.getHotel().getId(),
                        detail.getRoomType().getId(),
                        List.of("MAINTENANCE", "OUT_OF_SERVICE", "DIRTY", "CLEANING", "OCCUPIED"),
                        RoomAvailabilityService.RELEASED_RESERVATION_STATUSES,
                        reservation.getCheckInDate(), reservation.getCheckOutDate()).stream()
                .filter(RoomStatePolicy::isAssignable)
                .map(this::availableRoomDto)
                .toList();
        return new AvailableRoomContextDTO(
                reservation.getId(),
                reservation.getHotel().getId(),
                detail.getRoomType().getId(),
                detail.getRoomType().getNameVi(),
                reservation.getCheckInDate(),
                reservation.getCheckOutDate(),
                detail.getQuantity() == null ? 1 : detail.getQuantity(),
                assignedRooms,
                assignedRoomIds,
                candidates);
    }

    private CheckInReadinessDTO checkInReadiness(
            Reservation reservation,
            List<ReservationDetail> details,
            List<ReservationRoom> assignments,
            List<Room> rooms) {
        CheckInPolicy.Window window = checkInPolicy.window(reservation);
        List<CheckInReadinessIssueDTO> blockers = new java.util.ArrayList<>();
        boolean alreadyCheckedIn = "CHECKED_IN".equals(reservation.getStatus());
        if (!alreadyCheckedIn && !"CONFIRMED".equals(reservation.getStatus())) {
            blockers.add(new CheckInReadinessIssueDTO(
                    "INVALID_RESERVATION_STATUS",
                    "Only a confirmed reservation can be checked in."));
        }
        if (!alreadyCheckedIn && window.evaluatedAt().isBefore(window.earliestCheckInAt())) {
            blockers.add(new CheckInReadinessIssueDTO(
                    "ARRIVAL_WINDOW_NOT_OPEN",
                    "Check-in is not available before the approved arrival window."));
        }
        if (!alreadyCheckedIn && !window.evaluatedAt().isBefore(window.latestCheckInAt())) {
            blockers.add(new CheckInReadinessIssueDTO(
                    "STAY_WINDOW_CLOSED",
                    "The reservation stay window has already closed."));
        }

        int requiredRooms = details.stream()
                .mapToInt(detail -> detail.getQuantity() == null ? 1 : detail.getQuantity())
                .sum();
        long activeRows = assignments.stream().filter(item -> "ASSIGNED".equals(item.getStatus())).count();
        long distinctRooms = rooms.stream().map(Room::getId).distinct().count();
        if (!alreadyCheckedIn && (requiredRooms < 1 || activeRows != requiredRooms || distinctRooms != requiredRooms)) {
            blockers.add(new CheckInReadinessIssueDTO(
                    "MISSING_ROOM_ASSIGNMENT",
                    "Assign exactly " + requiredRooms + " physical rooms before check-in."));
        }

        boolean propertyMismatch = rooms.stream().anyMatch(room -> room.getHotel() == null
                || !java.util.Objects.equals(room.getHotel().getId(), reservation.getHotel().getId()));
        if (!alreadyCheckedIn && propertyMismatch) {
            blockers.add(new CheckInReadinessIssueDTO(
                    "ASSIGNMENT_PROPERTY_MISMATCH",
                    "An assigned room does not belong to this property."));
        }

        List<Room> unavailableRooms = rooms.stream()
                .filter(room -> !isRoomReadyForCheckIn(room))
                .toList();
        if (!alreadyCheckedIn && !unavailableRooms.isEmpty()) {
            String labels = unavailableRooms.stream()
                    .map(Room::getRoomNumber)
                    .sorted()
                    .collect(Collectors.joining(", "));
            blockers.add(new CheckInReadinessIssueDTO(
                    "ROOM_NOT_READY",
                    "Assigned rooms are not clean and operational: " + labels + "."));
        }

        List<RoomDTO> assignedRooms = rooms.stream()
                .sorted(java.util.Comparator.comparing(Room::getId))
                .map(this::availableRoomDto)
                .toList();
        return new CheckInReadinessDTO(
                reservation.getId(),
                reservation.getStatus(),
                !alreadyCheckedIn && blockers.isEmpty(),
                alreadyCheckedIn,
                window.evaluatedAt(),
                window.scheduledArrivalAt(),
                window.earliestCheckInAt(),
                window.latestCheckInAt(),
                window.zoneId(),
                window.earlyWindowMinutes(),
                window.policyVersion(),
                requiredRooms,
                assignedRooms,
                List.copyOf(blockers));
    }

    private boolean isRoomReadyForCheckIn(Room room) {
        return room != null
                && Set.of(RoomStatePolicy.AVAILABLE, RoomStatePolicy.RESERVED).contains(room.getStatus())
                && RoomStatePolicy.NONE.equals(room.getMaintenanceStatus())
                && Set.of(RoomStatePolicy.CLEAN, RoomStatePolicy.INSPECTED)
                        .contains(room.getHousekeepingStatus());
    }

    @Transactional
    public CheckoutResultDTO checkout(Long reservationId, CheckoutRequest request) {
        Long checkoutOverrideId = validateCheckoutRequest(request);
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new com.hotel.exceptions.ResourceNotFoundException("Không tìm thấy booking."));
        requireOperationalAccess(reservation);
        return completeCheckoutLocked(reservation, checkoutOverrideId);
    }

    private CheckoutResultDTO completeCheckoutLocked(Reservation reservation, Long checkoutOverrideId) {
        boolean alreadyCheckedOut = "CHECKED_OUT".equals(reservation.getStatus());
        if (!"CHECKED_IN".equals(reservation.getStatus()) && !alreadyCheckedOut) {
            throw new IllegalStateException("Chỉ có thể trả phòng cho booking đang CHECKED_IN hoặc retry CHECKED_OUT.");
        }
        InvoiceFinalizationService.FinalizedInvoice finalized = invoiceFinalizationService.finalizeInvoice(
                new InvoiceFinalizationService.FinalizeInvoiceCommand(reservation.getId(), checkoutOverrideId));
        PropertyInvoice invoice = finalized.invoice();
        CheckoutOperationsService.CheckoutOperationsResult operations = checkoutOperationsService.apply(reservation);
        if (!alreadyCheckedOut) {
            String previousStatus = reservation.getStatus();
            reservation.setStatus("CHECKED_OUT");
            reservationRepository.saveAndFlush(reservation);
            appendStatusChangedEvent(reservation, previousStatus, "CHECKED_OUT", "Đã hoàn tất trả phòng.");
        }
        return new CheckoutResultDTO(
                reservation.getId(),
                reservation.getStatus(),
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getStatus().name(),
                invoice.getTotalAmount(),
                operations.roomIds());
    }

    private Long validateCheckoutRequest(CheckoutRequest request) {
        if (request == null) {
            return null;
        }
        if (request.getPaymentAmount() != null
                || hasText(request.getPaymentMethod())
                || hasText(request.getTransactionId())) {
            throw new FinancialException(
                    FinancialErrorCode.INVALID_AMOUNT,
                    "Checkout no longer accepts caller-provided payment amounts, methods or transaction references.");
        }
        return request.getCheckoutOverrideId();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void validateAssignableRoom(Reservation reservation, ReservationDetail detail, Room room) {
        if (!room.getHotel().getId().equals(reservation.getHotel().getId())) {
            throw new IllegalArgumentException("Không thể gán phòng của cơ sở khác.");
        }
        if (!room.getRoomType().getId().equals(detail.getRoomType().getId())) {
            throw new IllegalArgumentException("Phòng được chọn không thuộc đúng loại phòng đã đặt.");
        }
        if (!RoomStatePolicy.isAssignable(room)) {
            throw new IllegalStateException("Phòng " + room.getRoomNumber() + " không sạch hoặc không sẵn sàng.");
        }
        if (reservationRoomRepository.hasConflictingAssignment(
                room.getId(), reservation.getId(), RoomAvailabilityService.RELEASED_RESERVATION_STATUSES,
                reservation.getCheckInDate(), reservation.getCheckOutDate())) {
            throw new IllegalStateException("Phòng " + room.getRoomNumber() + " đã được gán cho booking khác.");
        }
    }

    private RoomDTO availableRoomDto(Room room) {
        RoomDTO dto = new RoomDTO();
        dto.setId(room.getId());
        dto.setHotelId(room.getHotel().getId());
        dto.setRoomTypeId(room.getRoomType().getId());
        dto.setRoomTypeNameVi(room.getRoomType().getNameVi());
        dto.setRoomNumber(room.getRoomNumber());
        dto.setFloor(room.getFloor());
        dto.setStatus(room.getStatus());
        dto.setHousekeepingStatus(room.getHousekeepingStatus());
        dto.setMaintenanceStatus(room.getMaintenanceStatus());
        return dto;
    }

    private void validateHotelCanReceiveBookings(Hotel hotel) {
        if (!"APPROVED".equals(hotel.getApprovalStatus()) || !"ACTIVE".equals(hotel.getOperationStatus())) {
            throw new IllegalStateException("Cơ sở chưa được duyệt hoặc đang ngừng hoạt động.");
        }
    }

    private void requireOperationalAccess(Reservation reservation) {
        if (!propertyAccessService.isSystemAdministrator()) {
            propertyAccessService.requireAccessibleOrNotFound(reservation.getHotel().getId(), "booking");
        }
    }

    private void authorizeReservationView(Reservation reservation) {
        if (propertyAccessService.isSystemAdministrator()) return;
        User currentUser = propertyAccessService.currentUser();
        if (reservation.getUser().getId().equals(currentUser.getId())) return;
        propertyAccessService.requireAccessibleOrNotFound(reservation.getHotel().getId(), "booking");
    }

    private User createGuestUser(ReservationRequest request) {
        User guest = new User();
        guest.setUsername("guest_" + System.currentTimeMillis());
        guest.setEmail(guest.getUsername() + "@guest.local");
        guest.setPasswordHash("NOPASSWORD");
        String fullName = ((request.getFirstName() == null ? "" : request.getFirstName()) + " "
                + (request.getLastName() == null ? "" : request.getLastName())).trim();
        guest.setFullName(fullName.isEmpty() ? "Khách vãng lai" : fullName);
        guest.setPhone(request.getPhone());
        guest.setStatus("GUEST");
        return userRepository.save(guest);
    }

    private void validateReservationRequest(ReservationRequest request) {
        if (request == null) throw new IllegalArgumentException("Thiếu thông tin đặt phòng.");
        if (request.getRoomTypeId() == null) throw new IllegalArgumentException("Vui lòng chọn loại phòng.");
        int quantity = request.getQuantity() == null ? 1 : request.getQuantity();
        int adults = request.getAdults() != null ? request.getAdults()
                : request.getGuests() == null ? 0 : request.getGuests();
        int children = request.getChildren() == null ? 0 : request.getChildren();
        if (quantity < 1) throw new IllegalArgumentException("Số phòng phải lớn hơn 0.");
        if (adults < 1 || children < 0) throw new IllegalArgumentException("Số khách không hợp lệ.");
    }

    private Reservation findReservation(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new com.hotel.exceptions.ResourceNotFoundException("Không tìm thấy booking."));
    }

    private ReservationDTO mapToDTO(Reservation reservation) {
        return mapToDTO(reservation, false);
    }

    private ReservationDTO mapToDTO(Reservation reservation, boolean includeEvents) {
        ReservationDTO dto = new ReservationDTO();
        dto.setId(reservation.getId());
        dto.setUserId(reservation.getUser().getId());
        dto.setUsername(reservation.getUser().getUsername());
        dto.setUserFullName(reservation.getUser().getFullName());
        dto.setCheckInDate(reservation.getCheckInDate());
        dto.setCheckOutDate(reservation.getCheckOutDate());
        dto.setGuests(reservation.getGuests());
        dto.setTotalAmount(reservation.getTotalAmount());
        dto.setStatus(reservation.getStatus());
        dto.setPaymentMethod(reservation.getPaymentMethod());
        dto.setSpecialRequests(reservation.getSpecialRequests());
        dto.setDetails(reservationDetailRepository.findByReservationId(reservation.getId()).stream()
                .map(this::mapDetailToDTO).toList());
        if (includeEvents) {
            dto.setEvents(operationalAuditService.findAuthorizedAggregateHistory(
                            reservation.getHotel().getId(), "STAY", "RESERVATION",
                            reservation.getId().toString(), 100).stream()
                    .map(event -> new ReservationEventDTO(
                            event.id(), event.eventType(), event.reason(), event.beforeState(), event.afterState(),
                            event.actorType(), event.occurredAt()))
                    .toList());
        } else {
            dto.setEvents(List.of());
        }
        return dto;
    }

    private Specification<Reservation> reservationFilters(
            Specification<Reservation> specification, String status, String query) {
        if (status != null && !status.isBlank()) {
            String normalizedStatus = status.trim().toUpperCase(Locale.ROOT);
            specification = specification.and((root, ignored, cb) ->
                    cb.equal(root.get("status"), normalizedStatus));
        }
        if (query != null && !query.isBlank()) {
            String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
            Long reservationId = parsePositiveLong(normalizedQuery.replaceFirst("^res-", ""));
            specification = specification.and((root, ignored, cb) -> {
                var user = root.join("user");
                var textMatch = cb.or(
                        cb.like(cb.lower(user.get("username")), "%" + normalizedQuery + "%"),
                        cb.like(cb.lower(user.get("fullName")), "%" + normalizedQuery + "%"));
                return reservationId == null
                        ? textMatch
                        : cb.or(textMatch, cb.equal(root.get("id"), reservationId));
            });
        }
        return specification;
    }

    private Long parsePositiveLong(String value) {
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void appendStatusChangedEvent(
            Reservation reservation, String previousStatus, String currentStatus, String reason) {
        appendReservationEvent(
                reservation,
                "RESERVATION_STATUS_CHANGED",
                reason,
                Map.of("status", previousStatus),
                Map.of("status", currentStatus));
    }

    private void appendReservationEvent(
            Reservation reservation, String eventType, String reason, Object beforeState, Object afterState) {
        operationalAuditService.append(new OperationalAuditService.AuditCommand(
                "TENANT",
                reservation.getHotel().getId(),
                "STAY",
                eventType,
                "RESERVATION",
                reservation.getId().toString(),
                null,
                null,
                reason,
                beforeState,
                afterState,
                null));
    }

    private Map<String, Object> reservationState(Reservation reservation) {
        return Map.of(
                "status", reservation.getStatus(),
                "checkInDate", reservation.getCheckInDate(),
                "checkOutDate", reservation.getCheckOutDate(),
                "guests", reservation.getGuests(),
                "totalAmount", reservation.getTotalAmount());
    }

    private ReservationDetailDTO mapDetailToDTO(ReservationDetail detail) {
        ReservationDetailDTO dto = new ReservationDetailDTO();
        dto.setId(detail.getId());
        dto.setReservationId(detail.getReservation().getId());
        dto.setRoomTypeId(detail.getRoomType() == null ? null : detail.getRoomType().getId());
        dto.setRoomTypeName(detail.getRoomType() == null ? null : detail.getRoomType().getNameVi());
        dto.setQuantity(detail.getQuantity());
        dto.setAdults(detail.getAdults());
        dto.setChildren(detail.getChildren());
        dto.setPriceAtBooking(detail.getUnitPrice() == null ? detail.getPrice() : detail.getUnitPrice());
        dto.setSubtotal(detail.getSubtotal());

        List<ReservationRoom> assignments = reservationRoomRepository
                .findByReservationDetailIdAndStatus(detail.getId(), "ASSIGNED");
        dto.setAssignedRoomIds(assignments.stream().map(item -> item.getRoom().getId()).toList());
        dto.setAssignedRoomNumbers(assignments.stream().map(item -> item.getRoom().getRoomNumber()).toList());
        Room firstRoom = assignments.isEmpty() ? detail.getRoom() : assignments.get(0).getRoom();
        dto.setRoomId(firstRoom == null ? null : firstRoom.getId());
        dto.setRoomNumber(firstRoom == null ? null : firstRoom.getRoomNumber());
        return dto;
    }
}
