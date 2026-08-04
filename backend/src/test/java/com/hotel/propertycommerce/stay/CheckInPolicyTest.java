package com.hotel.propertycommerce.stay;

import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CheckInPolicyTest {

    private static final ZoneId PROPERTY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Test
    void demoPropertyUsesFiveMinuteEarlyWindowWhileNormalPropertyFailsClosed() {
        CheckInPolicy policy = policyAt("2026-08-04T06:56:00Z");
        Reservation demo = reservation(true, "14:00", "12:00");
        Reservation normal = reservation(false, "14:00", "12:00");

        assertEquals(5, policy.window(demo).earlyWindowMinutes());
        assertEquals("2026-08-04T13:55+07:00", policy.window(demo).earliestCheckInAt().toString());
        assertEquals(0, policy.window(normal).earlyWindowMinutes());
        assertEquals("2026-08-04T14:00+07:00", policy.window(normal).earliestCheckInAt().toString());
    }

    @Test
    void propertyTimesAndZoneProduceAnExplicitArrivalAndStayCloseWindow() {
        CheckInPolicy policy = policyAt("2026-08-04T06:56:00Z");
        CheckInPolicy.Window window = policy.window(reservation(true, "15:30:00", "11:30:00"));

        assertEquals("2026-08-04T15:30+07:00", window.scheduledArrivalAt().toString());
        assertEquals("2026-08-06T11:30+07:00", window.latestCheckInAt().toString());
        assertEquals("Asia/Ho_Chi_Minh", window.zoneId());
        assertEquals(CheckInPolicy.VERSION, window.policyVersion());
    }

    @Test
    void malformedPropertyTimeFallsBackButMalformedDefaultConfigurationFailsFast() {
        CheckInPolicy policy = policyAt("2026-08-04T06:56:00Z");
        assertEquals("2026-08-04T14:00+07:00",
                policy.window(reservation(false, "14:00garbage", "12:00")).scheduledArrivalAt().toString());

        assertThrows(IllegalArgumentException.class,
                () -> new CheckInPolicy("Asia/Ho_Chi_Minh", "14:00garbage", "12:00", 0, 5));
        assertThrows(IllegalArgumentException.class,
                () -> new CheckInPolicy("Asia/Ho_Chi_Minh", "14:00", "12:00", -1, 5));
    }

    private CheckInPolicy policyAt(String instant) {
        return new CheckInPolicy(
                Clock.fixed(Instant.parse(instant), PROPERTY_ZONE),
                PROPERTY_ZONE,
                LocalTime.of(14, 0),
                LocalTime.of(12, 0),
                0,
                5);
    }

    private Reservation reservation(boolean demo, String checkInTime, String checkOutTime) {
        Hotel hotel = new Hotel();
        hotel.setId(7L);
        hotel.setIsDemo(demo);
        hotel.setCheckinTime(checkInTime);
        hotel.setCheckoutTime(checkOutTime);
        Reservation reservation = new Reservation();
        reservation.setId(19L);
        reservation.setHotel(hotel);
        reservation.setCheckInDate(LocalDate.of(2026, 8, 4));
        reservation.setCheckOutDate(LocalDate.of(2026, 8, 6));
        return reservation;
    }
}
