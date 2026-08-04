package com.hotel.services;

import com.hotel.dtos.AssignRoomsRequest;
import com.hotel.dtos.RoomAssignmentMutationRequest;
import com.hotel.dtos.RoomAssignmentReleaseRequest;
import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.propertycommerce.checkout.CheckoutOperationsService;
import com.hotel.propertycommerce.config.PropertyPaymentConfigurationRepository;
import com.hotel.propertycommerce.invoice.InvoiceFinalizationService;
import com.hotel.repositories.HotelServiceRepository;
import com.hotel.repositories.PaymentRepository;
import com.hotel.repositories.ReservationDetailRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.ReservationRoomRepository;
import com.hotel.repositories.ReservationServiceItemRepository;
import com.hotel.repositories.RoomRepository;
import com.hotel.repositories.RoomTypeRepository;
import com.hotel.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationLifecyclePropertyIdorTest {

    @Mock private ReservationRepository reservationRepository;
    @Mock private ReservationDetailRepository reservationDetailRepository;
    @Mock private ReservationRoomRepository reservationRoomRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private RoomTypeRepository roomTypeRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoomAvailabilityService roomAvailabilityService;
    @Mock private NotificationService notificationService;
    @Mock private EmailService emailService;
    @Mock private ReservationServiceItemRepository reservationServiceItemRepository;
    @Mock private HotelServiceRepository hotelServiceRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PropertyAccessService propertyAccessService;
    @Mock private ReservationHoldService reservationHoldService;
    @Mock private PropertyPaymentConfigurationRepository propertyPaymentConfigurationRepository;
    @Mock private InvoiceFinalizationService invoiceFinalizationService;
    @Mock private CheckoutOperationsService checkoutOperationsService;
    @Mock private PublicInventoryEligibilityPolicy publicInventoryEligibilityPolicy;
    @Mock private OperationalAuditService operationalAuditService;

    @InjectMocks private ReservationService reservationService;

    private Reservation foreignReservation;

    @BeforeEach
    void setUp() {
        Hotel foreignHotel = new Hotel();
        foreignHotel.setId(99L);
        foreignReservation = new Reservation();
        foreignReservation.setId(7L);
        foreignReservation.setHotel(foreignHotel);
        foreignReservation.setStatus("CONFIRMED");

        lenient().when(reservationRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(foreignReservation));
        doThrow(new ResourceNotFoundException("booking not found"))
                .when(propertyAccessService).requireAccessibleOrNotFound(99L, "booking");
    }

    @ParameterizedTest
    @EnumSource(OperationalAction.class)
    void crossPropertyLifecycleMutationFailsClosedBeforeDomainWrites(OperationalAction action) {
        assertThatThrownBy(() -> execute(action))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("booking not found");

        verifyNoInteractions(reservationDetailRepository, reservationRoomRepository, roomRepository);
    }

    @Test
    void crossPropertyAvailableRoomLookupIsHiddenBeforeInventoryReads() {
        when(reservationRepository.findById(7L)).thenReturn(Optional.of(foreignReservation));

        assertThatThrownBy(() -> reservationService.getAvailableRoomContext(7L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("booking not found");

        verifyNoInteractions(reservationDetailRepository, reservationRoomRepository, roomRepository);
    }

    private void execute(OperationalAction action) {
        switch (action) {
            case ASSIGN -> {
                AssignRoomsRequest request = new AssignRoomsRequest();
                request.setRoomIds(List.of(10L));
                reservationService.assignRooms(7L, request);
            }
            case REASSIGN -> reservationService.updateRoomAssignment(
                    7L, new RoomAssignmentMutationRequest(List.of(10L), "Đổi phòng theo yêu cầu"));
            case RELEASE -> reservationService.releaseRoomAssignment(
                    7L, new RoomAssignmentReleaseRequest("Giải phóng để bảo trì"));
            case CHECK_IN -> reservationService.checkIn(7L);
            case CANCEL -> reservationService.cancelOperational(7L);
            case NO_SHOW -> reservationService.markNoShow(7L);
        }
    }

    private enum OperationalAction {
        ASSIGN,
        REASSIGN,
        RELEASE,
        CHECK_IN,
        CANCEL,
        NO_SHOW
    }
}
