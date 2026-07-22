package com.hotel.services;

import com.hotel.dtos.*;
import com.hotel.entities.*;
import com.hotel.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
    private final ReservationServiceItemRepository reservationServiceItemRepository;
    private final HotelServiceRepository hotelServiceRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final HousekeepingTaskRepository housekeepingTaskRepository;
    private final PropertyAccessService propertyAccessService;

    @Transactional
    public ReservationDTO createReservation(String username, ReservationRequest request) {
        validateReservationRequest(request);

        User user = username == null || username.isBlank()
                ? createGuestUser(request)
                : userRepository.findByUsername(username)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng đặt phòng."));

        RoomType roomType = roomTypeRepository.findByIdForUpdate(request.getRoomTypeId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy loại phòng."));
        Hotel hotel = roomType.getHotel();
        validateHotelCanReceiveBookings(hotel);

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

        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setRoom(null);
        reservation.setHotel(hotel);
        reservation.setCheckInDate(request.getCheckInDate());
        reservation.setCheckOutDate(request.getCheckOutDate());
        reservation.setGuests(adults + children);
        reservation.setStatus("PENDING");
        reservation.setPaymentMethod(request.getPaymentMethod());
        reservation.setSpecialRequests(request.getSpecialRequests());
        reservation.setTotalAmount(totalAmount);
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
    public ReservationDTO getReservationById(Long id) {
        Reservation reservation = findReservation(id);
        authorizeReservationView(reservation);
        return mapToDTO(reservation);
    }

    @Transactional(readOnly = true)
    public List<ReservationDTO> getMyReservations(String username) {
        return reservationRepository.findAll().stream()
                .filter(reservation -> reservation.getUser().getUsername().equals(username))
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional
    public ReservationDTO assignRooms(Long reservationId, AssignRoomsRequest request) {
        Reservation reservation = findReservation(reservationId);
        requireOperationalAccess(reservation);
        if (Set.of("CHECKED_OUT", "COMPLETED", "CANCELLED", "REJECTED", "EXPIRED", "NO_SHOW")
                .contains(reservation.getStatus())) {
            throw new IllegalStateException("Không thể gán phòng cho booking đã kết thúc hoặc bị hủy.");
        }

        List<ReservationDetail> details = reservationDetailRepository.findByReservationId(reservationId);
        if (details.size() != 1) {
            throw new IllegalStateException("Luồng hiện tại chỉ hỗ trợ một loại phòng trong mỗi booking.");
        }
        ReservationDetail detail = details.get(0);
        List<Long> roomIds = request == null || request.getRoomIds() == null ? List.of() : request.getRoomIds();
        int requiredQuantity = detail.getQuantity() == null ? 1 : detail.getQuantity();
        if (roomIds.size() != requiredQuantity || new HashSet<>(roomIds).size() != roomIds.size()) {
            throw new IllegalArgumentException("Số phòng được gán phải đúng bằng số lượng đã đặt và không được trùng.");
        }

        List<ReservationRoom> currentAssignments = reservationRoomRepository
                .findByReservationDetailIdAndStatus(detail.getId(), "ASSIGNED");
        if (!currentAssignments.isEmpty()) {
            throw new IllegalStateException("Booking đã được gán phòng. Hãy giải phóng phòng trước khi gán lại.");
        }

        List<Room> rooms = roomRepository.findAllById(roomIds);
        if (rooms.size() != roomIds.size()) {
            throw new IllegalArgumentException("Có phòng vật lý không tồn tại.");
        }
        for (Room room : rooms) {
            validateAssignableRoom(reservation, detail, room);
            ReservationRoom assignment = new ReservationRoom();
            assignment.setReservationDetail(detail);
            assignment.setRoom(room);
            assignment.setAssignedAt(LocalDateTime.now());
            assignment.setStatus("ASSIGNED");
            reservationRoomRepository.save(assignment);
        }

        detail.setRoom(rooms.get(0));
        reservation.setRoom(rooms.get(0));
        reservationDetailRepository.save(detail);
        reservationRepository.save(reservation);
        return mapToDTO(reservation);
    }

    @Transactional
    public ReservationDTO updateReservationStatus(Long id, String status) {
        Reservation reservation = reservationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy booking."));
        requireOperationalAccess(reservation);
        String normalizedStatus = status == null ? "" : status.trim().toUpperCase();

        if (normalizedStatus.equals(reservation.getStatus())) {
            return mapToDTO(reservation);
        }

        List<ReservationRoom> assignments = reservationRoomRepository.findByReservationDetailReservationId(id);

        if ("CHECKED_OUT".equals(normalizedStatus)) {
            completeCheckout(reservation, null);
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
            assignments.forEach(item -> {
                item.getRoom().setStatus("OCCUPIED");
                item.getRoom().setHousekeepingStatus("CLEAN");
                roomRepository.save(item.getRoom());
            });
        } else if (RoomAvailabilityService.RELEASED_RESERVATION_STATUSES.contains(normalizedStatus)) {
            assignments.stream().filter(item -> "ASSIGNED".equals(item.getStatus())).forEach(item -> {
                item.setStatus("RELEASED");
                item.setReleasedAt(LocalDateTime.now());
                reservationRoomRepository.save(item);
                item.getRoom().setStatus("AVAILABLE");
                roomRepository.save(item.getRoom());
            });
        } else if ("CONFIRMED".equals(normalizedStatus) && reservation.getUser().getEmail() != null) {
            emailService.sendBookingConfirmation(
                    reservation.getUser().getEmail(), reservation.getUser().getFullName(), reservation.getId(),
                    reservation.getCheckInDate().toString(), reservation.getCheckOutDate().toString()
            );
        }

        reservation.setStatus(normalizedStatus);
        return mapToDTO(reservationRepository.save(reservation));
    }

    @Transactional
    public ReservationDTO cancelMyReservation(Long id, String username) {
        Reservation reservation = reservationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy booking."));
        if (username == null || reservation.getUser() == null
                || !username.equals(reservation.getUser().getUsername())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Bạn không có quyền hủy booking này.");
        }
        if ("CANCELLED".equals(reservation.getStatus())) {
            return mapToDTO(reservation);
        }

        cancelLockedReservation(
                reservation,
                reservationRoomRepository.findByReservationDetailReservationId(id));
        reservation.setStatus("CANCELLED");
        return mapToDTO(reservationRepository.save(reservation));
    }

    private void cancelLockedReservation(Reservation reservation, List<ReservationRoom> assignments) {
        if (Set.of("CHECKED_IN", "CHECKED_OUT", "COMPLETED", "REJECTED", "EXPIRED", "NO_SHOW")
                .contains(reservation.getStatus())) {
            throw new IllegalStateException("Không thể hủy booking đã check-in hoặc kết thúc.");
        }

        paymentService.refundSuccessfulPayments(reservation.getId());
        assignments.stream().filter(item -> "ASSIGNED".equals(item.getStatus())).forEach(item -> {
            item.setStatus("RELEASED");
            item.setReleasedAt(LocalDateTime.now());
            reservationRoomRepository.save(item);
            item.getRoom().setStatus("AVAILABLE");
            roomRepository.save(item.getRoom());
        });
    }

    @Transactional
    public ReservationServiceItemDTO addExtraService(Long reservationId, AddServiceRequest request) {
        Reservation reservation = findReservation(reservationId);
        requireOperationalAccess(reservation);
        if (!"CHECKED_IN".equals(reservation.getStatus())) {
            throw new IllegalStateException("Chỉ có thể thêm dịch vụ khi khách đang ở.");
        }
        if (request == null || request.getServiceId() == null || request.getQuantity() == null || request.getQuantity() < 1) {
            throw new IllegalArgumentException("Dịch vụ và số lượng phải hợp lệ.");
        }

        HotelService service = hotelServiceRepository.findById(request.getServiceId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy dịch vụ."));
        boolean systemService = Boolean.TRUE.equals(service.getSystemService());
        boolean sameHotel = service.getHotel() != null && service.getHotel().getId().equals(reservation.getHotel().getId());
        if (!systemService && !sameHotel) {
            throw new IllegalArgumentException("Dịch vụ không thuộc cơ sở của booking này.");
        }
        if (!"ACTIVE".equals(service.getStatus())) {
            throw new IllegalStateException("Dịch vụ hiện không hoạt động.");
        }

        ReservationServiceItem item = new ReservationServiceItem();
        item.setReservation(reservation);
        item.setHotelService(service);
        item.setQuantity(request.getQuantity());
        item.setPrice(service.getPrice());
        item.setTotalAmount(service.getPrice().multiply(BigDecimal.valueOf(request.getQuantity())));
        item.setUsedAt(LocalDateTime.now());
        item.setStatus("ACTIVE");
        org.springframework.security.core.Authentication authentication =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            userRepository.findByUsername(authentication.getName()).ifPresent(item::setAddedBy);
        }
        ReservationServiceItem savedItem = reservationServiceItemRepository.save(item);

        reservation.setTotalAmount(reservation.getTotalAmount().add(item.getTotalAmount()));
        reservationRepository.save(reservation);

        ReservationServiceItemDTO dto = new ReservationServiceItemDTO();
        dto.setId(savedItem.getId());
        dto.setReservationId(reservation.getId());
        dto.setServiceId(service.getId());
        dto.setServiceNameVi(service.getNameVi());
        dto.setQuantity(savedItem.getQuantity());
        dto.setPrice(savedItem.getPrice());
        dto.setTotalAmount(savedItem.getTotalAmount());
        return dto;
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
                .map(this::availableRoomDto).toList();
    }

    @Transactional
    public CheckoutResultDTO checkout(Long reservationId, CheckoutRequest request) {
        Reservation reservation = findReservation(reservationId);
        requireOperationalAccess(reservation);
        return completeCheckout(reservation, request);
    }

    private CheckoutResultDTO completeCheckout(Reservation reservation, CheckoutRequest request) {
        if (!"CHECKED_IN".equals(reservation.getStatus())) {
            throw new IllegalStateException("Chỉ có thể trả phòng cho booking đang CHECKED_IN.");
        }
        Invoice invoice = invoiceRepository.findByReservationId(reservation.getId()).orElseGet(Invoice::new);
        if (invoice.getId() == null) {
            invoice.setInvoiceCode("INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            invoice.setReservation(reservation);
        }
        invoice.setIssueDate(LocalDate.now());
        invoice.setTotalAmount(reservation.getTotalAmount());

        BigDecimal paidAmount = request == null ? null : request.getPaymentAmount();
        if (paidAmount != null && paidAmount.signum() > 0) {
            Payment payment = new Payment();
            payment.setReservation(reservation);
            payment.setAmount(paidAmount);
            payment.setPaymentMethod(request.getPaymentMethod() == null ? "CASH" : request.getPaymentMethod());
            payment.setStatus("SUCCESS");
            payment.setTransactionId(request.getTransactionId());
            payment.setPaymentDate(LocalDateTime.now());
            paymentRepository.save(payment);
        }
        BigDecimal totalPaid = paymentRepository.findByReservationId(reservation.getId()).stream()
                .filter(payment -> "SUCCESS".equals(payment.getStatus()))
                .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        invoice.setStatus(totalPaid.compareTo(reservation.getTotalAmount()) >= 0 ? "PAID" : "PENDING");
        invoice = invoiceRepository.save(invoice);

        List<Long> dirtyRoomIds = new java.util.ArrayList<>();
        List<ReservationRoom> assignments = reservationRoomRepository.findByReservationDetailReservationId(reservation.getId());
        for (ReservationRoom assignment : assignments) {
            if (!"ASSIGNED".equals(assignment.getStatus())) continue;
            assignment.setStatus("RELEASED");
            assignment.setReleasedAt(LocalDateTime.now());
            reservationRoomRepository.save(assignment);
            Room room = assignment.getRoom();
            room.setStatus("DIRTY");
            room.setHousekeepingStatus("DIRTY");
            roomRepository.save(room);
            dirtyRoomIds.add(room.getId());
            if (housekeepingTaskRepository.findByRoomIdAndStatus(room.getId(), "PENDING").isEmpty()) {
                HousekeepingTask task = new HousekeepingTask();
                task.setHotel(reservation.getHotel());
                task.setRoom(room);
                task.setReservation(reservation);
                task.setStatus("PENDING");
                task.setNote("Dọn phòng sau check-out booking #" + reservation.getId());
                housekeepingTaskRepository.save(task);
            }
        }
        reservation.setStatus("CHECKED_OUT");
        reservationRepository.save(reservation);
        return new CheckoutResultDTO(reservation.getId(), reservation.getStatus(), invoice.getId(), invoice.getInvoiceCode(),
                invoice.getStatus(), invoice.getTotalAmount(), dirtyRoomIds);
    }

    private void validateAssignableRoom(Reservation reservation, ReservationDetail detail, Room room) {
        if (!room.getHotel().getId().equals(reservation.getHotel().getId())) {
            throw new IllegalArgumentException("Không thể gán phòng của cơ sở khác.");
        }
        if (!room.getRoomType().getId().equals(detail.getRoomType().getId())) {
            throw new IllegalArgumentException("Phòng được chọn không thuộc đúng loại phòng đã đặt.");
        }
        if (!"AVAILABLE".equals(room.getStatus()) || !"CLEAN".equals(room.getHousekeepingStatus())
                || !"NONE".equals(room.getMaintenanceStatus())) {
            throw new IllegalStateException("Phòng " + room.getRoomNumber() + " đang bảo trì.");
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
            propertyAccessService.requireCanManage(reservation.getHotel().getId());
        }
    }

    private void authorizeReservationView(Reservation reservation) {
        if (propertyAccessService.isSystemAdministrator()) return;
        User currentUser = propertyAccessService.currentUser();
        if (reservation.getUser().getId().equals(currentUser.getId())) return;
        propertyAccessService.requireCanManage(reservation.getHotel().getId());
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
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy booking."));
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
        dto.setDetails(reservationDetailRepository.findByReservationId(reservation.getId()).stream()
                .map(this::mapDetailToDTO).toList());
        return dto;
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
