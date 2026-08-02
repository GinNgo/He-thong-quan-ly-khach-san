package com.hotel.propertycommerce.checkout;

import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.propertycommerce.booking.BookingFinancialSummaryService;
import com.hotel.propertycommerce.folio.ReservationChargeLine;
import com.hotel.propertycommerce.folio.ReservationChargeService;
import com.hotel.propertycommerce.folio.SurchargeService;
import com.hotel.services.ReservationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PropertyCheckoutServiceChargeControllerTest {

    @Test
    void suppliesServerTimeWhenClientOmitsUsageTimestamp() {
        ReservationChargeService chargeService = mock(ReservationChargeService.class);
        PropertyCheckoutController controller = new PropertyCheckoutController(
                chargeService,
                mock(SurchargeService.class),
                mock(CheckoutPreviewService.class),
                mock(CheckoutOverrideService.class),
                mock(ReservationService.class),
                mock(BookingFinancialSummaryService.class));
        when(chargeService.addServiceCharge(org.mockito.ArgumentMatchers.any()))
                .thenReturn(chargeLine());

        LocalDateTime before = LocalDateTime.now(java.time.ZoneOffset.UTC).minusSeconds(1);
        controller.addService(
                42L,
                "corr-42",
                new PropertyCheckoutController.ServiceChargeRequest(
                        7L,
                        "SERVICE",
                        BigDecimal.ONE,
                        null));

        ArgumentCaptor<ReservationChargeService.AddServiceChargeCommand> command =
                ArgumentCaptor.forClass(ReservationChargeService.AddServiceChargeCommand.class);
        verify(chargeService).addServiceCharge(command.capture());
        assertThat(command.getValue().reservationId()).isEqualTo(42L);
        assertThat(command.getValue().serviceUsedAt()).isAfter(before);
        assertThat(command.getValue().serviceUsedAt())
                .isBeforeOrEqualTo(LocalDateTime.now(java.time.ZoneOffset.UTC).plusSeconds(1));
    }

    private ReservationChargeLine chargeLine() {
        Hotel hotel = new Hotel();
        hotel.setId(3L);
        Reservation reservation = new Reservation();
        reservation.setId(42L);
        reservation.setHotel(hotel);
        ReservationChargeLine line = ReservationChargeLine.create(
                hotel,
                reservation,
                ReservationChargeLine.ChargeType.SERVICE,
                7L,
                "v1",
                "BREAKFAST",
                "Breakfast",
                null,
                BigDecimal.valueOf(100_000),
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(100_000),
                LocalDateTime.of(2026, 8, 3, 1, 0),
                null,
                null);
        ReflectionTestUtils.setField(line, "id", 71L);
        return line;
    }
}
