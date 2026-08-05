package com.hotel.propertycommerce.payment;

import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import com.hotel.paymentprovider.domain.FinancialStates.PaymentState;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.propertycommerce.booking.BookingFinancialSummaryRepository;
import com.hotel.propertycommerce.booking.BookingFinancialSummaryService;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.UserRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ContextConfiguration(classes = PropertyPaymentPersistenceIntegrationTest.TestApplication.class)
@Import(BookingFinancialSummaryService.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:property-payment-persistence;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class PropertyPaymentPersistenceIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackages = "com.hotel")
    @EnableJpaRepositories(basePackages = "com.hotel")
    static class TestApplication {
    }

    @org.springframework.beans.factory.annotation.Autowired
    private PropertyPaymentAttemptRepository attemptRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private PropertyFinancialTransactionRepository transactionRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private BookingFinancialSummaryRepository summaryRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private BookingFinancialSummaryService summaryService;

    @org.springframework.beans.factory.annotation.Autowired
    private UserRepository userRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private HotelRepository hotelRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private ReservationRepository reservationRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private EntityManager entityManager;

    @Test
    void tenantFiltersProtectAttemptsAndImmutableLedgerQueries() {
        User customer = userRepository.saveAndFlush(user());
        Hotel first = hotelRepository.saveAndFlush(hotel("first"));
        Hotel second = hotelRepository.saveAndFlush(hotel("second"));
        Reservation firstReservation = reservationRepository.saveAndFlush(reservation(customer, first));
        Reservation secondReservation = reservationRepository.saveAndFlush(reservation(customer, second));

        PropertyPaymentAttempt firstAttempt = attemptRepository.saveAndFlush(
                attempt("attempt-first", "idem-first", first, firstReservation));
        attemptRepository.saveAndFlush(attempt("attempt-second", "idem-second", second, secondReservation));
        firstAttempt.transitionTo(PaymentState.PENDING, LocalDateTime.now(), null, null);
        firstAttempt = attemptRepository.saveAndFlush(firstAttempt);
        assertTrue(firstAttempt.getVersion() > 0);
        transactionRepository.saveAndFlush(transaction("transaction-first", "effect-first",
                first, firstReservation, firstAttempt));
        transactionRepository.saveAndFlush(transaction("transaction-second", "effect-second",
                second, secondReservation, null));
        summaryService.refresh(firstReservation.getId());
        summaryService.refresh(secondReservation.getId());
        entityManager.clear();

        Session session = entityManager.unwrap(Session.class);
        session.enableFilter("propertyPaymentAttemptTenantFilter").setParameter("hotelId", first.getId());
        session.enableFilter("propertyFinancialTransactionTenantFilter").setParameter("hotelId", first.getId());
        session.enableFilter("bookingFinancialSummaryTenantFilter").setParameter("hotelId", first.getId());

        assertEquals(1, attemptRepository.findAll().size());
        assertEquals("attempt-first", attemptRepository.findAll().getFirst().getPublicId());
        assertTrue(attemptRepository.findByPublicId("attempt-second").isEmpty());
        assertEquals(1, transactionRepository.findAll().size());
        assertEquals("transaction-first", transactionRepository.findAll().getFirst().getPublicId());
        assertTrue(transactionRepository.findByIdempotencyIdentity("effect-second").isEmpty());
        assertEquals(1, summaryRepository.findAll().size());
        assertTrue(summaryRepository.findByReservationId(secondReservation.getId()).isEmpty());
    }

    private PropertyPaymentAttempt attempt(
            String publicId,
            String idempotencyKey,
            Hotel hotel,
            Reservation reservation) {
        return PropertyPaymentAttempt.create(
                publicId,
                hotel,
                reservation,
                null,
                reservation.getUser(),
                PropertyPaymentAttempt.Purpose.DEPOSIT,
                "MANUAL_TRANSFER",
                "BANK",
                PaymentEnvironment.SIMULATOR,
                VndMoney.of(200_000),
                publicId.toUpperCase(),
                "{\"account\":\"****6789\"}",
                idempotencyKey,
                "hash-" + idempotencyKey,
                LocalDateTime.now().plusMinutes(15));
    }

    private PropertyFinancialTransaction transaction(
            String publicId,
            String identity,
            Hotel hotel,
            Reservation reservation,
            PropertyPaymentAttempt attempt) {
        return PropertyFinancialTransaction.record(
                publicId,
                hotel,
                reservation,
                null,
                attempt,
                null,
                PropertyFinancialTransaction.TransactionType.BOOKING_DEPOSIT,
                PropertyFinancialTransaction.Direction.DEBIT,
                VndMoney.of(200_000),
                "MANUAL_TRANSFER",
                "BANK",
                PaymentEnvironment.SIMULATOR,
                "provider-" + identity,
                identity,
                "SYSTEM",
                null,
                "Verified deposit",
                LocalDateTime.now());
    }

    private User user() {
        User user = new User();
        String identity = "customer-" + UUID.randomUUID();
        user.setUsername(identity);
        user.setEmail(identity + "@example.com");
        user.setPasswordHash("hash");
        user.setStatus("ACTIVE");
        return user;
    }

    private Hotel hotel(String prefix) {
        Hotel hotel = new Hotel();
        hotel.setName(prefix + "-" + UUID.randomUUID());
        hotel.setAddressLine("Address");
        hotel.setCity("City");
        hotel.setCountry("VN");
        hotel.setStatus("ACTIVE");
        hotel.setOperationStatus("ACTIVE");
        hotel.setApprovalStatus("APPROVED");
        return hotel;
    }

    private Reservation reservation(User user, Hotel hotel) {
        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setHotel(hotel);
        reservation.setCheckInDate(LocalDate.of(2026, 8, 10));
        reservation.setCheckOutDate(LocalDate.of(2026, 8, 12));
        reservation.setGuests(2);
        reservation.setTotalAmount(BigDecimal.valueOf(1_200_000));
        reservation.setStatus("PENDING_PAYMENT");
        reservation.setPaymentMethod("MANUAL_TRANSFER");
        return reservation;
    }
}
