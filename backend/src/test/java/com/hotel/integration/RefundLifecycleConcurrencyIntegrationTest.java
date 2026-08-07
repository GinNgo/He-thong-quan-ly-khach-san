package com.hotel.integration;

import com.hotel.entities.Hotel;
import com.hotel.entities.Payment;
import com.hotel.entities.RefundRequest;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.NotificationRepository;
import com.hotel.repositories.PaymentRepository;
import com.hotel.repositories.RefundProviderAttemptRepository;
import com.hotel.repositories.RefundRequestRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.services.RefundService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:refund-lifecycle-concurrency;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "app.reservation-hold.expiry-scan-ms=3600000"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RefundLifecycleConcurrencyIntegrationTest {

    @Autowired
    private HotelRepository hotelRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private RefundRequestRepository refundRequestRepository;
    @Autowired
    private RefundProviderAttemptRepository attemptRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private RefundService refundService;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void concurrentProviderSuccessReversesFinancialEffectsAndNotifiesExactlyOnce() throws Exception {
        FixtureIds ids = transactionTemplate.execute(status -> createFixture());
        assertThat(ids).isNotNull();

        RefundRequest request = refundService.requestRefundsForSuccessfulPayments(
                ids.reservationId(),
                "RESERVATION_CANCELLED").getFirst();
        refundService.markProviderPending(request.getId(), 1, "MOMO-REFUND-CONCURRENT");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<String> invoke = () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent refund start timed out.");
                }
                return refundService.markProviderSucceeded(
                        request.getId(),
                        1,
                        "MOMO-REFUND-CONCURRENT",
                        "0").getStatus();
            };

            Future<String> first = executor.submit(invoke);
            Future<String> second = executor.submit(invoke);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsOnly("SUCCEEDED");
        } finally {
            executor.shutdownNow();
        }

        RefundRequest saved = refundRequestRepository.findById(request.getId()).orElseThrow();
        User user = userRepository.findById(ids.userId()).orElseThrow();
        List<Payment> payments = paymentRepository.findByReservationId(ids.reservationId());
        assertThat(saved.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(saved.getPointsReversedAt()).isNotNull();
        assertThat(user.getPoints()).isEqualTo(8);
        assertThat(payments).hasSize(2);
        assertThat(payments.stream().filter(payment -> payment.getAmount().signum() < 0).count()).isEqualTo(1);
        assertThat(attemptRepository.findByRefundRequestIdOrderByAttemptNumberAsc(request.getId()))
                .singleElement()
                .extracting(attempt -> attempt.getStatus())
                .isEqualTo("SUCCEEDED");
        assertThat(notificationRepository.findAll().stream()
                .filter(notification -> ("REFUND:" + request.getPublicId() + ":SUCCEEDED")
                        .equals(notification.getEventKey()))
                .count()).isEqualTo(1);
    }

    private FixtureIds createFixture() {
        Hotel hotel = new Hotel();
        hotel.setCode("REFUND-CONCURRENCY-HOTEL");
        hotel.setSlug("refund-concurrency-hotel");
        hotel.setName("Refund concurrency hotel");
        hotel.setNameVi("Khach san refund");
        hotel.setAddressLine("1 Refund Street");
        hotel.setCity("Ho Chi Minh City");
        hotel.setCountry("Viet Nam");
        hotel.setStatus("ACTIVE");
        hotel.setApprovalStatus("APPROVED");
        hotel.setOperationStatus("ACTIVE");
        hotel = hotelRepository.saveAndFlush(hotel);

        User user = new User();
        user.setUsername("refund_concurrency_user");
        user.setEmail("refund_concurrency_user@example.test");
        user.setPasswordHash("test");
        user.setFullName("Refund Concurrency User");
        user.setStatus("ACTIVE");
        user.setPoints(10);
        user = userRepository.saveAndFlush(user);

        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setHotel(hotel);
        reservation.setCheckInDate(LocalDate.of(2028, 3, 1));
        reservation.setCheckOutDate(LocalDate.of(2028, 3, 2));
        reservation.setGuests(2);
        reservation.setTotalAmount(new BigDecimal("250000"));
        reservation.setStatus("CANCELLED");
        reservation = reservationRepository.saveAndFlush(reservation);

        Payment payment = new Payment();
        payment.setReservation(reservation);
        payment.setAmount(new BigDecimal("250000"));
        payment.setPaymentMethod("MOMO");
        payment.setStatus("SUCCEEDED");
        payment.setTransactionId("REFUND-CONCURRENCY-CHARGE");
        payment.setPaymentDate(LocalDateTime.now());
        paymentRepository.saveAndFlush(payment);
        return new FixtureIds(reservation.getId(), user.getId());
    }

    private record FixtureIds(Long reservationId, Long userId) {
    }
}
