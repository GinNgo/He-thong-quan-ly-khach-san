package com.hotel.propertycommerce.folio;

import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationChargeLineTest {

    @Test
    void capturesServerOwnedServiceSnapshots() {
        Fixture fixture = fixture(7L, 91L);
        LocalDateTime usedAt = LocalDateTime.of(2026, 8, 10, 14, 30);

        ReservationChargeLine line = ReservationChargeLine.create(
                fixture.hotel(),
                fixture.reservation(),
                ReservationChargeLine.ChargeType.SERVICE,
                15L,
                "menu-v3",
                "SPA-60",
                "Spa treatment",
                "Sixty minute treatment",
                BigDecimal.valueOf(200_000),
                BigDecimal.valueOf(2),
                BigDecimal.valueOf(20_000),
                BigDecimal.valueOf(10_000),
                BigDecimal.valueOf(410_000),
                usedAt,
                fixture.actor(),
                null);

        assertThat(line.getHotel()).isSameAs(fixture.hotel());
        assertThat(line.getReservation()).isSameAs(fixture.reservation());
        assertThat(line.getChargeType()).isEqualTo(ReservationChargeLine.ChargeType.SERVICE);
        assertThat(line.getSourceId()).isEqualTo(15L);
        assertThat(line.getSourceVersion()).isEqualTo("menu-v3");
        assertThat(line.getCode()).isEqualTo("SPA-60");
        assertThat(line.getQuantity()).isEqualByComparingTo("2.000");
        assertThat(line.totalMoney().amount()).isEqualByComparingTo("410000");
        assertThat(line.getServiceUsedAt()).isEqualTo(usedAt);
        assertThat(line.getActor()).isSameAs(fixture.actor());
    }

    @Test
    void rejectsCrossPropertyReservationOwnership() {
        Fixture fixture = fixture(7L, 91L);
        Hotel otherHotel = new Hotel();
        otherHotel.setId(8L);

        assertThatThrownBy(() -> line(otherHotel, fixture.reservation(), null, LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("charge-line property");
    }

    @Test
    void rejectsMissingServiceUsageAndInvalidMoneyOrQuantity() {
        Fixture fixture = fixture(7L, 91L);

        assertThatThrownBy(() -> line(fixture.hotel(), fixture.reservation(), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("usage timestamp");

        assertThatThrownBy(() -> ReservationChargeLine.create(
                fixture.hotel(), fixture.reservation(), ReservationChargeLine.ChargeType.SERVICE,
                15L, "v1", "SPA", "Spa", null,
                new BigDecimal("100.50"), BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(100), LocalDateTime.now(), fixture.actor(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("integer VND");

        assertThatThrownBy(() -> ReservationChargeLine.create(
                fixture.hotel(), fixture.reservation(), ReservationChargeLine.ChargeType.SERVICE,
                15L, "v1", "SPA", "Spa", null,
                BigDecimal.valueOf(100), new BigDecimal("1.0001"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(100), LocalDateTime.now(), fixture.actor(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("three decimal places");
    }

    @Test
    void requiresReversalsToStayInsideTheSameReservationAndRejectsUpdates() {
        Fixture originalFixture = fixture(7L, 91L);
        ReservationChargeLine original = line(
                originalFixture.hotel(), originalFixture.reservation(), null, LocalDateTime.now());
        Fixture otherReservation = fixture(7L, 92L);

        assertThatThrownBy(() -> ReservationChargeLine.create(
                otherReservation.hotel(), otherReservation.reservation(), ReservationChargeLine.ChargeType.ADJUSTMENT,
                null, null, "REV", "Correction", null,
                BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(100), null, otherReservation.actor(), original))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same reservation");

        assertThatThrownBy(original::rejectUpdate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("append-only");
    }

    private static ReservationChargeLine line(
            Hotel hotel,
            Reservation reservation,
            ReservationChargeLine reverses,
            LocalDateTime usedAt) {
        User actor = new User();
        actor.setId(31L);
        return ReservationChargeLine.create(
                hotel,
                reservation,
                ReservationChargeLine.ChargeType.SERVICE,
                15L,
                "v1",
                "SPA",
                "Spa",
                null,
                BigDecimal.valueOf(100),
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(100),
                usedAt,
                actor,
                reverses);
    }

    private static Fixture fixture(Long hotelId, Long reservationId) {
        Hotel hotel = new Hotel();
        hotel.setId(hotelId);
        Reservation reservation = new Reservation();
        reservation.setId(reservationId);
        reservation.setHotel(hotel);
        User actor = new User();
        actor.setId(31L);
        return new Fixture(hotel, reservation, actor);
    }

    private record Fixture(Hotel hotel, Reservation reservation, User actor) {
    }
}
