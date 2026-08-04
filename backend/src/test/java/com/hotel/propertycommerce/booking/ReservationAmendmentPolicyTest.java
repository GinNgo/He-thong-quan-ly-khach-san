package com.hotel.propertycommerce.booking;

import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReservationAmendmentPolicyTest {

    private static final Instant NOW = Instant.parse("2026-08-04T10:00:00Z");

    @Test
    void demoPolicyUsesShortConfigurableCutoffAndQuoteLifetime() {
        ReservationAmendmentPolicy policy = new ReservationAmendmentPolicy(
                Clock.fixed(NOW, ZoneOffset.UTC), 5, 2);

        assertEquals(LocalDateTime.of(2026, 8, 4, 10, 2), policy.quoteExpiresAt());
        assertEquals(LocalDateTime.of(2026, 8, 5, 13, 55),
                policy.cutoffAt(LocalDate.of(2026, 8, 5)));
    }

    @Test
    void onlyPendingPaymentAndConfirmedReservationsCanBeQuoted() {
        ReservationAmendmentPolicy policy = new ReservationAmendmentPolicy(
                Clock.fixed(NOW, ZoneOffset.UTC), 5, 2);

        policy.requireEditable("PENDING_PAYMENT", LocalDate.of(2026, 8, 5));
        policy.requireEditable("CONFIRMED", LocalDate.of(2026, 8, 5));

        FinancialException exception = assertThrows(FinancialException.class,
                () -> policy.requireEditable("CHECKED_IN", LocalDate.of(2026, 8, 5)));
        assertEquals(FinancialErrorCode.INVALID_STATE_TRANSITION, exception.code());
    }

    @Test
    void exactCutoffIsAllowedButOneSecondLaterIsRejected() {
        LocalDate checkIn = LocalDate.of(2026, 8, 5);
        ReservationAmendmentPolicy atBoundary = new ReservationAmendmentPolicy(
                Clock.fixed(Instant.parse("2026-08-05T13:55:00Z"), ZoneOffset.UTC), 5, 2);
        atBoundary.requireEditable("CONFIRMED", checkIn);

        ReservationAmendmentPolicy afterBoundary = new ReservationAmendmentPolicy(
                Clock.fixed(Instant.parse("2026-08-05T13:55:01Z"), ZoneOffset.UTC), 5, 2);
        FinancialException exception = assertThrows(FinancialException.class,
                () -> afterBoundary.requireEditable("CONFIRMED", checkIn));
        assertEquals(FinancialErrorCode.INVALID_STATE_TRANSITION, exception.code());
    }

    @Test
    void invalidTimingConfigurationFailsFast() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReservationAmendmentPolicy(Clock.systemUTC(), 0, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new ReservationAmendmentPolicy(Clock.systemUTC(), 5, 0));
    }

    @Test
    void propertyArrivalTimeOverridesTheConfiguredDefault() {
        ReservationAmendmentPolicy policy = new ReservationAmendmentPolicy(
                Clock.fixed(NOW, ZoneOffset.UTC), 5, 2);

        assertEquals(LocalDateTime.of(2026, 8, 5, 14, 55),
                policy.cutoffAt(LocalDate.of(2026, 8, 5), "15:00"));
        assertEquals(LocalDateTime.of(2026, 8, 5, 13, 55),
                policy.cutoffAt(LocalDate.of(2026, 8, 5), "invalid"));
    }

    @Test
    void propertyLocalArrivalCutoffIsComparedAsUtc() {
        ReservationAmendmentPolicy policy = new ReservationAmendmentPolicy(
                Clock.fixed(NOW, ZoneOffset.UTC),
                5,
                2,
                java.time.LocalTime.of(14, 0),
                ZoneId.of("Asia/Ho_Chi_Minh"));

        assertEquals(LocalDateTime.of(2026, 8, 5, 6, 55),
                policy.cutoffAt(LocalDate.of(2026, 8, 5)));
    }
}
