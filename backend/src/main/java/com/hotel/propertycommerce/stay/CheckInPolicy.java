package com.hotel.propertycommerce.stay;

import com.hotel.entities.Reservation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

@Component
public class CheckInPolicy {

    public static final String VERSION = "CHECK_IN_POLICY_V1";

    private final Clock clock;
    private final ZoneId propertyZone;
    private final LocalTime defaultArrivalTime;
    private final LocalTime defaultDepartureTime;
    private final long earlyWindowMinutes;
    private final long demoEarlyWindowMinutes;

    @Autowired
    public CheckInPolicy(
            @Value("${app.stay-check-in.property-zone-id:Asia/Ho_Chi_Minh}") String propertyZoneId,
            @Value("${app.stay-check-in.default-arrival-time:14:00}") String defaultArrivalTime,
            @Value("${app.stay-check-in.default-departure-time:12:00}") String defaultDepartureTime,
            @Value("${app.stay-check-in.early-window-minutes:0}") long earlyWindowMinutes,
            @Value("${app.stay-check-in.demo-early-window-minutes:5}") long demoEarlyWindowMinutes) {
        this(
                Clock.systemUTC(),
                ZoneId.of(propertyZoneId),
                parseTime(defaultArrivalTime, null, "arrival"),
                parseTime(defaultDepartureTime, null, "departure"),
                earlyWindowMinutes,
                demoEarlyWindowMinutes);
    }

    CheckInPolicy(
            Clock clock,
            ZoneId propertyZone,
            LocalTime defaultArrivalTime,
            LocalTime defaultDepartureTime,
            long earlyWindowMinutes,
            long demoEarlyWindowMinutes) {
        if (earlyWindowMinutes < 0 || demoEarlyWindowMinutes < 0) {
            throw new IllegalArgumentException("Check-in early windows cannot be negative.");
        }
        this.clock = java.util.Objects.requireNonNull(clock, "clock must not be null");
        this.propertyZone = java.util.Objects.requireNonNull(propertyZone, "propertyZone must not be null");
        this.defaultArrivalTime = java.util.Objects.requireNonNull(
                defaultArrivalTime, "defaultArrivalTime must not be null");
        this.defaultDepartureTime = java.util.Objects.requireNonNull(
                defaultDepartureTime, "defaultDepartureTime must not be null");
        this.earlyWindowMinutes = earlyWindowMinutes;
        this.demoEarlyWindowMinutes = demoEarlyWindowMinutes;
    }

    public Window window(Reservation reservation) {
        if (reservation == null || reservation.getHotel() == null
                || reservation.getCheckInDate() == null || reservation.getCheckOutDate() == null) {
            throw new IllegalStateException("Reservation stay dates and property are required for check-in.");
        }
        LocalTime arrival = parseTime(
                reservation.getHotel().getCheckinTime(), defaultArrivalTime, "arrival");
        LocalTime departure = parseTime(
                reservation.getHotel().getCheckoutTime(), defaultDepartureTime, "departure");
        long earlyMinutes = Boolean.TRUE.equals(reservation.getHotel().getIsDemo())
                ? demoEarlyWindowMinutes
                : earlyWindowMinutes;
        OffsetDateTime scheduledArrival = reservation.getCheckInDate()
                .atTime(arrival)
                .atZone(propertyZone)
                .toOffsetDateTime();
        OffsetDateTime latestCheckIn = reservation.getCheckOutDate()
                .atTime(departure)
                .atZone(propertyZone)
                .toOffsetDateTime();
        return new Window(
                OffsetDateTime.ofInstant(clock.instant(), propertyZone),
                scheduledArrival,
                scheduledArrival.minusMinutes(earlyMinutes),
                latestCheckIn,
                propertyZone.getId(),
                earlyMinutes,
                VERSION);
    }

    private static LocalTime parseTime(String value, LocalTime fallback, String label) {
        if (value == null || value.isBlank()) {
            if (fallback == null) throw new IllegalArgumentException("Default " + label + " time is required.");
            return fallback;
        }
        String normalized = value.trim();
        try {
            return LocalTime.parse(normalized);
        } catch (DateTimeParseException exception) {
            if (fallback != null) return fallback;
            throw new IllegalArgumentException("Default " + label + " time must use HH:mm.", exception);
        }
    }

    public record Window(
            OffsetDateTime evaluatedAt,
            OffsetDateTime scheduledArrivalAt,
            OffsetDateTime earliestCheckInAt,
            OffsetDateTime latestCheckInAt,
            String zoneId,
            long earlyWindowMinutes,
            String policyVersion) {
    }
}
