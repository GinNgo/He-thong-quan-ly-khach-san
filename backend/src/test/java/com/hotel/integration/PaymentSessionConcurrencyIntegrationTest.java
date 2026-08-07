package com.hotel.integration;

import com.hotel.entities.Hotel;
import com.hotel.entities.PaymentSession;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.PaymentRepository;
import com.hotel.repositories.PaymentSessionRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.services.PaymentSessionService;
import com.hotel.services.payment.VnpayCallbackData;
import com.hotel.services.payment.VnpayIpnResponse;
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
        "spring.datasource.url=jdbc:h2:mem:payment-session-concurrency;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "app.reservation-hold.expiry-scan-ms=3600000"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PaymentSessionConcurrencyIntegrationTest {

    @Autowired
    private HotelRepository hotelRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private PaymentSessionRepository sessionRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private PaymentSessionService paymentSessionService;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void concurrentDuplicateVnpayCallbacksConfirmAndAwardPointsExactlyOnce() throws Exception {
        FixtureIds ids = transactionTemplate.execute(status -> createFixture());
        assertThat(ids).isNotNull();

        VnpayCallbackData callback = new VnpayCallbackData(
                ids.providerReference(),
                "VNPAY-TRANSACTION-1",
                new BigDecimal("250000"),
                "00",
                "00",
                true);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<VnpayIpnResponse> invoke = () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent callback start timed out.");
                }
                return paymentSessionService.processVnpayCallback(callback);
            };

            Future<VnpayIpnResponse> first = executor.submit(invoke);
            Future<VnpayIpnResponse> second = executor.submit(invoke);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(
                    first.get(20, TimeUnit.SECONDS).responseCode(),
                    second.get(20, TimeUnit.SECONDS).responseCode()))
                    .containsExactlyInAnyOrder("00", "02");
        } finally {
            executor.shutdownNow();
        }

        Reservation reservation = reservationRepository.findById(ids.reservationId()).orElseThrow();
        PaymentSession session = sessionRepository.findById(ids.sessionId()).orElseThrow();
        User user = userRepository.findById(ids.userId()).orElseThrow();
        assertThat(reservation.getStatus()).isEqualTo("CONFIRMED");
        assertThat(session.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(session.isReconciliationRequired()).isFalse();
        assertThat(paymentRepository.findByReservationId(ids.reservationId())).hasSize(1);
        assertThat(user.getPoints()).isEqualTo(2);
    }

    private FixtureIds createFixture() {
        Hotel hotel = new Hotel();
        hotel.setCode("PAYMENT-CALLBACK-HOTEL");
        hotel.setSlug("payment-callback-hotel");
        hotel.setName("Payment callback hotel");
        hotel.setNameVi("Khach san callback");
        hotel.setAddressLine("1 Test Street");
        hotel.setCity("Ho Chi Minh City");
        hotel.setCountry("Viet Nam");
        hotel.setStatus("ACTIVE");
        hotel.setApprovalStatus("APPROVED");
        hotel.setOperationStatus("ACTIVE");
        hotel = hotelRepository.saveAndFlush(hotel);

        User user = new User();
        user.setUsername("payment_callback_user");
        user.setEmail("payment_callback_user@example.test");
        user.setPasswordHash("test");
        user.setFullName("Payment Callback User");
        user.setStatus("ACTIVE");
        user.setPoints(0);
        user = userRepository.saveAndFlush(user);

        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setHotel(hotel);
        reservation.setCheckInDate(LocalDate.of(2028, 2, 1));
        reservation.setCheckOutDate(LocalDate.of(2028, 2, 2));
        reservation.setGuests(2);
        reservation.setTotalAmount(new BigDecimal("250000"));
        reservation.setStatus("PENDING_PAYMENT");
        reservation = reservationRepository.saveAndFlush(reservation);

        PaymentSession session = new PaymentSession();
        session.setPublicId("payment-session-concurrency");
        session.setReservation(reservation);
        session.setHotel(hotel);
        session.setOwner(user);
        session.setProvider("VNPAY");
        session.setMethod("VNPAY");
        session.setExpectedAmount(new BigDecimal("250000"));
        session.setCurrency("VND");
        session.setProviderReference("VNPAY-CONCURRENT-REFERENCE");
        session.setIdempotencyKey("payment-concurrency-idempotency");
        session.setStatus("PENDING");
        session.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        session = sessionRepository.saveAndFlush(session);
        return new FixtureIds(reservation.getId(), session.getId(), user.getId(), session.getProviderReference());
    }

    private record FixtureIds(Long reservationId, Long sessionId, Long userId, String providerReference) {
    }
}
