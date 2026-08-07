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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PropertyCheckoutServiceChargeControllerTest {

    @Test
    void preservesAnOmittedUsageTimestampForStableIdempotencyHashing() {
        ReservationChargeService chargeService = mock(ReservationChargeService.class);
        PropertyCheckoutController controller = new PropertyCheckoutController(
                chargeService,
                mock(SurchargeService.class),
                mock(CheckoutPreviewService.class),
                mock(CheckoutOverrideService.class),
                mock(ReservationService.class),
                mock(BookingFinancialSummaryService.class));
        when(chargeService.addServiceCharge(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ReservationChargeService.AddServiceChargeResult(chargeLine(), false));

        controller.addService(
                42L,
                "service-charge-42",
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
        assertThat(command.getValue().idempotencyKey()).isEqualTo("service-charge-42");
        assertThat(command.getValue().correlationId()).isEqualTo("corr-42");
        assertThat(command.getValue().serviceUsedAt()).isNull();
    }

    @Test
    void exposesReplayStateFromThePersistedChargeResult() {
        ReservationChargeService chargeService = mock(ReservationChargeService.class);
        PropertyCheckoutController controller = new PropertyCheckoutController(
                chargeService,
                mock(SurchargeService.class),
                mock(CheckoutPreviewService.class),
                mock(CheckoutOverrideService.class),
                mock(ReservationService.class),
                mock(BookingFinancialSummaryService.class));
        when(chargeService.addServiceCharge(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ReservationChargeService.AddServiceChargeResult(chargeLine(), true));

        var response = controller.addService(
                42L,
                "service-charge-42",
                "corr-replay",
                new PropertyCheckoutController.ServiceChargeRequest(
                        7L,
                        "MINIBAR",
                        BigDecimal.ONE,
                        LocalDateTime.of(2026, 8, 3, 1, 0)));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().replayed()).isTrue();
        assertThat(response.getBody().correlationId()).isEqualTo("corr-replay");
    }

    @Test
    void httpContractRequiresIdempotencyKeyAndReturnsReplayMetadata() throws Exception {
        ReservationChargeService chargeService = mock(ReservationChargeService.class);
        PropertyCheckoutController controller = new PropertyCheckoutController(
                chargeService,
                mock(SurchargeService.class),
                mock(CheckoutPreviewService.class),
                mock(CheckoutOverrideService.class),
                mock(ReservationService.class),
                mock(BookingFinancialSummaryService.class));
        when(chargeService.addServiceCharge(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ReservationChargeService.AddServiceChargeResult(chargeLine(), true));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(post("/api/management/reservations/42/charges/services")
                        .header("Idempotency-Key", "service-http-42")
                        .header("X-Correlation-ID", "corr-http-42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\":7,\"chargeType\":\"MINIBAR\",\"quantity\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.chargeType").value("SERVICE"))
                .andExpect(jsonPath("$.replayed").value(true));

        mvc.perform(post("/api/management/reservations/42/charges/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\":7,\"chargeType\":\"MINIBAR\",\"quantity\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void surchargeControllerRequiresIdempotencyAndAcceptsLocalizedType() {
        ReservationChargeService chargeService = mock(ReservationChargeService.class);
        SurchargeService surchargeService = mock(SurchargeService.class);
        PropertyCheckoutController controller = new PropertyCheckoutController(
                chargeService,
                surchargeService,
                mock(CheckoutPreviewService.class),
                mock(CheckoutOverrideService.class),
                mock(ReservationService.class),
                mock(BookingFinancialSummaryService.class));
        when(surchargeService.addSurcharge(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new SurchargeService.AddSurchargeResult(chargeLine(), false));

        var response = controller.addSurcharge(
                42L,
                "surcharge-42",
                "corr-surcharge-42",
                new PropertyCheckoutController.SurchargeRequest(
                        "trả phòng muộn", "Đã duyệt trả phòng đến 16:00", BigDecimal.valueOf(250_000),
                        null, false, null));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().replayed()).isFalse();
        ArgumentCaptor<SurchargeService.AddSurchargeCommand> command =
                ArgumentCaptor.forClass(SurchargeService.AddSurchargeCommand.class);
        verify(surchargeService).addSurcharge(command.capture());
        assertThat(command.getValue().type()).isEqualTo(SurchargeService.SurchargeType.LATE_CHECK_OUT);
        assertThat(command.getValue().idempotencyKey()).isEqualTo("surcharge-42");
    }

    @Test
    void negativeAdjustmentControllerUsesSeparateTypedPermissionPathAndHistoryEndpoint() {
        ReservationChargeService chargeService = mock(ReservationChargeService.class);
        SurchargeService surchargeService = mock(SurchargeService.class);
        PropertyCheckoutController controller = new PropertyCheckoutController(
                chargeService,
                surchargeService,
                mock(CheckoutPreviewService.class),
                mock(CheckoutOverrideService.class),
                mock(ReservationService.class),
                mock(BookingFinancialSummaryService.class));
        when(surchargeService.addNegativeAdjustment(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new SurchargeService.AddSurchargeResult(chargeLine(), true));
        when(surchargeService.adjustmentHistory(42L)).thenReturn(List.of(
                new SurchargeService.AdjustmentHistoryEntry(
                        71L, 42L, "DISCOUNT", "SERVICE_RECOVERY", "Adjustment", "Approved",
                        BigDecimal.valueOf(100_000), LocalDateTime.of(2026, 8, 3, 1, 0), 9L, true)));

        var response = controller.addSurcharge(
                42L,
                "adjustment-42",
                "corr-adjustment-42",
                new PropertyCheckoutController.SurchargeRequest(
                        "hỗ trợ thiện chí", "Đã duyệt bồi hoàn dịch vụ", BigDecimal.valueOf(100_000),
                        null, true, "bồi hoàn dịch vụ"));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().replayed()).isTrue();
        ArgumentCaptor<SurchargeService.AddNegativeAdjustmentCommand> command =
                ArgumentCaptor.forClass(SurchargeService.AddNegativeAdjustmentCommand.class);
        verify(surchargeService).addNegativeAdjustment(command.capture());
        assertThat(command.getValue().type()).isEqualTo(SurchargeService.NegativeAdjustmentType.SERVICE_RECOVERY);
        assertThat(controller.adjustmentHistory(42L)).hasSize(1);
        assertThat(controller.adjustmentHistory(42L).get(0).approved()).isTrue();
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
