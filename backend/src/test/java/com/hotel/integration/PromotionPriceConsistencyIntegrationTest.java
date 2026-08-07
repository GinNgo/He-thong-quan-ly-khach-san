package com.hotel.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.BackendApplication;
import com.hotel.dtos.InvoiceDTO;
import com.hotel.entities.Hotel;
import com.hotel.entities.Payment;
import com.hotel.entities.PromotionCampaign;
import com.hotel.entities.RefundRequest;
import com.hotel.entities.Reservation;
import com.hotel.entities.Room;
import com.hotel.entities.RoomType;
import com.hotel.propertycommerce.config.PropertyPaymentConfiguration;
import com.hotel.propertycommerce.config.PropertyPaymentConfigurationRepository;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.PaymentRepository;
import com.hotel.repositories.PromotionCampaignRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.RoomRepository;
import com.hotel.repositories.RoomTypeRepository;
import com.hotel.services.InvoiceService;
import com.hotel.services.RefundService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = BackendApplication.class,
        properties = "payment.property.encryption-key=test-property-payment-encryption-key")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PromotionPriceConsistencyIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired HotelRepository hotelRepository;
    @Autowired RoomTypeRepository roomTypeRepository;
    @Autowired RoomRepository roomRepository;
    @Autowired PromotionCampaignRepository campaignRepository;
    @Autowired PropertyPaymentConfigurationRepository paymentConfigurationRepository;
    @Autowired ReservationRepository reservationRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired InvoiceService invoiceService;
    @Autowired RefundService refundService;

    private Hotel hotel;
    private RoomType roomType;
    private LocalDate checkIn;
    private LocalDate checkOut;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        hotel = new Hotel();
        hotel.setName("Quote Hotel " + suffix);
        hotel.setNameVi("Khách sạn báo giá " + suffix);
        hotel.setNameEn("Quote Hotel " + suffix);
        hotel.setCode("QUOTE-" + suffix);
        hotel.setAddressLine("1 Canonical Price Street");
        hotel.setCity("Đà Nẵng");
        hotel.setCountry("Việt Nam");
        hotel.setStatus("ACTIVE");
        hotel.setApprovalStatus("APPROVED");
        hotel.setOperationStatus("ACTIVE");
        hotel.setIsDemo(false);
        hotel = hotelRepository.saveAndFlush(hotel);

        roomType = new RoomType();
        roomType.setHotel(hotel);
        roomType.setCode("STANDARD-" + suffix);
        roomType.setNameVi("Phòng tiêu chuẩn");
        roomType.setNameEn("Standard room");
        roomType.setBasePrice(new BigDecimal("500000"));
        roomType.setMaxGuest(4);
        roomType.setMaxGuests(4);
        roomType.setMaxAdults(4);
        roomType.setMaxChildren(4);
        roomType.setStatus("ACTIVE");
        roomType = roomTypeRepository.saveAndFlush(roomType);

        Room room = new Room();
        room.setHotel(hotel);
        room.setRoomType(roomType);
        room.setRoomNumber("Q-101-" + suffix);
        room.setFloor(1);
        room.setStatus("AVAILABLE");
        room.setMaintenanceStatus("NONE");
        room.setHousekeepingStatus("CLEAN");
        room.setIsDemo(false);
        room.setMaxGuests(4);
        roomRepository.saveAndFlush(room);

        PromotionCampaign campaign = new PromotionCampaign();
        campaign.setCode("AUTO10-" + suffix.toUpperCase());
        campaign.setOwnerType("TENANT");
        campaign.setHotel(hotel);
        campaign.setApplicationType("AUTOMATIC");
        campaign.setNameVi("Giảm 10%");
        campaign.setNameEn("10% off");
        campaign.setDiscountType("PERCENT");
        campaign.setDiscountValue(new BigDecimal("10"));
        campaign.setStartsAt(Instant.now().minusSeconds(3600));
        campaign.setEndsAt(Instant.now().plusSeconds(3600));
        campaign.setTimezone("Asia/Ho_Chi_Minh");
        campaign.setEligibilityJson("{}");
        campaign.setStackingPolicy("NO_COUPON");
        campaign.setPriority(10);
        campaign.setStatus("ACTIVE");
        campaignRepository.saveAndFlush(campaign);

        PropertyPaymentConfiguration configuration = new PropertyPaymentConfiguration(hotel);
        ReflectionTestUtils.setField(configuration, "enabled", true);
        ReflectionTestUtils.setField(configuration, "environment", "SIMULATOR");
        ReflectionTestUtils.setField(configuration, "depositPolicyType", "NONE");
        ReflectionTestUtils.setField(configuration, "paymentExpiryMinutes", 15);
        paymentConfigurationRepository.saveAndFlush(configuration);

        checkIn = LocalDate.now().plusDays(10);
        checkOut = checkIn.plusDays(2);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void searchDetailBookingInvoiceAndRefundUseTheSameCanonicalTotal() throws Exception {
        String quoteBody = objectMapper.writeValueAsString(Map.of(
                "propertyId", hotel.getId(),
                "roomTypeId", roomType.getId(),
                "checkInDate", checkIn.toString(),
                "checkOutDate", checkOut.toString(),
                "quantity", 1,
                "adultCount", 2,
                "childCount", 0));
        JsonNode quote = objectMapper.readTree(mockMvc.perform(post("/api/public/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(quote.path("baseSubtotal").decimalValue()).isEqualByComparingTo("1000000");
        assertThat(quote.path("taxesAndFees").decimalValue()).isEqualByComparingTo("150000");
        assertThat(quote.path("totalDiscount").decimalValue()).isEqualByComparingTo("100000");
        BigDecimal canonicalTotal = quote.path("finalTotal").decimalValue();
        assertThat(canonicalTotal).isEqualByComparingTo("1050000");

        JsonNode roomTypes = objectMapper.readTree(mockMvc.perform(get("/api/room-types/public/hotel/{hotelId}", hotel.getId())
                        .param("checkIn", checkIn.toString())
                        .param("checkOut", checkOut.toString())
                        .param("guests", "2"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(roomTypes.get(0).path("quote").path("finalTotal").decimalValue())
                .isEqualByComparingTo(canonicalTotal);
        assertThat(roomTypes.get(0).path("totalPrice").decimalValue())
                .isEqualByComparingTo(canonicalTotal);

        JsonNode search = objectMapper.readTree(mockMvc.perform(get("/api/public/properties/search")
                        .param("checkInDate", checkIn.toString())
                        .param("checkOutDate", checkOut.toString())
                        .param("adultCount", "2")
                        .param("childCount", "0")
                        .param("roomCount", "1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode searchHotel = objectMapper.convertValue(search.path("content"), new TypeReference<List<JsonNode>>() { })
                .stream()
                .filter(item -> item.path("id").asLong() == hotel.getId())
                .findFirst()
                .orElseThrow();
        assertThat(searchHotel.path("quote").path("finalTotal").decimalValue())
                .isEqualByComparingTo(canonicalTotal);
        assertThat(searchHotel.path("pricing").path("totalAmount").decimalValue())
                .isEqualByComparingTo(canonicalTotal);

        String bookingBody = objectMapper.writeValueAsString(Map.ofEntries(
                Map.entry("roomTypeId", roomType.getId()),
                Map.entry("checkInDate", checkIn.toString()),
                Map.entry("checkOutDate", checkOut.toString()),
                Map.entry("guests", 2),
                Map.entry("quantity", 1),
                Map.entry("adults", 2),
                Map.entry("children", 0),
                Map.entry("firstName", "Price"),
                Map.entry("lastName", "Tester"),
                Map.entry("phone", "0900000000"),
                Map.entry("paymentMethod", "PAY_AT_HOTEL")));
        JsonNode booking = objectMapper.readTree(mockMvc.perform(post("/api/reservations/public/book")
                        .header("Idempotency-Key", "price-consistency-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        Long reservationId = booking.path("id").asLong();
        assertThat(booking.path("totalAmount").decimalValue()).isEqualByComparingTo(canonicalTotal);
        assertThat(booking.path("quote").path("finalTotal").decimalValue()).isEqualByComparingTo(canonicalTotal);

        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(reservation.getPricingBaseSubtotal()).isEqualByComparingTo("1000000");
        assertThat(reservation.getPricingTaxAmount()).isEqualByComparingTo("150000");
        assertThat(reservation.getPricingDiscountAmount()).isEqualByComparingTo("100000");
        assertThat(reservation.getTotalAmount()).isEqualByComparingTo(canonicalTotal);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                reservation.getUser().getUsername(), "N/A", List.of()));
        InvoiceDTO invoice = invoiceService.generateInvoice(reservationId);
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo(canonicalTotal);

        Payment payment = new Payment();
        payment.setReservation(reservation);
        payment.setAmount(canonicalTotal);
        payment.setPaymentMethod("VNPAY");
        payment.setStatus("SUCCEEDED");
        payment.setTransactionId("PAY-" + UUID.randomUUID());
        payment.setPaymentDate(LocalDateTime.now());
        paymentRepository.saveAndFlush(payment);
        List<RefundRequest> refunds = refundService.requestRefundsForSuccessfulPayments(
                reservationId, "PRICE_CONSISTENCY_TEST");
        assertThat(refunds).hasSize(1);
        assertThat(refunds.get(0).getRequestedAmount()).isEqualByComparingTo(canonicalTotal);
    }
}
