package com.hotel.services;

import com.hotel.dtos.ReservationDTO;
import com.hotel.dtos.ReservationRequest;
import com.hotel.entities.Reservation;
import com.hotel.entities.RoomType;
import com.hotel.entities.User;
import com.hotel.entities.Hotel;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.propertycommerce.config.PropertyPaymentConfiguration;
import com.hotel.propertycommerce.config.PropertyPaymentConfigurationRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.RoomRepository;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HotelRepository hotelRepository;
    
    @Mock
    private RoomRepository roomRepository;

    @Mock
    private com.hotel.repositories.ReservationDetailRepository reservationDetailRepository;

    @Mock
    private com.hotel.repositories.ReservationRoomRepository reservationRoomRepository;

    @Mock
    private com.hotel.repositories.RoomTypeRepository roomTypeRepository;

    @Mock
    private com.hotel.services.RoomAvailabilityService roomAvailabilityService;

    @Mock
    private com.hotel.services.NotificationService notificationService;

    @Mock
    private com.hotel.services.EmailService emailService;

    @Mock
    private com.hotel.repositories.ReservationServiceItemRepository reservationServiceItemRepository;

    @Mock
    private com.hotel.repositories.HotelServiceRepository hotelServiceRepository;

    @Mock
    private com.hotel.services.PropertyAccessService propertyAccessService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private PropertyPaymentConfigurationRepository propertyPaymentConfigurationRepository;

    @InjectMocks
    private ReservationService reservationService;

    private User mockUser;
    private Hotel mockHotel;
    private Reservation mockReservation;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUsername("testcustomer");

        mockHotel = new Hotel();
        mockHotel.setId(1L);
        mockHotel.setName("Test Hotel");
        mockHotel.setStatus("ACTIVE");
        mockHotel.setOperationStatus("ACTIVE");
        mockHotel.setApprovalStatus("APPROVED");

        mockReservation = new Reservation();
        mockReservation.setId(1L);
        mockReservation.setUser(mockUser);
        mockReservation.setHotel(mockHotel);
        mockReservation.setStatus("PENDING");
        mockReservation.setCheckInDate(LocalDate.now());
        mockReservation.setCheckOutDate(LocalDate.now().plusDays(2));
    }

    @Test
    void getAllReservations_AsSystemAdministrator_ReturnsAllHotels() {
        when(propertyAccessService.isSystemAdministrator()).thenReturn(true);
        when(reservationRepository.findAll()).thenReturn(java.util.List.of(mockReservation));
        when(reservationDetailRepository.findByReservationId(1L)).thenReturn(java.util.List.of());

        java.util.List<ReservationDTO> result = reservationService.getAllReservations();

        assertEquals(java.util.List.of(1L), result.stream().map(ReservationDTO::getId).toList());
        verify(reservationRepository).findAll();
        verify(reservationRepository, never()).findByHotelIdIn(any());
    }

    @Test
    void getAllReservations_AsPropertyStaff_ReturnsOnlyAccessibleHotels() {
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(propertyAccessService.accessibleHotelIds()).thenReturn(java.util.Set.of(1L));
        when(reservationRepository.findByHotelIdIn(java.util.Set.of(1L)))
                .thenReturn(java.util.List.of(mockReservation));
        when(reservationDetailRepository.findByReservationId(1L)).thenReturn(java.util.List.of());

        java.util.List<ReservationDTO> result = reservationService.getAllReservations();

        assertEquals(java.util.List.of(1L), result.stream().map(ReservationDTO::getId).toList());
        verify(reservationRepository).findByHotelIdIn(java.util.Set.of(1L));
        verify(reservationRepository, never()).findAll();
    }

    @Test
    void getAllReservations_AsUnassignedStaff_ReturnsEmptyWithoutQueryingReservations() {
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(propertyAccessService.accessibleHotelIds()).thenReturn(java.util.Set.of());

        assertTrue(reservationService.getAllReservations().isEmpty());

        verify(reservationRepository, never()).findAll();
        verify(reservationRepository, never()).findByHotelIdIn(any());
    }


    @Test
    void createReservationCalculatesAndPersistsServerOwnedPercentageDeposit() {
        ReservationRequest request = bookingRequest();
        RoomType roomType = roomType();
        PropertyPaymentConfiguration configuration = paymentConfiguration(true, "PERCENTAGE", BigDecimal.valueOf(30));
        when(userRepository.findByUsername("testcustomer")).thenReturn(Optional.of(mockUser));
        when(roomTypeRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(roomType));
        when(roomAvailabilityService.countAvailableRooms(5L, request.getCheckInDate(), request.getCheckOutDate()))
                .thenReturn(1L);
        when(roomAvailabilityService.getNights(request.getCheckInDate(), request.getCheckOutDate())).thenReturn(2L);
        when(roomAvailabilityService.calculateTotal(BigDecimal.valueOf(600_000), 2L, 1))
                .thenReturn(BigDecimal.valueOf(1_200_000));
        when(propertyPaymentConfigurationRepository.findByHotelId(1L)).thenReturn(Optional.of(configuration));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation reservation = invocation.getArgument(0);
            reservation.setId(99L);
            return reservation;
        });
        when(reservationDetailRepository.findByReservationId(99L)).thenReturn(java.util.List.of());

        reservationService.createReservation("testcustomer", request);

        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).save(captor.capture());
        Reservation saved = captor.getValue();
        assertEquals(0, BigDecimal.valueOf(1_200_000).compareTo(saved.getDepositBookingTotal()));
        assertEquals(0, BigDecimal.valueOf(360_000).compareTo(saved.getDepositRequired()));
        assertEquals("PERCENTAGE", saved.getDepositPolicyType());
        assertEquals(0, BigDecimal.valueOf(30).compareTo(saved.getDepositPolicyValue()));
        assertEquals("VND", saved.getDepositCurrency());
        assertEquals(11L, saved.getDepositConfigurationId());
        assertEquals(4L, saved.getDepositConfigurationVersion());
    }

    @Test
    void createReservationRejectsMissingPropertyDepositPolicyWithoutPersistingBooking() {
        ReservationRequest request = bookingRequest();
        RoomType roomType = roomType();
        when(userRepository.findByUsername("testcustomer")).thenReturn(Optional.of(mockUser));
        when(roomTypeRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(roomType));
        when(roomAvailabilityService.countAvailableRooms(5L, request.getCheckInDate(), request.getCheckOutDate()))
                .thenReturn(1L);
        when(roomAvailabilityService.getNights(request.getCheckInDate(), request.getCheckOutDate())).thenReturn(2L);
        when(roomAvailabilityService.calculateTotal(BigDecimal.valueOf(600_000), 2L, 1))
                .thenReturn(BigDecimal.valueOf(1_200_000));
        when(propertyPaymentConfigurationRepository.findByHotelId(1L)).thenReturn(Optional.empty());

        FinancialException exception = assertThrows(FinancialException.class,
                () -> reservationService.createReservation("testcustomer", request));

        assertEquals(FinancialErrorCode.POLICY_NOT_CONFIGURED, exception.code());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void createReservationRejectsDisabledPropertyPayments() {
        ReservationRequest request = bookingRequest();
        RoomType roomType = roomType();
        when(userRepository.findByUsername("testcustomer")).thenReturn(Optional.of(mockUser));
        when(roomTypeRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(roomType));
        when(roomAvailabilityService.countAvailableRooms(5L, request.getCheckInDate(), request.getCheckOutDate()))
                .thenReturn(1L);
        when(roomAvailabilityService.getNights(request.getCheckInDate(), request.getCheckOutDate())).thenReturn(2L);
        when(roomAvailabilityService.calculateTotal(BigDecimal.valueOf(600_000), 2L, 1))
                .thenReturn(BigDecimal.valueOf(1_200_000));
        when(propertyPaymentConfigurationRepository.findByHotelId(1L))
                .thenReturn(Optional.of(paymentConfiguration(false, "NONE", null)));

        FinancialException exception = assertThrows(FinancialException.class,
                () -> reservationService.createReservation("testcustomer", request));

        assertEquals(FinancialErrorCode.PAYMENT_ENVIRONMENT_DISABLED, exception.code());
        verify(reservationRepository, never()).save(any());
    }
    @Test
    void testCheckIn_Success() {
        when(propertyAccessService.isSystemAdministrator()).thenReturn(true);
        when(reservationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockReservation));
        when(reservationDetailRepository.findByReservationId(1L)).thenReturn(java.util.Collections.emptyList());
        when(reservationRoomRepository.findByReservationDetailReservationId(1L)).thenReturn(java.util.Collections.emptyList());
        when(reservationRepository.save(any(Reservation.class))).thenReturn(mockReservation);

        ReservationDTO updatedReservation = reservationService.updateReservationStatus(1L, "CHECKED_IN");

        assertNotNull(updatedReservation);
        assertEquals("CHECKED_IN", updatedReservation.getStatus());
        verify(reservationRepository).save(any(Reservation.class));
    }

    @Test
    void testCheckIn_Failure_NotFound() {
        when(reservationRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        Exception exception = assertThrows(RuntimeException.class, () -> {
            reservationService.updateReservationStatus(99L, "CHECKED_IN");
        });

        assertTrue(exception.getMessage().contains("Không tìm thấy") || exception.getMessage().contains("not found"));
    }

    @Test
    void cancelMyReservation_AsOwner_ShouldRefundAndReleaseRooms() {
        mockReservation.setStatus("CONFIRMED");
        com.hotel.entities.Room room = new com.hotel.entities.Room();
        room.setStatus("OCCUPIED");
        com.hotel.entities.ReservationRoom assignment = new com.hotel.entities.ReservationRoom();
        assignment.setRoom(room);
        assignment.setStatus("ASSIGNED");

        when(reservationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockReservation));
        when(reservationRoomRepository.findByReservationDetailReservationId(1L))
                .thenReturn(java.util.List.of(assignment));
        when(reservationRepository.save(any(Reservation.class))).thenReturn(mockReservation);

        ReservationDTO result = reservationService.cancelMyReservation(1L, "testcustomer");

        assertEquals("CANCELLED", result.getStatus());
        assertEquals("AVAILABLE", room.getStatus());
        assertEquals("RELEASED", assignment.getStatus());
        verify(paymentService).refundSuccessfulPayments(1L);
        verify(reservationRepository).save(mockReservation);
    }

    @Test
    void cancelMyReservation_AsOtherUser_ShouldReject() {
        when(reservationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockReservation));

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> reservationService.cancelMyReservation(1L, "attacker"));

        verify(paymentService, never()).refundSuccessfulPayments(any());
    }

    @Test
    void cancelMyReservation_AfterCheckIn_ShouldReject() {
        mockReservation.setStatus("CHECKED_IN");
        when(reservationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockReservation));

        assertThrows(IllegalStateException.class,
                () -> reservationService.cancelMyReservation(1L, "testcustomer"));

        verify(paymentService, never()).refundSuccessfulPayments(any());
    }

    @Test
    void cancelMyReservation_AlreadyCancelled_ShouldBeNoOp() {
        mockReservation.setStatus("CANCELLED");
        when(reservationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockReservation));

        ReservationDTO result = reservationService.cancelMyReservation(1L, "testcustomer");

        assertEquals("CANCELLED", result.getStatus());
        verify(paymentService, never()).refundSuccessfulPayments(any());
        verify(reservationRepository, never()).save(any());
    }
    private ReservationRequest bookingRequest() {
        ReservationRequest request = new ReservationRequest();
        request.setRoomTypeId(5L);
        request.setCheckInDate(LocalDate.of(2026, 8, 10));
        request.setCheckOutDate(LocalDate.of(2026, 8, 12));
        request.setQuantity(1);
        request.setAdults(2);
        request.setChildren(0);
        request.setGuests(2);
        request.setPaymentMethod("MANUAL_TRANSFER");
        return request;
    }

    private RoomType roomType() {
        RoomType roomType = new RoomType();
        roomType.setId(5L);
        roomType.setHotel(mockHotel);
        roomType.setNameVi("Deluxe room");
        roomType.setNameEn("Deluxe room");
        roomType.setBasePrice(BigDecimal.valueOf(600_000));
        roomType.setStatus("ACTIVE");
        roomType.setMaxGuests(2);
        roomType.setMaxAdults(2);
        roomType.setMaxChildren(1);
        return roomType;
    }

    private PropertyPaymentConfiguration paymentConfiguration(
            boolean enabled,
            String policyType,
            BigDecimal policyValue) {
        PropertyPaymentConfiguration configuration = new PropertyPaymentConfiguration(mockHotel);
        ReflectionTestUtils.setField(configuration, "id", 11L);
        ReflectionTestUtils.setField(configuration, "version", 4L);
        ReflectionTestUtils.setField(configuration, "enabled", enabled);
        ReflectionTestUtils.setField(configuration, "depositPolicyType", policyType);
        ReflectionTestUtils.setField(configuration, "depositValue", policyValue);
        return configuration;
    }
}
