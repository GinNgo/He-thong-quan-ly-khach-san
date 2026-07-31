package com.hotel.propertycommerce.booking;

import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.propertycommerce.config.PropertyPaymentConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DepositPolicySnapshotTest {

    @Test
    void capturesPercentagePolicyFromThePersistedPropertyConfiguration() {
        PropertyPaymentConfiguration configuration = configuration("PERCENTAGE", BigDecimal.valueOf(30));

        DepositPolicySnapshot snapshot = DepositPolicySnapshot.capture(
                configuration,
                BigDecimal.valueOf(1_200_000));

        assertEquals(7L, snapshot.propertyId());
        assertEquals(11L, snapshot.configurationId());
        assertEquals(4L, snapshot.configurationVersion());
        assertEquals(DepositPolicySnapshot.PolicyType.PERCENTAGE, snapshot.policyType());
        assertEquals(0, BigDecimal.valueOf(30).compareTo(snapshot.policyValue()));
        assertEquals(0, BigDecimal.valueOf(1_200_000).compareTo(snapshot.bookingTotal().amount()));
        assertEquals(0, BigDecimal.valueOf(360_000).compareTo(snapshot.requiredDeposit().amount()));
        assertEquals("VND", snapshot.currency());
    }

    @Test
    void capsFixedDepositAtTheAuthoritativeBookingTotal() {
        DepositPolicySnapshot snapshot = DepositPolicySnapshot.capture(
                configuration("FIXED", BigDecimal.valueOf(500_000)),
                BigDecimal.valueOf(350_000));

        assertEquals(0, BigDecimal.valueOf(350_000).compareTo(snapshot.requiredDeposit().amount()));
    }

    @Test
    void normalizesNoDepositPolicyToZeroVnd() {
        DepositPolicySnapshot snapshot = DepositPolicySnapshot.capture(
                configuration("NONE", null),
                BigDecimal.valueOf(800_000));

        assertEquals(DepositPolicySnapshot.PolicyType.NONE, snapshot.policyType());
        assertEquals(0, BigDecimal.ZERO.compareTo(snapshot.policyValue()));
        assertEquals(0, BigDecimal.ZERO.compareTo(snapshot.requiredDeposit().amount()));
    }

    @Test
    void rejectsPercentageThatWouldCreateFractionalVnd() {
        FinancialException exception = assertThrows(FinancialException.class, () ->
                DepositPolicySnapshot.capture(
                        configuration("PERCENTAGE", BigDecimal.valueOf(30)),
                        BigDecimal.valueOf(333_333)));

        assertEquals(FinancialErrorCode.INVALID_AMOUNT, exception.code());
        assertTrue(exception.fieldErrors().containsKey("bookingTotal"));
    }

    @Test
    void rejectsMissingAuthoritativeBookingTotal() {
        FinancialException exception = assertThrows(FinancialException.class, () ->
                DepositPolicySnapshot.capture(configuration("NONE", null), null));

        assertEquals(FinancialErrorCode.INVALID_AMOUNT, exception.code());
        assertTrue(exception.fieldErrors().containsKey("bookingTotal"));
    }

    @Test
    void rejectsARehydratedSnapshotWithATamperedDepositAmount() {
        FinancialException exception = assertThrows(FinancialException.class, () ->
                new DepositPolicySnapshot(
                        7L,
                        11L,
                        4L,
                        DepositPolicySnapshot.PolicyType.PERCENTAGE,
                        BigDecimal.valueOf(30),
                        com.hotel.paymentprovider.domain.VndMoney.of(1_000_000),
                        com.hotel.paymentprovider.domain.VndMoney.of(1)));

        assertEquals(FinancialErrorCode.INVALID_AMOUNT, exception.code());
    }

    @Test
    void remainsUnchangedWhenTheSourceConfigurationChanges() {
        PropertyPaymentConfiguration configuration = configuration("FIXED", BigDecimal.valueOf(200_000));
        DepositPolicySnapshot snapshot = DepositPolicySnapshot.capture(configuration, BigDecimal.valueOf(900_000));

        ReflectionTestUtils.setField(configuration, "depositPolicyType", "PERCENTAGE");
        ReflectionTestUtils.setField(configuration, "depositValue", BigDecimal.valueOf(50));

        assertNotEquals(configuration.getDepositPolicyType(), snapshot.policyType().name());
        assertEquals(0, BigDecimal.valueOf(200_000).compareTo(snapshot.requiredDeposit().amount()));
    }

    @Test
    void reservationRejectsReplacingAnAlreadyCapturedSnapshot() {
        Reservation reservation = new Reservation();
        Hotel hotel = new Hotel();
        hotel.setId(7L);
        reservation.setHotel(hotel);
        DepositPolicySnapshot snapshot = new DepositPolicySnapshot(
                7L, 11L, 4L, DepositPolicySnapshot.PolicyType.NONE, BigDecimal.ZERO,
                VndMoney.of(800_000), VndMoney.zero());

        reservation.captureDepositPolicy(snapshot);

        assertThrows(IllegalStateException.class, () -> reservation.captureDepositPolicy(snapshot));
    }

    @Test
    void reservationRejectsSnapshotFromAnotherProperty() {
        Reservation reservation = new Reservation();
        Hotel hotel = new Hotel();
        hotel.setId(8L);
        reservation.setHotel(hotel);
        DepositPolicySnapshot snapshot = new DepositPolicySnapshot(
                7L, 11L, 4L, DepositPolicySnapshot.PolicyType.NONE, BigDecimal.ZERO,
                VndMoney.of(800_000), VndMoney.zero());

        assertThrows(IllegalArgumentException.class, () -> reservation.captureDepositPolicy(snapshot));
    }

    private PropertyPaymentConfiguration configuration(String policyType, BigDecimal policyValue) {
        Hotel hotel = new Hotel();
        hotel.setId(7L);
        PropertyPaymentConfiguration configuration = new PropertyPaymentConfiguration(hotel);
        ReflectionTestUtils.setField(configuration, "id", 11L);
        ReflectionTestUtils.setField(configuration, "version", 4L);
        ReflectionTestUtils.setField(configuration, "depositPolicyType", policyType);
        ReflectionTestUtils.setField(configuration, "depositValue", policyValue);
        return configuration;
    }
}
