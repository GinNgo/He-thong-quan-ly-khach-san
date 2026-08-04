package com.hotel.propertycommerce.checkout;

import com.hotel.entities.Hotel;
import com.hotel.entities.HotelService;
import com.hotel.entities.Payment;
import com.hotel.entities.Reservation;
import com.hotel.entities.ReservationServiceItem;
import com.hotel.entities.User;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.propertycommerce.folio.ReservationChargeLine;
import com.hotel.propertycommerce.folio.ReservationChargeLineRepository;
import com.hotel.propertycommerce.payment.PropertyFinancialTransaction;
import com.hotel.propertycommerce.payment.PropertyFinancialTransactionRepository;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.HotelServiceRepository;
import com.hotel.repositories.PaymentRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.ReservationServiceItemRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.services.PropertyAccessService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@DataJpaTest
@ContextConfiguration(classes = FolioDatabaseReconciliationIntegrationTest.TestApplication.class)
@Import(FolioCalculationService.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:folio-database-reconciliation;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class FolioDatabaseReconciliationIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackages = "com.hotel")
    @EnableJpaRepositories(basePackages = "com.hotel")
    static class TestApplication {
    }

    @org.springframework.beans.factory.annotation.Autowired private FolioCalculationService folioService;
    @org.springframework.beans.factory.annotation.Autowired private UserRepository userRepository;
    @org.springframework.beans.factory.annotation.Autowired private HotelRepository hotelRepository;
    @org.springframework.beans.factory.annotation.Autowired private HotelServiceRepository hotelServiceRepository;
    @org.springframework.beans.factory.annotation.Autowired private ReservationRepository reservationRepository;
    @org.springframework.beans.factory.annotation.Autowired private ReservationServiceItemRepository legacyServiceRepository;
    @org.springframework.beans.factory.annotation.Autowired private ReservationChargeLineRepository chargeLineRepository;
    @org.springframework.beans.factory.annotation.Autowired private PropertyFinancialTransactionRepository transactionRepository;
    @org.springframework.beans.factory.annotation.Autowired private PaymentRepository legacyPaymentRepository;
    @org.springframework.beans.factory.annotation.Autowired private EntityManager entityManager;

    @MockBean private PropertyAccessService propertyAccessService;

    @Test
    void reconcilesCanonicalAndLegacyDatabaseRowsToTheExactDongWithoutDoubleCounting() {
        User customer = userRepository.saveAndFlush(user());
        Hotel hotel = hotelRepository.saveAndFlush(hotel());
        HotelService service = hotelServiceRepository.saveAndFlush(hotelService(hotel));
        Reservation reservation = reservationRepository.saveAndFlush(reservation(customer, hotel));

        ReservationServiceItem carriedLegacyService = legacyServiceRepository.saveAndFlush(
                legacyService(reservation, service, 50_000));
        legacyServiceRepository.saveAndFlush(legacyService(reservation, service, 100_000));

        ReservationChargeLine migratedService = chargeLine( hotel, reservation,
                ReservationChargeLine.ChargeType.SERVICE, 50_000, "MIGRATED-SERVICE", null);
        ReflectionTestUtils.setField(migratedService, "legacyServiceItemId", carriedLegacyService.getId());
        chargeLineRepository.saveAndFlush(migratedService);
        chargeLineRepository.saveAndFlush(chargeLine(hotel, reservation,
                ReservationChargeLine.ChargeType.SURCHARGE, 25_000, "LATE-CHECKOUT", null));
        ReservationChargeLine reversed = chargeLineRepository.saveAndFlush(chargeLine(hotel, reservation,
                ReservationChargeLine.ChargeType.SURCHARGE, 10_000, "INCORRECT-FEE", null));
        chargeLineRepository.saveAndFlush(chargeLine(hotel, reservation,
                ReservationChargeLine.ChargeType.ADJUSTMENT, 10_000, "REVERSE-INCORRECT-FEE", reversed));

        PropertyFinancialTransaction deposit = transactionRepository.saveAndFlush(
                debit(hotel, reservation, 300_000, "deposit"));
        transactionRepository.saveAndFlush(debit(hotel, reservation, 900_000, "balance"));
        transactionRepository.saveAndFlush(refund(hotel, reservation, deposit, 100_000, "refund"));
        legacyPaymentRepository.saveAndFlush(legacyPayment(reservation, 999_999));

        entityManager.flush();
        entityManager.clear();
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(propertyAccessService.accessibleHotelIds()).thenReturn(Set.of(hotel.getId()));

        FolioCalculationService.Folio folio = folioService.calculate(reservation.getId());

        assertThat(folio.roomCharges().amount()).isEqualByComparingTo("1000000");
        assertThat(folio.serviceCharges().amount()).isEqualByComparingTo("150000");
        assertThat(folio.surchargeCharges().amount()).isEqualByComparingTo("25000");
        assertThat(folio.grossCharges().amount()).isEqualByComparingTo("1175000");
        assertThat(folio.depositRequired().amount()).isEqualByComparingTo("300000");
        assertThat(folio.successfulPayments().amount()).isEqualByComparingTo("1200000");
        assertThat(folio.successfulRefunds().amount()).isEqualByComparingTo("100000");
        assertThat(folio.netSettled().amount()).isEqualByComparingTo("1100000");
        assertThat(folio.balance()).isEqualByComparingTo("75000");
        assertThat(folio.lines()).hasSize(6);
    }

    @Test
    void hidesAReservationOwnedByAnotherProperty() {
        User customer = userRepository.saveAndFlush(user());
        Hotel accessible = hotelRepository.saveAndFlush(hotel());
        Hotel other = hotelRepository.saveAndFlush(hotel());
        Reservation otherReservation = reservationRepository.saveAndFlush(reservation(customer, other));
        entityManager.clear();
        when(propertyAccessService.isSystemAdministrator()).thenReturn(false);
        when(propertyAccessService.accessibleHotelIds()).thenReturn(Set.of(accessible.getId()));

        assertThatThrownBy(() -> folioService.calculate(otherReservation.getId()))
                .isInstanceOfSatisfying(FinancialException.class,
                        exception -> assertThat(exception.code()).isEqualTo(FinancialErrorCode.RESOURCE_NOT_FOUND));
    }

    private User user() {
        User user = new User();
        String identity = "folio-" + UUID.randomUUID();
        user.setUsername(identity);
        user.setEmail(identity + "@example.com");
        user.setPasswordHash("hash");
        user.setStatus("ACTIVE");
        return user;
    }

    private Hotel hotel() {
        Hotel hotel = new Hotel();
        hotel.setName("Folio Hotel " + UUID.randomUUID());
        hotel.setAddressLine("Address");
        hotel.setCity("City");
        hotel.setCountry("VN");
        hotel.setStatus("ACTIVE");
        hotel.setOperationStatus("ACTIVE");
        hotel.setApprovalStatus("APPROVED");
        return hotel;
    }

    private HotelService hotelService(Hotel hotel) {
        HotelService service = new HotelService();
        service.setHotel(hotel);
        service.setSystemService(false);
        service.setCode("BREAKFAST");
        service.setNameVi("Bua sang");
        service.setNameEn("Breakfast");
        service.setPrice(BigDecimal.valueOf(50_000));
        service.setStatus("ACTIVE");
        return service;
    }

    private Reservation reservation(User user, Hotel hotel) {
        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setHotel(hotel);
        reservation.setCheckInDate(LocalDate.of(2026, 8, 10));
        reservation.setCheckOutDate(LocalDate.of(2026, 8, 12));
        reservation.setGuests(2);
        reservation.setTotalAmount(BigDecimal.valueOf(1_000_000));
        reservation.setStatus("CHECKED_IN");
        reservation.setPaymentMethod("MANUAL_TRANSFER");
        ReflectionTestUtils.setField(reservation, "depositBookingTotal", BigDecimal.valueOf(1_000_000));
        ReflectionTestUtils.setField(reservation, "depositRequired", BigDecimal.valueOf(300_000));
        return reservation;
    }

    private ReservationServiceItem legacyService(
            Reservation reservation, HotelService service, long amount) {
        ReservationServiceItem item = new ReservationServiceItem();
        item.setReservation(reservation);
        item.setHotelService(service);
        item.setQuantity(1);
        item.setPrice(BigDecimal.valueOf(amount));
        item.setTotalAmount(BigDecimal.valueOf(amount));
        item.setUsedAt(LocalDateTime.of(2026, 8, 10, 8, 0));
        item.setStatus("ACTIVE");
        return item;
    }

    private ReservationChargeLine chargeLine(
            Hotel hotel,
            Reservation reservation,
            ReservationChargeLine.ChargeType type,
            long amount,
            String code,
            ReservationChargeLine reverses) {
        return ReservationChargeLine.create(
                hotel, reservation, type, null, "fixture-v1", code, code, null,
                BigDecimal.valueOf(amount), BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(amount),
                type == ReservationChargeLine.ChargeType.SERVICE
                        ? LocalDateTime.of(2026, 8, 10, 8, 0) : null,
                null, reverses);
    }

    private PropertyFinancialTransaction debit(
            Hotel hotel, Reservation reservation, long amount, String identity) {
        return PropertyFinancialTransaction.record(
                "txn-" + identity, hotel, reservation, null, null, null,
                "deposit".equals(identity)
                        ? PropertyFinancialTransaction.TransactionType.BOOKING_DEPOSIT
                        : PropertyFinancialTransaction.TransactionType.ROOM_PAYMENT,
                PropertyFinancialTransaction.Direction.DEBIT, VndMoney.of(amount),
                "MANUAL_TRANSFER", "BANK", PaymentEnvironment.SIMULATOR,
                "provider-" + identity, "effect-" + identity, "SYSTEM", null,
                "Successful payment", LocalDateTime.of(2026, 8, 10, 9, 0));
    }

    private PropertyFinancialTransaction refund(
            Hotel hotel,
            Reservation reservation,
            PropertyFinancialTransaction original,
            long amount,
            String identity) {
        return PropertyFinancialTransaction.record(
                "txn-" + identity, hotel, reservation, null, null, original,
                PropertyFinancialTransaction.TransactionType.REFUND,
                PropertyFinancialTransaction.Direction.CREDIT, VndMoney.of(amount),
                "MANUAL_TRANSFER", "BANK", PaymentEnvironment.SIMULATOR,
                "provider-" + identity, "effect-" + identity, "SYSTEM", null,
                "Successful refund", LocalDateTime.of(2026, 8, 10, 10, 0));
    }

    private Payment legacyPayment(Reservation reservation, long amount) {
        Payment payment = new Payment();
        payment.setReservation(reservation);
        payment.setAmount(BigDecimal.valueOf(amount));
        payment.setPaymentMethod("LEGACY");
        payment.setStatus("SUCCESS");
        payment.setTransactionId("legacy-" + UUID.randomUUID());
        payment.setPaymentDate(LocalDateTime.of(2026, 8, 10, 7, 0));
        return payment;
    }
}
