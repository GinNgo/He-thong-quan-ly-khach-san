package com.hotel.propertycommerce.folio;

import com.hotel.domain.lifecycle.PaymentStatus;
import com.hotel.entities.Hotel;
import com.hotel.entities.HotelService;
import com.hotel.entities.Payment;
import com.hotel.entities.Reservation;
import com.hotel.entities.ReservationDetail;
import com.hotel.entities.ReservationServiceItem;
import com.hotel.entities.User;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.propertycommerce.checkout.FolioCalculationService;
import com.hotel.propertycommerce.payment.PropertyFinancialTransaction;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FolioCalculationServiceTest {

    @Test
    void reconcilesRoomChargesCorrectionsDiscountsPaymentsAndRefundsToOneVnd() {
        Fixture fixture = fixture();
        ReservationDetail room = roomDetail(fixture, 1_000_000, 11L);
        ReservationChargeLine original = serviceLine(fixture, 300_000, 21L, null);
        ReservationChargeLine reversal = serviceLine(fixture, 300_000, 22L, original);
        ReservationChargeLine replacement = serviceLine(fixture, 150_000, 23L, null);
        ReservationChargeLine surcharge = line(fixture, ReservationChargeLine.ChargeType.SURCHARGE,
                250_000, 250_000, 0, 0, 24L, null);
        ReservationChargeLine fee = line(fixture, ReservationChargeLine.ChargeType.FEE,
                50_000, 50_000, 0, 0, 25L, null);
        ReservationChargeLine tax = line(fixture, ReservationChargeLine.ChargeType.TAX,
                30_000, 30_000, 0, 0, 26L, null);
        ReservationChargeLine discount = line(fixture, ReservationChargeLine.ChargeType.DISCOUNT,
                0, 100_000, 0, 100_000, 27L, null);
        List<PropertyFinancialTransaction> transactions = List.of(
                transaction(fixture, 500_000, PropertyFinancialTransaction.Direction.DEBIT,
                        PropertyFinancialTransaction.TransactionType.BOOKING_DEPOSIT, 31L, null),
                transaction(fixture, 1_000_000, PropertyFinancialTransaction.Direction.DEBIT,
                        PropertyFinancialTransaction.TransactionType.ROOM_PAYMENT, 32L, null),
                transaction(fixture, 100_000, PropertyFinancialTransaction.Direction.CREDIT,
                        PropertyFinancialTransaction.TransactionType.REFUND, 33L,
                        transaction(fixture, 500_000, PropertyFinancialTransaction.Direction.DEBIT,
                                PropertyFinancialTransaction.TransactionType.BOOKING_DEPOSIT, 31L, null)));

        FolioCalculationService.Folio folio = calculator().calculateFromEvidence(
                fixture.reservation(),
                List.of(room),
                List.of(),
                List.of(original, reversal, replacement, surcharge, fee, tax, discount),
                transactions,
                List.of());

        assertThat(folio.roomCharges().amount()).isEqualByComparingTo("1000000");
        assertThat(folio.serviceCharges().amount()).isEqualByComparingTo("150000");
        assertThat(folio.surchargeCharges().amount()).isEqualByComparingTo("250000");
        assertThat(folio.taxCharges().amount()).isEqualByComparingTo("30000");
        assertThat(folio.feeCharges().amount()).isEqualByComparingTo("50000");
        assertThat(folio.discounts().amount()).isEqualByComparingTo("100000");
        assertThat(folio.grossCharges().amount()).isEqualByComparingTo("1380000");
        assertThat(folio.successfulPayments().amount()).isEqualByComparingTo("1500000");
        assertThat(folio.successfulRefunds().amount()).isEqualByComparingTo("100000");
        assertThat(folio.netSettled().amount()).isEqualByComparingTo("1400000");
        assertThat(folio.balance()).isEqualByComparingTo("-20000");
        assertThat(folio.sourceVersion()).isEqualTo(33L);
    }

    @Test
    void countsDepositLedgerOnceAndIgnoresLegacyPaymentsWhenAuthoritativeLedgerExists() {
        Fixture fixture = fixture();
        ReservationDetail room = roomDetail(fixture, 1_000_000, 11L);
        PropertyFinancialTransaction deposit = transaction(
                fixture, 300_000, PropertyFinancialTransaction.Direction.DEBIT,
                PropertyFinancialTransaction.TransactionType.BOOKING_DEPOSIT, 31L, null);
        Payment duplicateLegacy = legacyPayment(fixture, 300_000, 41L);

        FolioCalculationService.Folio folio = calculator().calculateFromEvidence(
                fixture.reservation(), List.of(room), List.of(), List.of(),
                List.of(deposit), List.of(duplicateLegacy));

        assertThat(folio.successfulPayments().amount()).isEqualByComparingTo("300000");
        assertThat(folio.balance()).isEqualByComparingTo("700000");
    }

    @Test
    void fallsBackToActiveLegacyServicesAndSuccessfulLegacyPayments() {
        Fixture fixture = fixture();
        ReflectionTestUtils.setField(fixture.reservation(), "depositBookingTotal", null);
        ReservationDetail room = roomDetail(fixture, 1_000_000, 11L);
        ReservationServiceItem legacyService = legacyService(fixture, 75_000, 2, 51L);
        Payment charge = legacyPayment(fixture, 1_200_000, 61L);
        Payment refund = legacyPayment(fixture, -50_000, 62L);
        Payment pending = legacyPayment(fixture, 500_000, 63L);
        pending.setStatus(PaymentStatus.PENDING.name());
        Payment cancelled = legacyPayment(fixture, 500_000, 64L);
        cancelled.setStatus("CANCELLED");

        FolioCalculationService.Folio folio = calculator().calculateFromEvidence(
                fixture.reservation(), List.of(room), List.of(legacyService), List.of(),
                List.of(), List.of(charge, refund, pending, cancelled));

        assertThat(folio.serviceCharges().amount()).isEqualByComparingTo("150000");
        assertThat(folio.grossCharges().amount()).isEqualByComparingTo("1150000");
        assertThat(folio.successfulPayments().amount()).isEqualByComparingTo("1200000");
        assertThat(folio.successfulRefunds().amount()).isEqualByComparingTo("50000");
        assertThat(folio.balance()).isEqualByComparingTo("0");
    }

    @Test
    void rejectsDuplicateReversalsAndCrossReservationEvidence() {
        Fixture fixture = fixture();
        ReservationDetail room = roomDetail(fixture, 1_000_000, 11L);
        ReservationChargeLine original = serviceLine(fixture, 300_000, 21L, null);
        ReservationChargeLine first = serviceLine(fixture, 300_000, 22L, original);
        ReservationChargeLine second = serviceLine(fixture, 300_000, 23L, original);

        assertThatThrownBy(() -> calculator().calculateFromEvidence(
                fixture.reservation(), List.of(room), List.of(),
                List.of(original, first, second), List.of(), List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("more than once");

        Fixture other = fixture(4L, 99L);
        ReservationChargeLine foreign = serviceLine(other, 100_000, 24L, null);
        assertThatThrownBy(() -> calculator().calculateFromEvidence(
                fixture.reservation(), List.of(room), List.of(),
                List.of(foreign), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("another property");
    }

    @Test
    void failsClosedWhenRoomSnapshotOrChargeEquationDoesNotReconcile() {
        Fixture fixture = fixture();
        ReservationDetail mismatched = roomDetail(fixture, 900_000, 11L);
        assertThatThrownBy(() -> calculator().calculateFromEvidence(
                fixture.reservation(), List.of(mismatched), List.of(), List.of(), List.of(), List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("immutable booking snapshot");

        ReservationDetail room = roomDetail(fixture, 1_000_000, 11L);
        ReservationChargeLine invalid = line(fixture, ReservationChargeLine.ChargeType.SURCHARGE,
                100_000, 90_000, 0, 0, 21L, null);
        assertThatThrownBy(() -> calculator().calculateFromEvidence(
                fixture.reservation(), List.of(room), List.of(), List.of(invalid), List.of(), List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not reconcile");

        Payment ambiguousRefund = legacyPayment(fixture, 100_000, 31L);
        ambiguousRefund.setStatus("REFUNDED");
        assertThatThrownBy(() -> calculator().calculateFromEvidence(
                fixture.reservation(), List.of(room), List.of(), List.of(), List.of(), List.of(ambiguousRefund)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reconciliation evidence");
    }

    private FolioCalculationService calculator() {
        return new FolioCalculationService(null, null, null, null, null, null, null);
    }

    private ReservationDetail roomDetail(Fixture fixture, long subtotal, Long id) {
        ReservationDetail detail = new ReservationDetail();
        detail.setReservation(fixture.reservation());
        detail.setQuantity(1);
        detail.setPrice(BigDecimal.valueOf(subtotal));
        detail.setUnitPrice(BigDecimal.valueOf(subtotal));
        detail.setSubtotal(BigDecimal.valueOf(subtotal));
        ReflectionTestUtils.setField(detail, "id", id);
        return detail;
    }

    private ReservationChargeLine serviceLine(
            Fixture fixture,
            long total,
            Long id,
            ReservationChargeLine reverses) {
        ReservationChargeLine.ChargeType type = reverses == null
                ? ReservationChargeLine.ChargeType.SERVICE
                : ReservationChargeLine.ChargeType.ADJUSTMENT;
        return line(fixture, type, total, total, 0, 0, id, reverses);
    }

    private ReservationChargeLine line(
            Fixture fixture,
            ReservationChargeLine.ChargeType type,
            long unitPrice,
            long total,
            long tax,
            long discount,
            Long id,
            ReservationChargeLine reverses) {
        ReservationChargeLine line = ReservationChargeLine.create(
                fixture.hotel(),
                fixture.reservation(),
                type,
                type == ReservationChargeLine.ChargeType.SERVICE ? 15L : null,
                "v1",
                type.name(),
                type.name(),
                "Test line",
                BigDecimal.valueOf(unitPrice),
                BigDecimal.ONE,
                BigDecimal.valueOf(tax),
                BigDecimal.valueOf(discount),
                BigDecimal.valueOf(total),
                type == ReservationChargeLine.ChargeType.SERVICE ? LocalDateTime.of(2026, 8, 1, 3, 0) : null,
                fixture.actor(),
                reverses);
        ReflectionTestUtils.setField(line, "id", id);
        return line;
    }

    private PropertyFinancialTransaction transaction(
            Fixture fixture,
            long amount,
            PropertyFinancialTransaction.Direction direction,
            PropertyFinancialTransaction.TransactionType type,
            Long id,
            PropertyFinancialTransaction original) {
        PropertyFinancialTransaction transaction = PropertyFinancialTransaction.record(
                "tx-" + id,
                fixture.hotel(),
                fixture.reservation(),
                null,
                null,
                original,
                type,
                direction,
                VndMoney.of(amount),
                "CASH",
                "INTERNAL",
                PaymentEnvironment.SIMULATOR,
                "provider-" + id,
                "effect-" + id,
                "USER",
                fixture.actor().getId(),
                "Test transaction",
                LocalDateTime.of(2026, 8, 1, 3, 0));
        ReflectionTestUtils.setField(transaction, "id", id);
        return transaction;
    }

    private ReservationServiceItem legacyService(Fixture fixture, long price, int quantity, Long id) {
        HotelService service = new HotelService();
        service.setId(15L);
        service.setCode("LEGACY-SVC");
        service.setNameVi("Legacy service");
        ReservationServiceItem item = new ReservationServiceItem();
        item.setReservation(fixture.reservation());
        item.setHotelService(service);
        item.setPrice(BigDecimal.valueOf(price));
        item.setQuantity(quantity);
        item.setTotalAmount(BigDecimal.valueOf(price * quantity));
        item.setStatus("ACTIVE");
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private Payment legacyPayment(Fixture fixture, long amount, Long id) {
        Payment payment = new Payment();
        payment.setReservation(fixture.reservation());
        payment.setAmount(BigDecimal.valueOf(amount));
        payment.setPaymentMethod("CASH");
        payment.setStatus(PaymentStatus.SUCCEEDED.name());
        payment.setTransactionId("legacy-" + id);
        ReflectionTestUtils.setField(payment, "id", id);
        return payment;
    }

    private Fixture fixture() {
        return fixture(3L, 42L);
    }

    private Fixture fixture(Long hotelId, Long reservationId) {
        Hotel hotel = new Hotel();
        hotel.setId(hotelId);
        User actor = new User();
        actor.setId(9L);
        Reservation reservation = new Reservation();
        reservation.setId(reservationId);
        reservation.setHotel(hotel);
        reservation.setUser(actor);
        reservation.setCheckInDate(LocalDate.of(2026, 8, 1));
        reservation.setCheckOutDate(LocalDate.of(2026, 8, 2));
        reservation.setTotalAmount(BigDecimal.valueOf(1_000_000));
        ReflectionTestUtils.setField(reservation, "depositBookingTotal", BigDecimal.valueOf(1_000_000));
        ReflectionTestUtils.setField(reservation, "depositRequired", BigDecimal.valueOf(300_000));
        return new Fixture(hotel, reservation, actor);
    }

    private record Fixture(Hotel hotel, Reservation reservation, User actor) {
    }
}
