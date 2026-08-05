package com.hotel.entities;

import com.hotel.services.OperationalPolicyService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReservationOperationalPolicySnapshotTest {

    @Test
    void capturesOneImmutableOperationalPolicySnapshot() {
        Reservation reservation = new Reservation();
        OperationalPolicyService.PolicySnapshot snapshot = new OperationalPolicyService.PolicySnapshot(
                10L, 4L, LocalDateTime.of(2026, 8, 10, 0, 0), "{\"version\":4}");

        reservation.captureOperationalPolicy(snapshot);

        assertEquals(4L, reservation.getOperationalPolicyVersion());
        assertEquals("{\"version\":4}", reservation.getOperationalPolicySnapshot());
        assertThrows(IllegalStateException.class, () -> reservation.captureOperationalPolicy(snapshot));
    }
}
