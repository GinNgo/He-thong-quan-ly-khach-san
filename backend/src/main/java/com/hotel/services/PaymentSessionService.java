package com.hotel.services;

import com.hotel.domain.lifecycle.PaymentStatus;
import com.hotel.domain.lifecycle.ReservationStatus;
import com.hotel.domain.payment.PaymentCompletionResult;
import com.hotel.domain.payment.PaymentProvider;
import com.hotel.dtos.PaymentSessionResponse;
import com.hotel.dtos.PaymentSessionStatusResponse;
import com.hotel.entities.PaymentSession;
import com.hotel.entities.Reservation;
import com.hotel.entities.ReservationHold;
import com.hotel.entities.User;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.PaymentSessionRepository;
import com.hotel.repositories.ReservationHoldRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.services.payment.DemoPaymentTokenService;
import com.hotel.services.payment.MomoPaymentGateway;
import com.hotel.services.payment.ProviderCallbackData;
import com.hotel.services.payment.ProviderCallbackOutcome;
import com.hotel.services.payment.VerifiedDemoToken;
import com.hotel.services.payment.VnpayPaymentGateway;
import com.hotel.services.payment.VnpayCallbackData;
import com.hotel.services.payment.VnpayIpnResponse;
import com.hotel.services.payment.ZaloPayPaymentGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentSessionService {

    private final PaymentSessionRepository sessionRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationHoldRepository holdRepository;
    private final PropertyAccessService propertyAccessService;
    private final VnpayPaymentGateway vnpayGateway;
    private final MomoPaymentGateway momoGateway;
    private final ZaloPayPaymentGateway zaloPayGateway;
    private final DemoPaymentTokenService demoTokenService;
    private final PaymentService paymentService;
    private final boolean demoEnabled;
    private final String demoBaseUrl;
    private final Clock clock;

    public PaymentSessionService(
            PaymentSessionRepository sessionRepository,
            ReservationRepository reservationRepository,
            ReservationHoldRepository holdRepository,
            PropertyAccessService propertyAccessService,
            VnpayPaymentGateway vnpayGateway,
            MomoPaymentGateway momoGateway,
            ZaloPayPaymentGateway zaloPayGateway,
            DemoPaymentTokenService demoTokenService,
            PaymentService paymentService,
            @Value("${payment.demo.enabled:false}") boolean demoEnabled,
            @Value("${payment.demo.base-url:http://localhost:4200/payment-simulator}") String demoBaseUrl,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.reservationRepository = reservationRepository;
        this.holdRepository = holdRepository;
        this.propertyAccessService = propertyAccessService;
        this.vnpayGateway = vnpayGateway;
        this.momoGateway = momoGateway;
        this.zaloPayGateway = zaloPayGateway;
        this.demoTokenService = demoTokenService;
        this.paymentService = paymentService;
        this.demoEnabled = demoEnabled;
        this.demoBaseUrl = demoBaseUrl;
        this.clock = clock;
    }

    @Transactional
    public PaymentSessionResponse createSession(
            Long reservationId,
            String providerValue,
            String idempotencyKey,
            String clientIp) {
        validateIdempotencyKey(idempotencyKey);
        PaymentProvider provider = PaymentProvider.fromRequest(providerValue);
        User currentUser = propertyAccessService.currentUser();
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found."));
        requireOwner(reservation, currentUser);

        String normalizedKey = idempotencyKey.trim();
        Optional<PaymentSession> replay = sessionRepository.findByOwnerIdAndIdempotencyKeyForUpdate(
                currentUser.getId(), normalizedKey);
        if (replay.isPresent()) {
            validateReplay(replay.get(), reservation, provider);
            return toResponse(replay.get(), clientIp);
        }

        if (ReservationStatus.fromStorage(reservation.getStatus()) != ReservationStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Only pending-payment reservations can create a payment session.");
        }
        if (sessionRepository.findActiveByReservationIdForUpdate(reservationId).isPresent()) {
            throw new IllegalStateException("Reservation already has an active payment session.");
        }

        ReservationHold hold = holdRepository.findActiveByReservationIdForUpdate(reservationId)
                .orElseThrow(() -> new IllegalStateException("Reservation no longer has an active inventory hold."));
        LocalDateTime now = LocalDateTime.now(clock);
        if (!hold.getExpiresAt().isAfter(now)) {
            throw new IllegalStateException("Reservation inventory hold has expired.");
        }

        PaymentSession session = new PaymentSession();
        session.setPublicId(UUID.randomUUID().toString());
        session.setReservation(reservation);
        session.setHotel(reservation.getHotel());
        session.setOwner(currentUser);
        session.setProvider(provider.name());
        session.setMethod(provider.name());
        session.setExpectedAmount(reservation.getTotalAmount());
        session.setCurrency("VND");
        session.setProviderReference(createProviderReference(provider));
        session.setIdempotencyKey(normalizedKey);
        session.setStatus(PaymentStatus.PENDING.name());
        session.setExpiresAt(hold.getExpiresAt());
        session.setReconciliationRequired(false);
        PaymentSession saved = sessionRepository.save(session);
        return toResponse(saved, clientIp);
    }

    @Transactional
    public PaymentCompletionResult confirmDemoPayment(String token) {
        if (!demoEnabled) {
            throw new IllegalStateException("The internal payment simulator is disabled.");
        }
        VerifiedDemoToken verified = demoTokenService.verify(token);
        Long reservationId = sessionRepository.findReservationIdByPublicId(verified.sessionId())
                .orElseThrow(() -> new IllegalArgumentException("Payment session not found."));
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        PaymentSession session = sessionRepository.findByPublicIdForUpdate(verified.sessionId())
                .orElseThrow(() -> new IllegalArgumentException("Payment session not found."));

        User currentUser = propertyAccessService.currentUser();
        if (session.getOwner() == null || !Objects.equals(session.getOwner().getId(), currentUser.getId())) {
            throw new SecurityException("You cannot confirm this payment session.");
        }

        PaymentStatus status = PaymentStatus.fromStorage(session.getStatus());
        if (status == PaymentStatus.SUCCEEDED) {
            return PaymentCompletionResult.IDEMPOTENT;
        }
        if (status != PaymentStatus.CREATED && status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Payment session cannot be confirmed from its current state.");
        }
        if (PaymentProvider.VNPAY.name().equals(session.getProvider())) {
            throw new IllegalArgumentException("VNPay sessions require an authoritative provider callback.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (!session.getExpiresAt().isAfter(now)) {
            session.setStatus(PaymentStatus.EXPIRED.name());
            sessionRepository.save(session);
            throw new IllegalArgumentException("Payment session has expired.");
        }
        if (!Objects.equals(reservation.getId(), session.getReservation().getId())) {
            throw new IllegalStateException("Payment session reservation binding is invalid.");
        }

        String transactionId = "DEMO-" + session.getProviderReference();
        PaymentCompletionResult result = paymentService.handleSuccessfulPayment(
                reservationId,
                session.getMethod(),
                transactionId);
        session.setStatus(PaymentStatus.SUCCEEDED.name());
        session.setProviderTransactionId(transactionId);
        session.setCompletedAt(now);
        session.setReconciliationRequired(result == PaymentCompletionResult.RECONCILIATION_REQUIRED);
        sessionRepository.save(session);
        return result;
    }

    @Transactional
    public VnpayIpnResponse processVnpayCallback(VnpayCallbackData callback) {
        ProviderCallbackOutcome outcome = processVerifiedCallback(new ProviderCallbackData(
                PaymentProvider.VNPAY,
                callback.providerReference(),
                callback.providerTransactionId(),
                callback.amount(),
                callback.successful(),
                "VNPAY_" + callback.responseCode() + "_" + callback.transactionStatus()));
        return switch (outcome) {
            case NOT_FOUND -> new VnpayIpnResponse("01", "Order not Found");
            case INVALID_AMOUNT -> new VnpayIpnResponse("04", "Invalid Amount");
            case DUPLICATE -> new VnpayIpnResponse("02", "Order already confirmed");
            default -> new VnpayIpnResponse("00", "Confirm Success");
        };
    }

    @Transactional
    public ProviderCallbackOutcome processProviderCallback(ProviderCallbackData callback) {
        return processVerifiedCallback(callback);
    }

    @Transactional
    public PaymentSessionStatusResponse getOwnedSessionStatus(String publicId) {
        PaymentSession session = sessionRepository.findByPublicIdForUpdate(publicId)
                .orElseThrow(() -> new IllegalArgumentException("Payment session not found."));
        User currentUser = propertyAccessService.currentUser();
        if (session.getOwner() == null || currentUser == null
                || !Objects.equals(session.getOwner().getId(), currentUser.getId())) {
            throw new SecurityException("You cannot view this payment session.");
        }

        PaymentStatus status = PaymentStatus.fromStorage(session.getStatus());
        if ((status == PaymentStatus.CREATED || status == PaymentStatus.PENDING)
                && !session.getExpiresAt().isAfter(LocalDateTime.now(clock))) {
            session.setStatus(PaymentStatus.EXPIRED.name());
            sessionRepository.save(session);
        }
        return PaymentSessionStatusResponse.builder()
                .sessionId(session.getPublicId())
                .reservationId(session.getReservation().getId())
                .provider(session.getProvider())
                .amount(session.getExpectedAmount())
                .currency(session.getCurrency())
                .status(session.getStatus())
                .expiresAt(session.getExpiresAt())
                .completedAt(session.getCompletedAt())
                .reconciliationRequired(session.isReconciliationRequired())
                .failureCode(session.getFailureCode())
                .build();
    }

    private PaymentSessionResponse toResponse(PaymentSession session, String clientIp) {
        PaymentProvider provider = PaymentProvider.fromRequest(session.getProvider());
        String url = session.getCheckoutUrl();
        String mode;
        if (url == null || url.isBlank()) {
            LocalDateTime now = LocalDateTime.now(clock);
            if (provider == PaymentProvider.VNPAY) {
                mode = "SANDBOX";
                url = vnpayGateway.createPaymentUrl(session, clientIp, now);
            } else if (provider == PaymentProvider.MOMO && momoGateway.isConfigured()) {
                mode = "SANDBOX";
                url = momoGateway.createPaymentUrl(session, now);
            } else if (provider == PaymentProvider.ZALOPAY && zaloPayGateway.isConfigured()) {
                mode = "SANDBOX";
                url = zaloPayGateway.createPaymentUrl(session, now);
            } else {
                if (!demoEnabled) {
                    throw new IllegalStateException(provider + " sandbox is not configured.");
                }
                mode = "SIMULATOR";
                String token = demoTokenService.issue(session);
                url = demoBaseUrl + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
            }
            session.setCheckoutUrl(url);
            sessionRepository.save(session);
        } else {
            mode = url.startsWith(demoBaseUrl) ? "SIMULATOR" : "SANDBOX";
        }

        return PaymentSessionResponse.builder()
                .sessionId(session.getPublicId())
                .reservationId(session.getReservation().getId())
                .provider(session.getProvider())
                .method(session.getMethod())
                .amount(session.getExpectedAmount())
                .currency(session.getCurrency())
                .status(session.getStatus())
                .mode(mode)
                .expiresAt(session.getExpiresAt())
                .url(url)
                .reconciliationRequired(session.isReconciliationRequired())
                .build();
    }

    private void requireOwner(Reservation reservation, User currentUser) {
        if (reservation.getUser() == null || currentUser == null
                || !Objects.equals(reservation.getUser().getId(), currentUser.getId())) {
            throw new ResourceNotFoundException("Reservation not found.");
        }
    }

    private void validateReplay(PaymentSession session, Reservation reservation, PaymentProvider provider) {
        if (!Objects.equals(session.getReservation().getId(), reservation.getId())
                || !Objects.equals(session.getOwner().getId(), reservation.getUser().getId())
                || provider != PaymentProvider.fromRequest(session.getProvider())
                || session.getExpectedAmount().compareTo(reservation.getTotalAmount()) != 0) {
            throw new IllegalArgumentException("Idempotency key belongs to another payment payload.");
        }
    }

    private void validateIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key is required.");
        }
        int length = value.trim().length();
        if (length < 8 || length > 120) {
            throw new IllegalArgumentException("Idempotency-Key must contain between 8 and 120 characters.");
        }
    }

    private ProviderCallbackOutcome processVerifiedCallback(ProviderCallbackData callback) {
        Long reservationId = sessionRepository.findReservationIdByProviderReference(callback.providerReference())
                .orElse(null);
        if (reservationId == null) {
            return ProviderCallbackOutcome.NOT_FOUND;
        }

        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId).orElse(null);
        PaymentSession session = sessionRepository.findByProviderReferenceForUpdate(callback.providerReference())
                .orElse(null);
        if (reservation == null || session == null
                || !callback.provider().name().equals(session.getProvider())) {
            return ProviderCallbackOutcome.NOT_FOUND;
        }
        if (session.getExpectedAmount().compareTo(callback.amount()) != 0) {
            return ProviderCallbackOutcome.INVALID_AMOUNT;
        }

        PaymentStatus currentStatus = PaymentStatus.fromStorage(session.getStatus());
        if (!callback.successful()) {
            if (currentStatus == PaymentStatus.SUCCEEDED) {
                return ProviderCallbackOutcome.DUPLICATE;
            }
            if (currentStatus == PaymentStatus.CREATED || currentStatus == PaymentStatus.PENDING) {
                session.setStatus(PaymentStatus.FAILED.name());
                session.setFailureCode(callback.failureCode());
                sessionRepository.save(session);
            }
            return ProviderCallbackOutcome.FAILED_RECORDED;
        }
        if (currentStatus == PaymentStatus.SUCCEEDED) {
            return ProviderCallbackOutcome.DUPLICATE;
        }

        PaymentCompletionResult result = paymentService.handleSuccessfulPayment(
                reservationId,
                session.getMethod(),
                callback.providerTransactionId(),
                currentStatus);
        session.setStatus(PaymentStatus.SUCCEEDED.name());
        session.setProviderTransactionId(callback.providerTransactionId());
        session.setCompletedAt(LocalDateTime.now(clock));
        session.setFailureCode(null);
        session.setReconciliationRequired(result == PaymentCompletionResult.RECONCILIATION_REQUIRED);
        sessionRepository.save(session);
        return ProviderCallbackOutcome.CONFIRMED;
    }

    private String createProviderReference(PaymentProvider provider) {
        String random = UUID.randomUUID().toString().replace("-", "");
        if (provider == PaymentProvider.ZALOPAY) {
            return LocalDate.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh")))
                    .format(DateTimeFormatter.ofPattern("yyMMdd")) + "_" + random.substring(0, 24);
        }
        return provider.name() + "-" + random;
    }
}
