package com.hotel.services;

import com.hotel.domain.lifecycle.BookingLifecyclePolicy;
import com.hotel.domain.lifecycle.PaymentStatus;
import com.hotel.domain.lifecycle.RefundStatus;
import com.hotel.domain.lifecycle.TransitionDecision;
import com.hotel.entities.Payment;
import com.hotel.entities.RefundProviderAttempt;
import com.hotel.entities.RefundRequest;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.repositories.PaymentRepository;
import com.hotel.repositories.RefundProviderAttemptRepository;
import com.hotel.repositories.RefundRequestRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class RefundService {

    private static final BigDecimal POINT_VALUE = new BigDecimal("100000");

    private final RefundRequestRepository requestRepository;
    private final RefundProviderAttemptRepository attemptRepository;
    private final ReservationRepository reservationRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    public RefundService(
            RefundRequestRepository requestRepository,
            RefundProviderAttemptRepository attemptRepository,
            ReservationRepository reservationRepository,
            PaymentRepository paymentRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            Clock clock) {
        this.requestRepository = requestRepository;
        this.attemptRepository = attemptRepository;
        this.reservationRepository = reservationRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    @Transactional
    public List<RefundRequest> requestRefundsForSuccessfulPayments(Long reservationId, String reason) {
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        LocalDateTime now = LocalDateTime.now(clock);
        List<RefundRequest> requests = new ArrayList<>();

        for (Payment payment : paymentRepository.findByReservationId(reservationId)) {
            if (!isRefundableCharge(payment)) {
                continue;
            }
            RefundRequest request = requestRepository.findByOriginalPaymentId(payment.getId())
                    .orElseGet(() -> createRequest(reservation, payment, reason, now));
            sendRequestedNotificationOnce(request, now);
            requests.add(request);
        }
        return requests;
    }

    @Transactional
    public RefundRequest markProviderPending(
            Long requestId,
            Integer attemptNumber,
            String providerReference) {
        return markProviderPending(requestId, attemptNumber, providerReference, null, null);
    }

    @Transactional
    public RefundRequest markProviderPending(
            Long requestId,
            Integer attemptNumber,
            String providerReference,
            String responseCode,
            String detailsJson) {
        return applyProviderOutcome(
                requestId,
                attemptNumber,
                RefundStatus.PENDING_PROVIDER,
                providerReference,
                null,
                responseCode,
                detailsJson);
    }

    @Transactional
    public RefundRequest markProviderSucceeded(
            Long requestId,
            Integer attemptNumber,
            String providerReference,
            String responseCode) {
        return markProviderSucceeded(requestId, attemptNumber, providerReference, responseCode, null);
    }

    @Transactional
    public RefundRequest markProviderSucceeded(
            Long requestId,
            Integer attemptNumber,
            String providerReference,
            String responseCode,
            String detailsJson) {
        return applyProviderOutcome(
                requestId,
                attemptNumber,
                RefundStatus.SUCCEEDED,
                providerReference,
                null,
                responseCode,
                detailsJson);
    }

    @Transactional
    public RefundRequest markProviderFailed(
            Long requestId,
            Integer attemptNumber,
            String providerReference,
            String failureCode,
            String responseCode) {
        return markProviderFailed(
                requestId,
                attemptNumber,
                providerReference,
                failureCode,
                responseCode,
                null);
    }

    @Transactional
    public RefundRequest markProviderFailed(
            Long requestId,
            Integer attemptNumber,
            String providerReference,
            String failureCode,
            String responseCode,
            String detailsJson) {
        return applyProviderOutcome(
                requestId,
                attemptNumber,
                RefundStatus.FAILED,
                providerReference,
                failureCode,
                responseCode,
                detailsJson);
    }

    @Transactional(readOnly = true)
    public List<RefundRequest> getByReservation(Long reservationId) {
        return requestRepository.findByReservationIdOrderByIdAsc(reservationId);
    }

    private RefundRequest createRequest(
            Reservation reservation,
            Payment payment,
            String reason,
            LocalDateTime now) {
        RefundRequest request = new RefundRequest();
        request.setPublicId(UUID.randomUUID().toString());
        request.setReservation(reservation);
        request.setOriginalPayment(payment);
        request.setHotel(reservation.getHotel());
        request.setRequestedAmount(payment.getAmount());
        request.setCurrency("VND");
        request.setProvider(normalizeProvider(payment.getPaymentMethod()));
        request.setStatus(RefundStatus.REQUESTED.name());
        request.setIdempotencyKey("CANCEL-" + reservation.getId() + "-PAYMENT-" + payment.getId());
        request.setReason(reason == null || reason.isBlank() ? "RESERVATION_CANCELLED" : reason.trim());
        request.setRequestedAt(now);
        RefundRequest saved = requestRepository.save(request);

        RefundProviderAttempt attempt = new RefundProviderAttempt();
        attempt.setRefundRequest(saved);
        attempt.setHotel(saved.getHotel());
        attempt.setProvider(saved.getProvider());
        attempt.setAttemptNumber(1);
        attempt.setIdempotencyKey("REFUND-" + saved.getPublicId() + "-1");
        attempt.setRequestedAmount(saved.getRequestedAmount());
        attempt.setStatus(RefundStatus.REQUESTED.name());
        attempt.setRequestedAt(now);
        attemptRepository.save(attempt);
        return saved;
    }

    private RefundRequest applyProviderOutcome(
            Long requestId,
            Integer attemptNumber,
            RefundStatus target,
            String providerReference,
            String failureCode,
            String responseCode,
            String detailsJson) {
        if (attemptNumber == null || attemptNumber < 1) {
            throw new IllegalArgumentException("Refund attempt number must be positive.");
        }
        RefundRequest request = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Refund request not found."));
        RefundStatus current = RefundStatus.fromStorage(request.getStatus());
        TransitionDecision decision = BookingLifecyclePolicy.refundTransition(current, target);
        if (decision == TransitionDecision.IDEMPOTENT) {
            if (target == RefundStatus.PENDING_PROVIDER) {
                RefundProviderAttempt attempt = attemptRepository.findForUpdate(requestId, attemptNumber)
                        .orElseThrow(() -> new IllegalArgumentException("Refund provider attempt not found."));
                if (providerReference != null && !providerReference.isBlank()) {
                    attempt.setProviderReference(providerReference.trim());
                    request.setProviderRefundReference(providerReference.trim());
                }
                attempt.setResponseCode(trimToNull(responseCode));
                attempt.setDetailsJson(trimToNull(detailsJson));
                attemptRepository.save(attempt);
                requestRepository.save(request);
            }
            return request;
        }
        if (decision != TransitionDecision.APPLY) {
            throw new IllegalStateException("Refund transition is not valid from " + current + " to " + target + ".");
        }

        RefundProviderAttempt attempt = attemptRepository.findForUpdate(requestId, attemptNumber)
                .orElseThrow(() -> new IllegalArgumentException("Refund provider attempt not found."));
        LocalDateTime now = LocalDateTime.now(clock);
        attempt.setStatus(target.name());
        attempt.setProviderReference(trimToNull(providerReference));
        attempt.setFailureCode(trimToNull(failureCode));
        attempt.setResponseCode(trimToNull(responseCode));
        attempt.setDetailsJson(trimToNull(detailsJson));
        if (target == RefundStatus.SUCCEEDED || target == RefundStatus.FAILED) {
            attempt.setCompletedAt(now);
        }
        attemptRepository.save(attempt);

        request.setStatus(target.name());
        request.setProviderRefundReference(trimToNull(providerReference));
        request.setFailureCode(trimToNull(failureCode));
        if (target == RefundStatus.SUCCEEDED) {
            createRefundLedgerOnce(request, now);
            reversePointsOnce(request, now);
            request.setCompletedAt(now);
        } else if (target == RefundStatus.FAILED) {
            request.setCompletedAt(now);
        }
        RefundRequest saved = requestRepository.save(request);
        if (target == RefundStatus.SUCCEEDED || target == RefundStatus.FAILED) {
            sendTerminalNotificationOnce(saved, target, now);
        }
        return saved;
    }

    private boolean isRefundableCharge(Payment payment) {
        return payment.getId() != null
                && payment.getAmount() != null
                && payment.getAmount().signum() > 0
                && PaymentStatus.fromStorage(payment.getStatus()) == PaymentStatus.SUCCEEDED;
    }

    private void createRefundLedgerOnce(RefundRequest request, LocalDateTime now) {
        String transactionId = "REFUND-" + request.getPublicId();
        if (paymentRepository.findByTransactionId(transactionId).isPresent()) {
            return;
        }
        Payment refund = new Payment();
        refund.setReservation(request.getReservation());
        refund.setAmount(request.getRequestedAmount().negate());
        refund.setPaymentMethod(request.getProvider());
        refund.setStatus(PaymentStatus.SUCCEEDED.name());
        refund.setTransactionId(transactionId);
        refund.setPaymentDate(now);
        paymentRepository.save(refund);
    }

    private void reversePointsOnce(RefundRequest request, LocalDateTime now) {
        if (request.getPointsReversedAt() != null || request.getReservation().getUser() == null) {
            return;
        }
        User reservationUser = request.getReservation().getUser();
        User user = userRepository.findByIdForUpdate(reservationUser.getId())
                .orElseThrow(() -> new IllegalStateException("Refund customer no longer exists."));
        int points = request.getRequestedAmount().divide(POINT_VALUE, RoundingMode.DOWN).intValue();
        int current = user.getPoints() == null ? 0 : user.getPoints();
        user.setPoints(Math.max(0, current - points));
        userRepository.save(user);
        request.setPointsReversedAt(now);
    }

    private void sendRequestedNotificationOnce(RefundRequest request, LocalDateTime now) {
        User user = request.getReservation().getUser();
        if (request.getRequestNotifiedAt() != null || user == null || user.getUsername() == null) {
            return;
        }
        notificationService.sendUserNotificationOnce(
                "REFUND:" + request.getPublicId() + ":REQUESTED",
                user.getUsername(),
                user.getId(),
                "PAYMENT",
                "Yêu cầu hoàn tiền đã được ghi nhận",
                "Yêu cầu hoàn " + request.getRequestedAmount().toPlainString()
                        + " VND cho booking #" + request.getReservation().getId() + " đang chờ xử lý.");
        request.setRequestNotifiedAt(now);
        requestRepository.save(request);
    }

    private void sendTerminalNotificationOnce(
            RefundRequest request,
            RefundStatus status,
            LocalDateTime now) {
        User user = request.getReservation().getUser();
        if (request.getTerminalNotifiedAt() != null || user == null || user.getUsername() == null) {
            return;
        }
        boolean succeeded = status == RefundStatus.SUCCEEDED;
        notificationService.sendUserNotificationOnce(
                "REFUND:" + request.getPublicId() + ":" + status.name(),
                user.getUsername(),
                user.getId(),
                "PAYMENT",
                succeeded ? "Hoàn tiền thành công" : "Hoàn tiền chưa thành công",
                succeeded
                        ? "Khoản hoàn " + request.getRequestedAmount().toPlainString()
                                + " VND cho booking #" + request.getReservation().getId() + " đã hoàn tất."
                        : "Khoản hoàn cho booking #" + request.getReservation().getId()
                                + " cần được kiểm tra thêm.");
        request.setTerminalNotifiedAt(now);
        requestRepository.save(request);
    }

    private String normalizeProvider(String value) {
        return value == null || value.isBlank() ? "INTERNAL" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
