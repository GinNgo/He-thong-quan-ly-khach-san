package com.hotel.propertycommerce.invoice;

import com.hotel.controllers.InvoiceController;
import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.repositories.InvoiceRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.services.PropertyAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyInvoiceCompatibilityControllerTest {

    @Mock private InvoiceRepository legacyInvoiceRepository;
    @Mock private PropertyInvoiceRepository invoiceRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private PropertyAccessService propertyAccessService;

    @Test
    void writeShapedCompatibilityRouteReturnsExistingFinalizedInvoiceWithoutLegacyMutation() {
        PropertyInvoice invoice = finalizedInvoice();
        when(reservationRepository.findById(42L)).thenReturn(Optional.of(invoice.getReservation()));
        when(invoiceRepository.findByReservationIdAndStatus(42L, PropertyInvoice.Status.FINALIZED))
                .thenReturn(Optional.of(invoice));
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(propertyAccessService.currentUser()).thenReturn(invoice.getReservation().getUser());
        InvoiceController controller = new InvoiceController(
                legacyInvoiceRepository, invoiceRepository, reservationRepository, propertyAccessService);

        var response = controller.generateInvoice(42L);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(88L);
        assertThat(response.getBody().getInvoiceCode()).isEqualTo("INV-3-42");
        assertThat(response.getBody().getTotalAmount()).isEqualByComparingTo("1000000");
        verifyNoInteractions(legacyInvoiceRepository);
    }

    @Test
    void compatibilityRouteDoesNotCreateInvoiceBeforeCanonicalCheckoutFinalization() {
        Reservation reservation = new Reservation();
        User customer = new User();
        customer.setId(8L);
        reservation.setUser(customer);
        when(reservationRepository.findById(42L)).thenReturn(Optional.of(reservation));
        when(propertyAccessService.currentUser()).thenReturn(customer);
        when(invoiceRepository.findByReservationIdAndStatus(42L, PropertyInvoice.Status.FINALIZED))
                .thenReturn(Optional.empty());
        InvoiceController controller = new InvoiceController(
                legacyInvoiceRepository, invoiceRepository, reservationRepository, propertyAccessService);

        var response = controller.generateInvoice(42L);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNull();
        verifyNoInteractions(legacyInvoiceRepository);
    }

    private PropertyInvoice finalizedInvoice() {
        Hotel hotel = new Hotel();
        hotel.setId(3L);
        Reservation reservation = new Reservation();
        reservation.setId(42L);
        reservation.setHotel(hotel);
        User customer = new User();
        customer.setId(8L);
        reservation.setUser(customer);
        User staff = new User();
        staff.setId(9L);
        PropertyInvoice invoice = PropertyInvoice.finalized(
                hotel,
                reservation,
                "INV-3-42",
                "{}",
                "{}",
                VndMoney.of(1_000_000),
                VndMoney.zero(),
                VndMoney.zero(),
                VndMoney.zero(),
                VndMoney.of(1_000_000),
                VndMoney.of(1_000_000),
                VndMoney.zero(),
                VndMoney.zero(),
                staff,
                LocalDateTime.of(2026, 8, 1, 10, 0));
        ReflectionTestUtils.setField(invoice, "id", 88L);
        return invoice;
    }
}
