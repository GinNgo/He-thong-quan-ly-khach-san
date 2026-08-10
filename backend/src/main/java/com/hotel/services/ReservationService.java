package com.hotel.services;

import com.hotel.domain.lifecycle.BookingLifecyclePolicy;
import com.hotel.domain.lifecycle.PaymentStatus;
import com.hotel.domain.lifecycle.RefundStatus;
import com.hotel.domain.lifecycle.ReservationStatus;
import com.hotel.domain.lifecycle.TransitionDecision;
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
import com.hotel.propertycommerce.refund.PropertyRefundService;
import com.hotel.propertycommerce.refund.PropertyRefundRequest;
import com.hotel.propertycommerce.refund.PropertyRefundRequestRepository;
import com.hotel.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
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
    private final PaymentRepository paymentRepository;
    private final PaymentSessionRepository paymentSessionRepository;
    private final RefundRequestRepository refundRequestRepository;
    private final RefundService refundService;
    private final PropertyRefundService propertyRefundService;
    private final PropertyRefundRequestRepository propertyRefundRequestRepository;
    private final PropertyAccessService propertyAccessService;
    private final ReservationHoldService reservationHoldService;
    private final PropertyPaymentConfigurationRepository propertyPaymentConfigurationRepository;
    private final InvoiceFinalizationService invoiceFinalizationService;
    private final CheckoutOperationsService checkoutOperationsService;
    private final PublicInventoryEligibilityPolicy publicInventoryEligibilityPolicy;

    @Autowired(required = false)
    private UserPropertyRepository userPropertyRepository;

    /** Legacy unit fixtures omit this field; production always wires the canonical evaluator. */
    @Autowired(required = false)
    private PromotionQuoteService promotionQuoteService;

    @Autowired(required = false)
    private OperationalAuditService operationalAuditService;

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
        PromotionQuoteDTO acceptedQuote = promotionQuoteService == null ? null
                : promotionQuoteService.quoteForRoom(
                roomType,
                request.getCheckInDate(),
                request.getCheckOutDate(),
                quantity,
                adults,
                children,
                request.getCouponCode(),
                user.getId());
        BigDecimal totalAmount = acceptedQuote == null
                ? roomAvailabilityService.calculateTotal(roomType.getBasePrice(), nights, quantity)
                : acceptedQuote.finalTotal();
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
        if (acceptedQuote != null) {
            promotionQuoteService.captureSnapshot(reservation, acceptedQuote);
        }
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

        if (acceptedQuote != null) {
            promotionQuoteService.redeem(savedReservation, user, acceptedQuote);
        }

        reservationHoldService.createHold(
                savedReservation.getId(),
                roomType.getId(),
                quantity,
                "RESERVATION-" + savedReservation.getId());

        notificationService.sendSystemNotification(
                "BOOKING",
                "Có đặt phòng mới!",
                "Khách hàng " + user.getFullName() + " vừa đặt " + quantity + " " + roomType.getNameVi()
                        + " từ " + request.getCheckInDate() + " đến " + request.getCheckOutDate()
        );
        auditReservation("RESERVATION_CREATED", savedReservation, null, reservationSnapshot(savedReservation), "Reservation created");
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
    public ReservationDTO getReservationById(Long id) {
        Reservation reservation = findReservation(id);
        authorizeReservationView(reservation);
        return mapToDTO(reservation);
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
        return reservationRepository.findByUserUsername(username).stream()
                .map(this::mapToDTO).toList();
    }

    @Transactional
    public ReservationDTO assignRooms(Long reservationId, AssignRoomsRequest request) {
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new com.hotel.exceptions.ResourceNotFoundException("Không tìm thấy booking."));
        requireOperationalAccess(reservation);
        java.util.Map<String, Object> before = reservationSnapshot(reservation);
        if (Set.of("CHECKED_OUT", "COMPLETED", "CANCELLED", "REJECTED", "EXPIRED", "NO_SHOW")
                .contains(reservation.getStatus())) {
            throw new IllegalStateException("Không thể gán phòng cho booking đã kết thúc hoặc bị hủy.");
        }

        List<ReservationDetail> details = reservationDetailRepository.findByReservationId(reservationId);
        if (details.size() != 1) {
            throw new IllegalStateException("Luồng hiện tại chỉ hỗ trợ một loại phòng trong mỗi booking.");
        }
        ReservationDetail detail = details.get(0);
        List<Long> roomIds = request == null || request.getRoomIds() == null
                ? List.of()
                : request.getRoomIds().stream().sorted().toList();
        int requiredQuantity = detail.getQuantity() == null ? 1 : detail.getQuantity();
        if (roomIds.size() != requiredQuantity || new HashSet<>(roomIds).size() != roomIds.size()) {
            throw new IllegalArgumentException("Số phòng được gán phải đúng bằng số lượng đã đặt và không được trùng.");
        }

        List<ReservationRoom> currentAssignments = reservationRoomRepository
                .findByReservationDetailIdAndStatusForUpdate(detail.getId(), "ASSIGNED");
        if (!currentAssignments.isEmpty()) {
            List<Long> assignedRoomIds = currentAssignments.stream()
                    .map(ReservationRoom::getRoom)
                    .map(Room::getId)
                    .sorted()
                    .toList();
            if (assignedRoomIds.equals(roomIds)) {
                return mapToDTO(reservation);
            }
            throw new IllegalStateException("Booking đã được gán phòng. Hãy giải phóng phòng trước khi gán lại.");
        }

        List<Room> rooms = roomRepository.findAllByIdForUpdate(roomIds);
        if (rooms.size() != roomIds.size()) {
            throw new IllegalArgumentException("Có phòng vật lý không tồn tại.");
        }
        List<ReservationRoom> newAssignments = new java.util.ArrayList<>();
        for (Room room : rooms) {
            validateAssignableRoom(reservation, detail, room);
            RoomStatePolicy.reserve(room);
            ReservationRoom assignment = new ReservationRoom();
            assignment.setReservationDetail(detail);
            assignment.setRoom(room);
            assignment.setAssignedAt(LocalDateTime.now());
            assignment.setStayStartDate(reservation.getCheckInDate());
            assignment.setStayEndDate(reservation.getCheckOutDate());
            assignment.setStatus("ASSIGNED");
            newAssignments.add(assignment);
        }
        roomRepository.saveAllAndFlush(rooms);
        reservationRoomRepository.saveAllAndFlush(newAssignments);

        detail.setRoom(rooms.get(0));
        reservation.setRoom(rooms.get(0));
        reservationDetailRepository.save(detail);
        reservationRepository.save(reservation);
        auditReservation("RESERVATION_ROOMS_ASSIGNED", reservation, before, reservationSnapshot(reservation), "Physical rooms assigned");
        return mapToDTO(reservation);
    }

    @Transactional
    public ReservationDTO updateReservationStatus(Long id, String status) {
        Reservation reservation = reservationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new com.hotel.exceptions.ResourceNotFoundException("Không tìm thấy booking."));
        requireOperationalAccess(reservation);
        ReservationStatus currentStatus = ReservationStatus.fromStorage(reservation.getStatus());
        java.util.Map<String, Object> before = reservationSnapshot(reservation);
        ReservationStatus targetStatus = ReservationStatus.fromStorage(status);
        TransitionDecision transition = BookingLifecyclePolicy.reservationTransition(currentStatus, targetStatus);
        if (transition == TransitionDecision.IDEMPOTENT) {
            reconcileReservationHold(id, targetStatus, LocalDateTime.now());
            auditReservation("RESERVATION_STATUS_REPLAYED", reservation, before, reservationSnapshot(reservation), "Idempotent lifecycle replay");
            return mapToDTO(reservation);
        }
        if (transition == TransitionDecision.REJECT) {
            throw new IllegalStateException(
                    "Không thể chuyển booking từ " + currentStatus + " sang " + targetStatus + ".");
        }
        String normalizedStatus = targetStatus.name();

        boolean roomMutation = "CHECKED_IN".equals(normalizedStatus)
                || "CANCELLED".equals(normalizedStatus)
                || RoomAvailabilityService.RELEASED_RESERVATION_STATUSES.contains(normalizedStatus);
        List<ReservationRoom> assignments = roomMutation
                ? reservationRoomRepository.findAssignedByReservationIdForUpdate(id)
                : reservationRoomRepository.findByReservationDetailReservationId(id);

        if ("CHECKED_OUT".equals(normalizedStatus)) {
            completeCheckoutLocked(reservation, null);
            reconcileReservationHold(id, targetStatus, LocalDateTime.now());
            auditReservation("RESERVATION_CHECKED_OUT", reservation, before, reservationSnapshot(reservation), "Reservation checked out");
            return mapToDTO(reservation);
        } else if ("CANCELLED".equals(normalizedStatus)) {
            cancelLockedReservation(reservation, assignments);
        } else if ("CHECKED_IN".equals(normalizedStatus)) {
            if (RoomAvailabilityService.RELEASED_RESERVATION_STATUSES.contains(reservation.getStatus())) {
                throw new IllegalStateException("Không thể nhận phòng cho booking đã kết thúc hoặc bị hủy.");
            }
            int required = reservationDetailRepository.findByReservationId(id).stream()
                    .mapToInt(detail -> detail.getQuantity() == null ? 1 : detail.getQuantity()).sum();
            long assigned = assignments.stream().filter(item -> "ASSIGNED".equals(item.getStatus())).count();
            if (assigned != required) {
                throw new IllegalStateException("Phải gán đủ " + required + " phòng vật lý trước khi check-in.");
            }
            List<Long> assignedRoomIds = assignments.stream()
                    .map(ReservationRoom::getRoom)
                    .map(Room::getId)
                    .distinct()
                    .sorted()
                    .toList();
            java.util.Map<Long, Room> lockedRooms = roomRepository.findAllByIdForUpdate(assignedRoomIds).stream()
                    .collect(Collectors.toMap(Room::getId, room -> room));
            if (lockedRooms.size() != assignedRoomIds.size()) {
                throw new FinancialException(
                        FinancialErrorCode.CONCURRENT_MODIFICATION,
                        "An assigned room changed during check-in; retry safely.");
            }
            assignments.forEach(item -> {
                Room lockedRoom = lockedRooms.get(item.getRoom().getId());
                RoomStatePolicy.checkIn(lockedRoom);
                roomRepository.save(lockedRoom);
            });
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
        reconcileReservationHold(id, targetStatus, LocalDateTime.now());
        auditReservation("RESERVATION_" + normalizedStatus, saved, before, reservationSnapshot(saved),
                "Reservation lifecycle transitioned to " + normalizedStatus);
        return mapToDTO(saved);
    }

    @Transactional
    public ReservationDTO checkIn(Long id) {
        // Front-desk check-in may be the first operational action. Assign the
        // reserved room type automatically when no physical room was selected.
        Reservation reservation = reservationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new com.hotel.exceptions.ResourceNotFoundException("Không tìm thấy booking."));
        requireOperationalAccess(reservation);
        List<ReservationRoom> assignments = reservationRoomRepository.findAssignedByReservationIdForUpdate(id);
        if (assignments.isEmpty()) {
            List<ReservationDetail> details = reservationDetailRepository.findByReservationId(id);
            if (details.size() == 1 && details.get(0).getRoomType() != null) {
                int quantity = details.get(0).getQuantity() == null ? 1 : details.get(0).getQuantity();
                List<Long> roomIds = roomRepository.findAvailableRoomsByRoomTypeAndDateForUpdate(
                                details.get(0).getRoomType().getId(),
                                List.of("MAINTENANCE", "OUT_OF_SERVICE", "DIRTY", "CLEANING", "OCCUPIED"),
                                RoomAvailabilityService.RELEASED_RESERVATION_STATUSES,
                                reservation.getCheckInDate(), reservation.getCheckOutDate()).stream()
                        .filter(room -> room.getHotel().getId().equals(reservation.getHotel().getId()))
                        .filter(RoomStatePolicy::isAssignable)
                        .limit(quantity)
                        .map(Room::getId)
                        .toList();
                if (roomIds.size() == quantity) {
                    AssignRoomsRequest request = new AssignRoomsRequest();
                    request.setRoomIds(roomIds);
                    assignRooms(id, request);
                }
            }
        }
        return updateReservationStatus(id, "CHECKED_IN");
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
        return cancelMyReservation(id, username,
                new CustomerCancellationRequest("CHANGE_OF_PLANS", "Thay đổi kế hoạch"));
    }

    @Transactional
    public ReservationDTO cancelMyReservation(Long id, String username, CustomerCancellationRequest request) {
        Reservation reservation = reservationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new com.hotel.exceptions.ResourceNotFoundException("Không tìm thấy booking."));
        if (username == null || reservation.getUser() == null
                || !username.equals(reservation.getUser().getUsername())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Bạn không có quyền hủy booking này.");
        }
        java.util.Map<String, Object> before = reservationSnapshot(reservation);
        if ("CANCELLED".equals(reservation.getStatus())) {
            reservationHoldService.releaseActiveHold(id, LocalDateTime.now());
            return mapToDTO(reservation);
        }

        cancelLockedReservation(
                reservation,
                reservationRoomRepository.findAssignedByReservationIdForUpdate(id));
        String reasonCode = request.reasonCode().trim().toUpperCase(java.util.Locale.ROOT);
        String reason = request.reason() == null ? "" : request.reason().trim();
        if ("OTHER".equals(reasonCode) && reason.isBlank()) {
            throw new IllegalArgumentException("Vui lòng nhập lý do khác.");
        }
        String displayedReason = reason.isBlank() ? cancellationReasonLabel(reasonCode) : reason;
        reservation.setStatus("CANCELLED");
        reservation.setCancellationReasonCode(reasonCode);
        reservation.setCancellationReason(displayedReason);
        reservation.setCancelledAt(LocalDateTime.now());
        Reservation saved = reservationRepository.save(reservation);
        reservationHoldService.releaseActiveHold(id, LocalDateTime.now());
        auditReservation("RESERVATION_CANCELLED", saved, before, reservationSnapshot(saved),
                "Customer cancelled reservation: " + displayedReason);
        notifyPropertyCancellation(saved, displayedReason);
        return mapToDTO(saved);
    }

    private void notifyPropertyCancellation(Reservation reservation, String reason) {
        Long hotelId = reservation.getHotel().getId();
        userRepository.findSupportRecipientUsernames(hotelId).stream()
                .map(userRepository::findByUsername)
                .flatMap(java.util.Optional::stream)
                .filter(user -> user.getRoles().stream().anyMatch(role -> Set.of(
                        "PROPERTY_OWNER", "HOTEL_ADMIN", "HOTEL_MANAGER", "RECEPTIONIST", "SUPER_ADMIN")
                        .contains(role.getCode())))
                .forEach(user -> notificationService.sendUserNotificationOnce(
                        "reservation-cancelled:" + reservation.getId() + ":" + user.getId(),
                        user.getUsername(), user.getId(), "RESERVATION_CANCELLED",
                        "Khách đã hủy đặt phòng #" + reservation.getId(),
                        "Lý do: " + reason));
    }

    private String cancellationReasonLabel(String code) {
        return switch (code) {
            case "CHANGE_OF_PLANS" -> "Thay đổi kế hoạch";
            case "BOOKED_BY_MISTAKE" -> "Đặt nhầm";
            case "FOUND_ANOTHER_PROPERTY" -> "Đã chọn nơi lưu trú khác";
            case "PAYMENT_ISSUE" -> "Gặp vấn đề thanh toán";
            default -> throw new IllegalArgumentException("Lý do hủy không hợp lệ.");
        };
    }

    private void reconcileReservationHold(
            Long reservationId,
            ReservationStatus status,
            LocalDateTime now) {
        switch (status) {
            case PENDING_PAYMENT -> {
                // Pending payment remains the inventory-holding state.
            }
            case EXPIRED -> reservationHoldService.expireActiveHold(reservationId, now);
            case CANCELLED, REJECTED, NO_SHOW -> reservationHoldService.releaseActiveHold(reservationId, now);
            default -> reservationHoldService.consumeActiveHold(reservationId, now);
        }
    }

    private void cancelLockedReservation(Reservation reservation, List<ReservationRoom> assignments) {
        if (Set.of("CHECKED_IN", "CHECKED_OUT", "COMPLETED", "REJECTED", "EXPIRED", "NO_SHOW")
                .contains(reservation.getStatus())) {
            throw new IllegalStateException("Không thể hủy booking đã check-in hoặc kết thúc.");
        }

        refundService.requestRefundsForSuccessfulPayments(reservation.getId(), "RESERVATION_CANCELLED");
        propertyRefundService.requestCancellationRefunds(
                reservation.getId(),
                "Khách hủy đặt phòng",
                "reservation-cancellation:" + reservation.getId());
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
        Reservation reservation = findReservation(reservationId);
        requireOperationalAccess(reservation);
        List<ReservationDetail> details = reservationDetailRepository.findByReservationId(reservationId);
        if (details.size() != 1 || details.get(0).getRoomType() == null) {
            throw new IllegalStateException("Booking phải có đúng một loại phòng để chọn phòng vật lý.");
        }
        return roomRepository.findAvailableRoomsByRoomTypeAndDate(
                        details.get(0).getRoomType().getId(),
                        List.of("MAINTENANCE", "OUT_OF_SERVICE", "DIRTY", "CLEANING", "OCCUPIED"),
                        RoomAvailabilityService.RELEASED_RESERVATION_STATUSES,
                        reservation.getCheckInDate(), reservation.getCheckOutDate()).stream()
                .filter(room -> room.getHotel().getId().equals(reservation.getHotel().getId()))
                .filter(RoomStatePolicy::isAssignable)
                .map(this::availableRoomDto).toList();
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
            reservation.setStatus("CHECKED_OUT");
            reservationRepository.saveAndFlush(reservation);
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

    private java.util.Map<String, Object> reservationSnapshot(Reservation reservation) {
        java.util.Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("id", reservation.getId());
        snapshot.put("status", reservation.getStatus());
        snapshot.put("hotelId", reservation.getHotel() == null ? null : reservation.getHotel().getId());
        snapshot.put("checkInDate", reservation.getCheckInDate());
        snapshot.put("checkOutDate", reservation.getCheckOutDate());
        snapshot.put("roomId", reservation.getRoom() == null ? null : reservation.getRoom().getId());
        return snapshot;
    }

    private void auditReservation(String eventType, Reservation reservation, Object before, Object after, String reason) {
        if (operationalAuditService == null || reservation == null || reservation.getHotel() == null) return;
        operationalAuditService.append(new OperationalAuditService.AuditCommand(
                "TENANT", reservation.getHotel().getId(), "RESERVATION", eventType, "RESERVATION",
                String.valueOf(reservation.getId()), null, null, reason, before, after, null));
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
        dto.setCancellationReasonCode(reservation.getCancellationReasonCode());
        dto.setCancellationReason(reservation.getCancellationReason());
        dto.setCancelledAt(reservation.getCancelledAt());
        dto.setDetails(reservationDetailRepository.findByReservationId(reservation.getId()).stream()
                .map(this::mapDetailToDTO).toList());
        dto.setPayment(mapPaymentSummary(reservation));
        dto.setRefunds(java.util.stream.Stream.concat(
                        refundRequestRepository.findByReservationIdOrderByIdAsc(reservation.getId()).stream()
                                .map(this::mapRefundSummary),
                        propertyRefundRequestRepository.findByReservationIdOrderByRequestedAtAsc(reservation.getId()).stream()
                                .map(this::mapCanonicalRefundSummary))
                .sorted(java.util.Comparator.comparing(
                        RefundSummaryDTO::getRequestedAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .toList());
        dto.setQuote(promotionQuoteService == null ? null : promotionQuoteService.restoreSnapshot(reservation));
        dto.setProperty(mapPropertyContact(reservation.getHotel()));
        return dto;
    }

    private ReservationDTO.PropertyContactDTO mapPropertyContact(Hotel hotel) {
        if (hotel == null) return null;
        ReservationDTO.PropertyContactDTO dto = new ReservationDTO.PropertyContactDTO();
        dto.setId(hotel.getId());
        dto.setName(hotel.getName());
        dto.setAddress(hotel.getAddressLine());
        dto.setPhone(hotel.getPhone());
        dto.setEmail(hotel.getEmail());
        if (userPropertyRepository != null) {
            userPropertyRepository.findByHotelIdAndRelationshipTypeAndStatus(hotel.getId(), "OWNER", "ACTIVE")
                    .stream().findFirst().map(UserProperty::getUser).ifPresent(owner -> {
                        dto.setContactName(owner.getFullName());
                        dto.setEmail(dto.getEmail() == null || dto.getEmail().isBlank() ? owner.getEmail() : dto.getEmail());
                        dto.setPhone(dto.getPhone() == null || dto.getPhone().isBlank() ? owner.getPhone() : dto.getPhone());
                    });
        }
        return dto;
    }

    private PaymentLifecycleSummaryDTO mapPaymentSummary(Reservation reservation) {
        List<Payment> successfulCharges = paymentRepository.findByReservationId(reservation.getId()).stream()
                .filter(payment -> payment.getAmount() != null && payment.getAmount().signum() > 0)
                .filter(payment -> PaymentStatus.fromStorage(payment.getStatus()) == PaymentStatus.SUCCEEDED)
                .toList();
        List<PaymentSession> sessions = paymentSessionRepository
                .findByReservationIdOrderByIdDesc(reservation.getId());

        if (!sessions.isEmpty()) {
            PaymentSession session = sessions.get(0);
            String displayStatus = displayPaymentStatus(session);
            return PaymentLifecycleSummaryDTO.builder()
                    .provider(session.getProvider())
                    .amount(session.getExpectedAmount())
                    .currency(session.getCurrency())
                    .status(displayStatus)
                    .expiresAt(session.getExpiresAt())
                    .completedAt(session.getCompletedAt())
                    .reconciliationRequired(session.isReconciliationRequired())
                    .failureCode(session.getFailureCode())
                    .build();
        }

        if (successfulCharges.isEmpty()) {
            return null;
        }
        BigDecimal paidAmount = successfulCharges.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Payment latestCharge = successfulCharges.stream()
                .max(java.util.Comparator.comparing(
                        payment -> payment.getPaymentDate() == null ? LocalDateTime.MIN : payment.getPaymentDate()))
                .orElseThrow();
        return PaymentLifecycleSummaryDTO.builder()
                .provider(latestCharge.getPaymentMethod())
                .amount(paidAmount)
                .currency("VND")
                .status(PaymentStatus.SUCCEEDED.name())
                .completedAt(latestCharge.getPaymentDate())
                .reconciliationRequired(false)
                .build();
    }

    private String displayPaymentStatus(PaymentSession session) {
        PaymentStatus status = PaymentStatus.fromStorage(session.getStatus());
        if ((status == PaymentStatus.CREATED || status == PaymentStatus.PENDING)
                && session.getExpiresAt() != null
                && !session.getExpiresAt().isAfter(LocalDateTime.now())) {
            return PaymentStatus.EXPIRED.name();
        }
        return status.name();
    }

    private RefundSummaryDTO mapRefundSummary(RefundRequest request) {
        return RefundSummaryDTO.builder()
                .publicId(request.getPublicId())
                .amount(request.getRequestedAmount())
                .currency(request.getCurrency())
                .provider(request.getProvider())
                .status(RefundStatus.fromStorage(request.getStatus()).name())
                .requestedAt(request.getRequestedAt())
                .completedAt(request.getCompletedAt())
                .failureCode(request.getFailureCode())
                .build();
    }

    private RefundSummaryDTO mapCanonicalRefundSummary(PropertyRefundRequest request) {
        return RefundSummaryDTO.builder()
                .publicId(request.getPublicId())
                .amount(request.getRequestedAmount())
                .currency(request.getCurrency())
                .provider(request.getOriginalTransaction().getProvider())
                .status(request.getStatus().name())
                .requestedAt(request.getRequestedAt())
                .completedAt(request.getCompletedAt())
                .build();
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
