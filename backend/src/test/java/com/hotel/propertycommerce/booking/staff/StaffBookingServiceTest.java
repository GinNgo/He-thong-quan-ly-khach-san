package com.hotel.propertycommerce.booking.staff;

import com.hotel.dtos.ReservationDTO;
import com.hotel.dtos.ReservationRequest;
import com.hotel.entities.Hotel;
import com.hotel.entities.Role;
import com.hotel.entities.RoomType;
import com.hotel.entities.User;
import com.hotel.propertycommerce.config.PropertyPaymentConfiguration;
import com.hotel.propertycommerce.config.PropertyPaymentConfigurationRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.RoomTypeRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.services.PropertyAccessService;
import com.hotel.services.ReservationService;
import com.hotel.services.RoomAvailabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffBookingServiceTest {
    @Mock StaffBookingQuoteRepository quoteRepository;
    @Mock RoomTypeRepository roomTypeRepository;
    @Mock UserRepository userRepository;
    @Mock PropertyPaymentConfigurationRepository configurationRepository;
    @Mock RoomAvailabilityService availabilityService;
    @Mock PropertyAccessService propertyAccessService;
    @Mock ReservationService reservationService;
    @Mock ReservationRepository reservationRepository;
    @InjectMocks StaffBookingService service;

    Hotel hotel; RoomType roomType; User customer; PropertyPaymentConfiguration configuration;

    @BeforeEach void setUp() {
        ReflectionTestUtils.setField(service, "quoteTtlMinutes", 2L);
        hotel = new Hotel(); hotel.setId(3L); hotel.setName("Luxe Demo");
        roomType = new RoomType(); roomType.setId(7L); roomType.setHotel(hotel); roomType.setStatus("ACTIVE");
        roomType.setNameVi("Deluxe"); roomType.setNameEn("Deluxe"); roomType.setBasePrice(BigDecimal.valueOf(1_000_000));
        Role role = new Role(); role.setCode("CUSTOMER");
        customer = new User(); customer.setId(8L); customer.setStatus("ACTIVE"); customer.setRoles(Set.of(role));
        configuration = new PropertyPaymentConfiguration(hotel);
        ReflectionTestUtils.setField(configuration, "id", 12L); ReflectionTestUtils.setField(configuration, "enabled", true);
        ReflectionTestUtils.setField(configuration, "depositPolicyType", "PERCENTAGE"); ReflectionTestUtils.setField(configuration, "depositValue", BigDecimal.valueOf(25));
        ReflectionTestUtils.setField(configuration, "version", 4L);
    }

    @Test void quoteUsesServerPriceAvailabilityAndDeposit() {
        when(roomTypeRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(roomType));
        when(userRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(customer));
        when(availabilityService.countAvailableRooms(7L, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12))).thenReturn(3L);
        when(availabilityService.getNights(any(), any())).thenReturn(2L);
        when(availabilityService.calculateTotal(BigDecimal.valueOf(1_000_000), 2, 1)).thenReturn(BigDecimal.valueOf(2_000_000));
        when(configurationRepository.findByHotelId(3L)).thenReturn(Optional.of(configuration));
        when(quoteRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.quote(request());

        assertThat(response.totalAmount()).isEqualByComparingTo("2000000");
        assertThat(response.depositAmount()).isEqualByComparingTo("500000");
        assertThat(response.availableRooms()).isEqualTo(3);
        verify(propertyAccessService).requireAccessibleOrNotFound(3L, "cơ sở");
    }

    @Test void createUsesSelectedCustomerAndLeavesPhysicalAssignmentToDedicatedWorkflow() {
        StaffBookingQuote quote = quote();
        when(quoteRepository.findByPublicIdForUpdate("q1")).thenReturn(Optional.of(quote));
        when(roomTypeRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(roomType));
        when(configurationRepository.findByHotelId(3L)).thenReturn(Optional.of(configuration));
        when(availabilityService.getNights(any(), any())).thenReturn(2L);
        when(availabilityService.calculateTotal(BigDecimal.valueOf(1_000_000), 2, 1)).thenReturn(BigDecimal.valueOf(2_000_000));
        when(availabilityService.countAvailableRooms(
                org.mockito.ArgumentMatchers.any(Long.class), any(), any())).thenReturn(2L);
        ReservationDTO created = new ReservationDTO(); created.setId(42L);
        when(reservationService.createStaffReservation(any(), any(), any(), any())).thenReturn(created);
        com.hotel.entities.Reservation entity = new com.hotel.entities.Reservation(); entity.setId(42L);
        when(reservationRepository.findById(42L)).thenReturn(Optional.of(entity));

        service.create("q1", "staff:3", "create-key");

        ArgumentCaptor<ReservationRequest> request = ArgumentCaptor.forClass(ReservationRequest.class);
        verify(reservationService).createStaffReservation(org.mockito.ArgumentMatchers.eq(8L), request.capture(), org.mockito.ArgumentMatchers.eq("staff:3"), org.mockito.ArgumentMatchers.eq("create-key"));
        assertThat(request.getValue().getRoomTypeId()).isEqualTo(7L);
        assertThat(quote.getStatus()).isEqualTo("APPLIED");
        assertThat(quote.getReservation().getId()).isEqualTo(42L);
    }

    private StaffBookingService.QuoteRequest request() { return new StaffBookingService.QuoteRequest(3L, 8L, 7L, LocalDate.of(2026,8,10), LocalDate.of(2026,8,12), 1, 2, 0, "CASH", null); }
    private StaffBookingQuote quote() { StaffBookingQuote q = new StaffBookingQuote(); q.setPublicId("q1"); q.setHotel(hotel); q.setCustomer(customer); q.setRoomType(roomType); q.setCheckInDate(LocalDate.of(2026,8,10)); q.setCheckOutDate(LocalDate.of(2026,8,12)); q.setQuantity(1); q.setAdults(2); q.setChildren(0); q.setPaymentMethod("CASH"); q.setBasePrice(BigDecimal.valueOf(1_000_000)); q.setTotalAmount(BigDecimal.valueOf(2_000_000)); q.setDepositAmount(BigDecimal.valueOf(500_000)); q.setPaymentConfigurationId(12L); q.setPaymentConfigurationVersion(4L); q.setAvailableRooms(2L); q.setExpiresAt(LocalDateTime.now().plusMinutes(1)); q.setStatus("QUOTED"); return q; }
}
