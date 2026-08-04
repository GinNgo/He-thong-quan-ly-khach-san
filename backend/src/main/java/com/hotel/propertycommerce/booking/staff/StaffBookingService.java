package com.hotel.propertycommerce.booking.staff;

import com.hotel.dtos.ReservationDTO;
import com.hotel.dtos.ReservationRequest;
import com.hotel.entities.RoomType;
import com.hotel.entities.User;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.propertycommerce.booking.DepositPolicySnapshot;
import com.hotel.propertycommerce.config.PropertyPaymentConfiguration;
import com.hotel.propertycommerce.config.PropertyPaymentConfigurationRepository;
import com.hotel.repositories.RoomTypeRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.services.PropertyAccessService;
import com.hotel.services.ReservationService;
import com.hotel.services.RoomAvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StaffBookingService {
    private final StaffBookingQuoteRepository quoteRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final UserRepository userRepository;
    private final PropertyPaymentConfigurationRepository paymentConfigurationRepository;
    private final RoomAvailabilityService roomAvailabilityService;
    private final PropertyAccessService propertyAccessService;
    private final ReservationService reservationService;
    private final ReservationRepository reservationRepository;

    @Value("${app.staff-booking.quote-ttl-minutes:2}")
    private long quoteTtlMinutes;

    @Transactional(readOnly = true)
    public ContextResponse context(Long hotelId, String customerQuery) {
        var hotel = propertyAccessService.requireManagedHotel(hotelId);
        String query = customerQuery == null ? "" : customerQuery.trim();
        List<CustomerOption> customers = query.length() < 2 ? List.of() : userRepository
                .searchActiveCustomers(query, PageRequest.of(0, 20)).stream()
                .map(user -> new CustomerOption(user.getId(), user.getFullName(), user.getUsername(), maskEmail(user.getEmail())))
                .toList();
        List<RoomTypeOption> roomTypes = roomTypeRepository.findByHotelId(hotel.getId()).stream()
                .filter(item -> "ACTIVE".equalsIgnoreCase(item.getStatus()))
                .map(item -> new RoomTypeOption(item.getId(), item.getCode(), item.getNameVi(), item.getNameEn(),
                        item.getBasePrice(), item.getMaxAdults(), item.getMaxChildren(), item.getMaxGuests()))
                .toList();
        return new ContextResponse(hotel.getId(), hotel.getName(), customers, roomTypes,
                List.of("CASH", "BANK_TRANSFER", "SIMULATOR"));
    }

    @Transactional
    public QuoteResponse quote(QuoteRequest request) {
        validateRequest(request);
        propertyAccessService.requireAccessibleOrNotFound(request.hotelId(), "cơ sở");
        RoomType roomType = roomTypeRepository.findByIdForUpdate(request.roomTypeId())
                .filter(item -> item.getHotel() != null && request.hotelId().equals(item.getHotel().getId()))
                .filter(item -> "ACTIVE".equalsIgnoreCase(item.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại phòng."));
        User customer = activeCustomer(request.customerId());
        roomAvailabilityService.validateCapacity(roomType, request.quantity(), request.adults(), request.children());
        long available = roomAvailabilityService.countAvailableRooms(
                roomType.getId(), request.checkInDate(), request.checkOutDate());
        if (available < request.quantity()) {
            throw new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION,
                    "Inventory is no longer sufficient for this staff booking quote.");
        }
        long nights = roomAvailabilityService.getNights(request.checkInDate(), request.checkOutDate());
        BigDecimal total = roomAvailabilityService.calculateTotal(roomType.getBasePrice(), nights, request.quantity());
        PropertyPaymentConfiguration configuration = paymentConfigurationRepository.findByHotelId(request.hotelId())
                .filter(PropertyPaymentConfiguration::isEnabled)
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.POLICY_NOT_CONFIGURED,
                        "Payment and deposit policy is not configured for this property."));
        DepositPolicySnapshot deposit = DepositPolicySnapshot.capture(configuration, total);
        LocalDateTime now = LocalDateTime.now();
        StaffBookingQuote quote = new StaffBookingQuote();
        quote.setPublicId(UUID.randomUUID().toString());
        quote.setHotel(roomType.getHotel());
        quote.setCustomer(customer);
        quote.setRoomType(roomType);
        quote.setCheckInDate(request.checkInDate());
        quote.setCheckOutDate(request.checkOutDate());
        quote.setQuantity(request.quantity());
        quote.setAdults(request.adults());
        quote.setChildren(request.children());
        quote.setPaymentMethod(request.paymentMethod().trim().toUpperCase(Locale.ROOT));
        quote.setSpecialRequests(request.specialRequests());
        quote.setBasePrice(roomType.getBasePrice());
        quote.setTotalAmount(total);
        quote.setDepositAmount(deposit.requiredDeposit().amount());
        quote.setPaymentConfigurationId(configuration.getId());
        quote.setPaymentConfigurationVersion(configuration.getVersion());
        quote.setAvailableRooms(available);
        quote.setExpiresAt(now.plusMinutes(quoteTtlMinutes));
        quote.setStatus("QUOTED");
        return response(quoteRepository.save(quote), false);
    }

    @Transactional
    public ReservationDTO create(String quotePublicId, String scope, String idempotencyKey) {
        StaffBookingQuote quote = quoteRepository.findByPublicIdForUpdate(requireText(quotePublicId))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy báo giá đặt phòng."));
        propertyAccessService.requireAccessibleOrNotFound(quote.getHotel().getId(), "báo giá đặt phòng");
        if ("APPLIED".equals(quote.getStatus()) && quote.getReservation() != null) {
            return reservationService.getReservationById(quote.getReservation().getId());
        }
        if (!"QUOTED".equals(quote.getStatus()) || !LocalDateTime.now().isBefore(quote.getExpiresAt())) {
            throw new FinancialException(FinancialErrorCode.INVALID_STATE_TRANSITION,
                    "The staff booking quote expired; request a fresh quote.");
        }
        RoomType roomType = roomTypeRepository.findByIdForUpdate(quote.getRoomType().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại phòng."));
        PropertyPaymentConfiguration configuration = paymentConfigurationRepository.findByHotelId(quote.getHotel().getId())
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.POLICY_NOT_CONFIGURED));
        long nights = roomAvailabilityService.getNights(quote.getCheckInDate(), quote.getCheckOutDate());
        BigDecimal currentTotal = roomAvailabilityService.calculateTotal(roomType.getBasePrice(), nights, quote.getQuantity());
        long available = roomAvailabilityService.countAvailableRooms(roomType.getId(), quote.getCheckInDate(), quote.getCheckOutDate());
        if (roomType.getBasePrice().compareTo(quote.getBasePrice()) != 0
                || currentTotal.compareTo(quote.getTotalAmount()) != 0
                || !configuration.getId().equals(quote.getPaymentConfigurationId())
                || configuration.getVersion() != quote.getPaymentConfigurationVersion()
                || available < quote.getQuantity()) {
            throw new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION,
                    "Price, deposit policy or inventory changed; request a fresh quote.");
        }
        ReservationRequest request = requestFrom(quote);
        ReservationDTO created = reservationService.createStaffReservation(
                quote.getCustomer().getId(), request, scope, idempotencyKey);
        quote.setStatus("APPLIED");
        quote.setReservation(reservationRepository.findById(created.getId()).orElseThrow());
        quoteRepository.save(quote);
        return created;
    }

    @Transactional(readOnly = true)
    public ReservationDTO replay(String quotePublicId) {
        StaffBookingQuote quote = quoteRepository.findByPublicId(requireText(quotePublicId)).orElse(null);
        if (quote == null || !"APPLIED".equals(quote.getStatus()) || quote.getReservation() == null) return null;
        propertyAccessService.requireAccessibleOrNotFound(quote.getHotel().getId(), "báo giá đặt phòng");
        return reservationService.getReservationById(quote.getReservation().getId());
    }

    private ReservationRequest requestFrom(StaffBookingQuote quote) {
        ReservationRequest request = new ReservationRequest();
        request.setRoomTypeId(quote.getRoomType().getId()); request.setCheckInDate(quote.getCheckInDate());
        request.setCheckOutDate(quote.getCheckOutDate()); request.setQuantity(quote.getQuantity());
        request.setAdults(quote.getAdults()); request.setChildren(quote.getChildren());
        request.setGuests(quote.getAdults() + quote.getChildren()); request.setPaymentMethod(quote.getPaymentMethod());
        request.setSpecialRequests(quote.getSpecialRequests()); return request;
    }

    private User activeCustomer(Long id) {
        return userRepository.findByIdForUpdate(id).filter(user -> "ACTIVE".equalsIgnoreCase(user.getStatus()))
                .filter(user -> user.getRoles() != null && user.getRoles().stream()
                        .anyMatch(role -> "CUSTOMER".equalsIgnoreCase(role.getCode())))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng."));
    }

    private void validateRequest(QuoteRequest request) {
        if (request == null || request.hotelId() == null || request.customerId() == null || request.roomTypeId() == null
                || request.checkInDate() == null || request.checkOutDate() == null || request.quantity() < 1
                || request.adults() < 1 || request.children() < 0 || request.paymentMethod() == null
                || !request.checkOutDate().isAfter(request.checkInDate()) || request.checkInDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Staff booking quote fields are invalid.");
        }
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "";
        int at = email.indexOf('@'); return email.substring(0, Math.min(2, at)) + "***" + email.substring(at);
    }
    private String requireText(String value) { if (value == null || value.isBlank()) throw new IllegalArgumentException("quoteId is required."); return value.trim(); }
    private QuoteResponse response(StaffBookingQuote q, boolean replayed) { return new QuoteResponse(q.getPublicId(), q.getHotel().getId(), q.getCustomer().getId(), q.getRoomType().getId(), q.getRoomType().getNameVi(), q.getCheckInDate(), q.getCheckOutDate(), q.getQuantity(), q.getAdults(), q.getChildren(), q.getAvailableRooms(), q.getBasePrice(), q.getTotalAmount(), q.getDepositAmount(), "VND", q.getExpiresAt(), q.getStatus(), replayed); }

    public record ContextResponse(Long hotelId, String hotelName, List<CustomerOption> customers, List<RoomTypeOption> roomTypes, List<String> paymentMethods) {}
    public record CustomerOption(Long id, String fullName, String username, String maskedEmail) {}
    public record RoomTypeOption(Long id, String code, String nameVi, String nameEn, BigDecimal basePrice, Integer maxAdults, Integer maxChildren, Integer maxGuests) {}
    public record QuoteRequest(Long hotelId, Long customerId, Long roomTypeId, LocalDate checkInDate, LocalDate checkOutDate, int quantity, int adults, int children, String paymentMethod, String specialRequests) {}
    public record QuoteResponse(String quoteId, Long hotelId, Long customerId, Long roomTypeId, String roomTypeName, LocalDate checkInDate, LocalDate checkOutDate, int quantity, int adults, int children, long availableRooms, BigDecimal basePrice, BigDecimal totalAmount, BigDecimal depositAmount, String currency, LocalDateTime expiresAt, String status, boolean replayed) {}
    public record CreateRequest(String quoteId) {}
}
