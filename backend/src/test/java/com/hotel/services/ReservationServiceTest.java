package com.hotel.services;

import com.hotel.dtos.ReservationDTO;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.entities.Hotel;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

        mockReservation = new Reservation();
        mockReservation.setId(1L);
        mockReservation.setUser(mockUser);
        mockReservation.setHotel(mockHotel);
        mockReservation.setStatus("PENDING");
        mockReservation.setCheckInDate(LocalDate.now());
        mockReservation.setCheckOutDate(LocalDate.now().plusDays(2));
    }

    @Test
    void testCheckIn_Success() {
        when(propertyAccessService.isSystemAdministrator()).thenReturn(true);
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(mockReservation));
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
        when(reservationRepository.findById(99L)).thenReturn(Optional.empty());

        Exception exception = assertThrows(RuntimeException.class, () -> {
            reservationService.updateReservationStatus(99L, "CHECKED_IN");
        });

        assertTrue(exception.getMessage().contains("Không tìm thấy") || exception.getMessage().contains("not found"));
    }
}
