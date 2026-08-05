package com.hotel.propertycommerce.booking;

import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Set;

@Component
public class ReservationAmendmentPolicy {

    public static final String VERSION = "RESERVATION_CHANGE_POLICY_V1";
    private static final Set<String> EDITABLE_STATUSES = Set.of("PENDING_PAYMENT", "CONFIRMED");

    private final Clock clock;
    private final long cutoffMinutes;
    private final long quoteTtlMinutes;
    private final LocalTime defaultArrivalTime;
    private final ZoneId propertyZone;

    @Autowired
    public ReservationAmendmentPolicy(
            @Value("${app.reservation-amendment.cutoff-minutes:1440}") long cutoffMinutes,
            @Value("${app.reservation-amendment.quote-ttl-minutes:15}") long quoteTtlMinutes,
            @Value("${app.reservation-amendment.default-arrival-time:14:00}") String defaultArrivalTime,
            @Value("${app.reservation-amendment.property-zone-id:Asia/Ho_Chi_Minh}") String propertyZoneId) {
        this(
                Clock.systemUTC(),
                cutoffMinutes,
                quoteTtlMinutes,
                parseArrivalTime(defaultArrivalTime, null),
                ZoneId.of(propertyZoneId));
    }

    ReservationAmendmentPolicy(Clock clock, long cutoffMinutes, long quoteTtlMinutes) {
        this(clock, cutoffMinutes, quoteTtlMinutes, LocalTime.of(14, 0), ZoneOffset.UTC);
    }

    ReservationAmendmentPolicy(
            Clock clock,
            long cutoffMinutes,
            long quoteTtlMinutes,
            LocalTime defaultArrivalTime) {
        this(clock, cutoffMinutes, quoteTtlMinutes, defaultArrivalTime, ZoneOffset.UTC);
    }

    ReservationAmendmentPolicy(
            Clock clock,
            long cutoffMinutes,
            long quoteTtlMinutes,
            LocalTime defaultArrivalTime,
            ZoneId propertyZone) {
        if (cutoffMinutes < 1 || quoteTtlMinutes < 1) {
            throw new IllegalArgumentException("Reservation amendment timing values must be at least one minute.");
        }
        this.clock = clock;
        this.cutoffMinutes = cutoffMinutes;
        this.quoteTtlMinutes = quoteTtlMinutes;
        this.defaultArrivalTime = java.util.Objects.requireNonNull(
                defaultArrivalTime, "defaultArrivalTime must not be null");
        this.propertyZone = java.util.Objects.requireNonNull(propertyZone, "propertyZone must not be null");
    }

    public void requireEditable(String status, LocalDate checkInDate) {
        requireEditable(status, checkInDate, null);
    }

    public void requireEditable(String status, LocalDate checkInDate, String propertyArrivalTime) {
        if (status == null || !EDITABLE_STATUSES.contains(status.trim().toUpperCase())) {
            throw new FinancialException(
                    FinancialErrorCode.INVALID_STATE_TRANSITION,
                    "Only pending-payment or confirmed reservations can be changed.");
        }
        if (checkInDate == null || now().isAfter(cutoffAt(checkInDate, propertyArrivalTime))) {
            throw new FinancialException(
                    FinancialErrorCode.INVALID_STATE_TRANSITION,
                    "The reservation change cutoff has passed.");
        }
    }

    public LocalDateTime cutoffAt(LocalDate checkInDate) {
        return cutoffAt(checkInDate, null);
    }

    public LocalDateTime cutoffAt(LocalDate checkInDate, String propertyArrivalTime) {
        if (checkInDate == null) {
            throw new IllegalArgumentException("checkInDate is required.");
        }
        return checkInDate.atTime(parseArrivalTime(propertyArrivalTime, defaultArrivalTime))
                .minusMinutes(cutoffMinutes)
                .atZone(propertyZone)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }

    public LocalDateTime quoteExpiresAt() {
        return now().plusMinutes(quoteTtlMinutes);
    }

    public LocalDateTime now() {
        Instant instant = clock.instant();
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static LocalTime parseArrivalTime(String value, LocalTime fallback) {
        if (value == null || value.isBlank()) {
            if (fallback == null) {
                throw new IllegalArgumentException("Default reservation arrival time is required.");
            }
            return fallback;
        }
        String normalized = value.trim();
        try {
            return LocalTime.parse(normalized.length() == 5 ? normalized : normalized.substring(0, 5));
        } catch (DateTimeParseException | IndexOutOfBoundsException exception) {
            if (fallback != null) {
                return fallback;
            }
            throw new IllegalArgumentException("Default reservation arrival time must use HH:mm.", exception);
        }
    }
}
