package com.hotel.propertycommerce.reporting;

import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.reporting.RevenueReportModels.FinancialContext;
import com.hotel.paymentprovider.reporting.RevenueReportModels.NormalizedFilters;
import com.hotel.paymentprovider.reporting.RevenueReportModels.RecognitionBasis;
import com.hotel.propertycommerce.payment.PropertyFinancialTransaction;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@Import(PropertyRevenueRepository.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:property-revenue-query;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class PropertyRevenueRepositoryTest {

    @Autowired
    private PropertyRevenueRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void executesEveryAuthoritativeSourceQueryForAnEmptyTenantScope() {
        PropertyRevenueRepository.PropertyRevenueSource source = repository.load(propertyFilters(null));

        assertEquals(0, source.transactions().size());
        assertEquals(0, source.invoices().size());
        assertEquals(0, source.invoiceLines().size());
        assertEquals(0, source.allocations().size());
        assertEquals(0, source.creditNotes().size());
        assertEquals(0, source.creditNoteLines().size());
    }

    @Test
    void executesProviderMethodTransactionAndRoomFiltersWithoutStringInjection() {
        PropertyRevenueRepository.PropertyRevenueSource source = repository.load(new NormalizedFilters(
                FinancialContext.PROPERTY_COMMERCE,
                RecognitionBasis.NET,
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"),
                "Asia/Ho_Chi_Minh",
                42L,
                "momo",
                "bank_qr",
                "room_payment",
                "deluxe",
                null));

        assertEquals(0, source.transactions().size());
        assertEquals(0, source.invoices().size());
    }

    @Test
    void activeTenantFilterAndResolvedPropertyScopeMustBothMatch() {
        Hotel first = persistHotel("first");
        Hotel second = persistHotel("second");
        persistTransaction(first, "first-transaction");
        persistTransaction(second, "second-transaction");
        entityManager.flush();
        entityManager.clear();

        entityManager.unwrap(Session.class)
                .enableFilter("propertyFinancialTransactionTenantFilter")
                .setParameter("hotelId", first.getId());

        PropertyRevenueRepository.PropertyRevenueSource firstSource = repository.load(
                propertyFilters(first.getId(), null));
        PropertyRevenueRepository.PropertyRevenueSource mismatchedSource = repository.load(
                propertyFilters(second.getId(), null));

        assertEquals(1, firstSource.transactions().size());
        assertEquals("first-transaction", firstSource.transactions().getFirst().publicId());
        assertEquals(0, mismatchedSource.transactions().size());
    }

    @Test
    void rejectsPlatformContextAndPlatformOnlyFilters() {
        assertThrows(IllegalArgumentException.class, () -> repository.load(new NormalizedFilters(
                FinancialContext.PLATFORM_BILLING,
                RecognitionBasis.CASH_COLLECTED,
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"),
                "UTC",
                null,
                null,
                null,
                null,
                null,
                null)));
        assertThrows(IllegalArgumentException.class, () -> repository.load(propertyFilters(42L, "PRO")));
    }

    private NormalizedFilters propertyFilters(String planCode) {
        return propertyFilters(42L, planCode);
    }

    private NormalizedFilters propertyFilters(Long propertyId, String planCode) {
        return new NormalizedFilters(
                FinancialContext.PROPERTY_COMMERCE,
                RecognitionBasis.CASH_COLLECTED,
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"),
                "Asia/Ho_Chi_Minh",
                propertyId,
                null,
                null,
                null,
                null,
                planCode);
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

    private void persistTransaction(Hotel hotel, String publicId) {
        User customer = new User();
        customer.setUsername(publicId + '-' + UUID.randomUUID());
        customer.setEmail(publicId + "@example.com");
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

        entityManager.persist(PropertyFinancialTransaction.record(
                publicId,
                hotel,
                reservation,
                null,
                null,
                null,
                PropertyFinancialTransaction.TransactionType.ROOM_PAYMENT,
                PropertyFinancialTransaction.Direction.DEBIT,
                VndMoney.of(1_000_000),
                "BANK_QR",
                "MOMO",
                null,
                "provider-" + publicId,
                "effect-" + publicId,
                "SYSTEM",
                null,
                "Reporting fixture",
                LocalDateTime.of(2026, 7, 15, 0, 0)));
    }
}
