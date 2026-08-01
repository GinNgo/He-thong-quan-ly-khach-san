package com.hotel.propertycommerce.reporting;

import com.hotel.BackendApplication;
import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.reporting.RevenueReportModels.FinancialContext;
import com.hotel.paymentprovider.reporting.RevenueReportModels.NormalizedFilters;
import com.hotel.paymentprovider.reporting.RevenueReportModels.RecognitionBasis;
import com.hotel.propertycommerce.payment.PropertyFinancialTransaction;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@Import({PropertyRevenueRepository.class, PropertyRevenueService.class})
@ContextConfiguration(classes = BackendApplication.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:property-revenue-reconciliation;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class PropertyRevenueReconciliationIntegrationTest {

    @Autowired private EntityManager entityManager;
    @Autowired private PropertyRevenueService revenueService;

    @Test
    void reportMatchesAuthoritativeLedgerToOneVndAndExcludesAnotherProperty() {
        Hotel selected = persistHotel("selected");
        Hotel other = persistHotel("other");
        Reservation selectedReservation = persistReservation(selected, "selected");
        Reservation otherReservation = persistReservation(other, "other");

        PropertyFinancialTransaction debit = persistTransaction(
                "selected-debit", selected, selectedReservation, null,
                PropertyFinancialTransaction.TransactionType.ROOM_PAYMENT,
                PropertyFinancialTransaction.Direction.DEBIT, 1_000_000);
        persistTransaction(
                "selected-refund", selected, selectedReservation, debit,
                PropertyFinancialTransaction.TransactionType.REFUND,
                PropertyFinancialTransaction.Direction.CREDIT, 100_000);
        persistTransaction(
                "other-debit", other, otherReservation, null,
                PropertyFinancialTransaction.TransactionType.ROOM_PAYMENT,
                PropertyFinancialTransaction.Direction.DEBIT, 7_000_000);
        entityManager.flush();
        entityManager.clear();

        var report = revenueService.generate(filters(selected.getId()));
        Object[] databaseTotals = (Object[]) entityManager.createNativeQuery("""
                select
                  sum(case when direction = 'DEBIT' then amount else 0 end),
                  sum(case when direction = 'CREDIT' then amount else 0 end),
                  count(*)
                from property_financial_transactions
                where hotel_id = :hotelId
                  and occurred_at >= :fromInclusive
                  and occurred_at < :toExclusive
                """)
                .setParameter("hotelId", selected.getId())
                .setParameter("fromInclusive", LocalDateTime.of(2026, 7, 1, 0, 0))
                .setParameter("toExclusive", LocalDateTime.of(2026, 8, 1, 0, 0))
                .getSingleResult();

        BigDecimal databaseGross = new BigDecimal(databaseTotals[0].toString()).setScale(0);
        BigDecimal databaseRefunds = new BigDecimal(databaseTotals[1].toString()).setScale(0);
        assertEquals(databaseGross, report.totals().grossRevenue());
        assertEquals(databaseRefunds, report.totals().refunds());
        assertEquals(databaseGross.subtract(databaseRefunds), report.totals().netRevenue());
        assertEquals(((Number) databaseTotals[2]).longValue(), report.totalRowCount());
        assertEquals(0, report.totals().unreconciledTransactionCount());
    }

    private NormalizedFilters filters(Long propertyId) {
        return new NormalizedFilters(
                FinancialContext.PROPERTY_COMMERCE, RecognitionBasis.CASH_COLLECTED,
                Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-08-01T00:00:00Z"),
                "UTC", propertyId, null, null, null, null, null);
    }

    private Hotel persistHotel(String prefix) {
        Hotel hotel = new Hotel();
        hotel.setName(prefix + '-' + UUID.randomUUID());
        hotel.setAddressLine("Address");
        hotel.setCity("City");
        hotel.setCountry("VN");
        hotel.setStatus("ACTIVE");
        hotel.setOperationStatus("ACTIVE");
        hotel.setApprovalStatus("APPROVED");
        entityManager.persist(hotel);
        return hotel;
    }

    private Reservation persistReservation(Hotel hotel, String prefix) {
        User customer = new User();
        customer.setUsername(prefix + '-' + UUID.randomUUID());
        customer.setEmail(prefix + '-' + UUID.randomUUID() + "@example.com");
        customer.setPasswordHash("hash");
        customer.setStatus("ACTIVE");
        entityManager.persist(customer);

        Reservation reservation = new Reservation();
        reservation.setUser(customer);
        reservation.setHotel(hotel);
        reservation.setCheckInDate(LocalDate.of(2026, 7, 10));
        reservation.setCheckOutDate(LocalDate.of(2026, 7, 12));
        reservation.setGuests(2);
        reservation.setTotalAmount(BigDecimal.valueOf(1_000_000));
        reservation.setStatus("CONFIRMED");
        reservation.setPaymentMethod("BANK_QR");
        entityManager.persist(reservation);
        return reservation;
    }

    private PropertyFinancialTransaction persistTransaction(
            String publicId,
            Hotel hotel,
            Reservation reservation,
            PropertyFinancialTransaction original,
            PropertyFinancialTransaction.TransactionType type,
            PropertyFinancialTransaction.Direction direction,
            long amount) {
        PropertyFinancialTransaction transaction = PropertyFinancialTransaction.record(
                publicId, hotel, reservation, null, null, original, type, direction,
                VndMoney.of(amount), "BANK_QR", "MOMO", null, "provider-" + publicId,
                "effect-" + publicId, "SYSTEM", null, "Reconciliation fixture",
                LocalDateTime.of(2026, 7, direction == PropertyFinancialTransaction.Direction.DEBIT ? 15 : 20, 0, 0));
        entityManager.persist(transaction);
        return transaction;
    }
}
