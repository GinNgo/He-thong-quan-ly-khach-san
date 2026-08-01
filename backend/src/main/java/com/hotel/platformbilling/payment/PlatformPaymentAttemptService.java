package com.hotel.platformbilling.payment;

import com.hotel.entities.User;
import com.hotel.paymentprovider.domain.FinancialStates.SubscriptionOrderState;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.platformbilling.config.PlatformPaymentConfiguration;
import com.hotel.platformbilling.config.PlatformPaymentConfigurationService;
import com.hotel.platformbilling.order.PlatformSubscriptionOrderRepository;
import com.hotel.platformbilling.order.SubscriptionOrder;
import com.hotel.services.PropertyAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class PlatformPaymentAttemptService {

    private final PlatformSubscriptionOrderRepository orderRepository;
    private final PlatformPaymentAttemptRepository attemptRepository;
    private final PlatformPaymentConfigurationService configurationService;
    private final PropertyAccessService propertyAccessService;
    private final Clock clock;

    @Autowired
    public PlatformPaymentAttemptService(
            PlatformSubscriptionOrderRepository orderRepository,
            PlatformPaymentAttemptRepository attemptRepository,
            PlatformPaymentConfigurationService configurationService,
            PropertyAccessService propertyAccessService) {
        this(orderRepository, attemptRepository, configurationService, propertyAccessService, Clock.systemUTC());
    }

    public PlatformPaymentAttemptService(
            PlatformSubscriptionOrderRepository orderRepository,
            PlatformPaymentAttemptRepository attemptRepository,
            PlatformPaymentConfigurationService configurationService,
            PropertyAccessService propertyAccessService,
            Clock clock) {
        this.orderRepository = orderRepository;
        this.attemptRepository = attemptRepository;
        this.configurationService = configurationService;
        this.propertyAccessService = propertyAccessService;
        this.clock = clock;
    }

    @Transactional
    public AttemptResponse create(CreateAttemptCommand command) {
        validate(command);
        SubscriptionOrder order = orderRepository.findByPublicIdForUpdate(normalizeText(command.orderPublicId(), 64))
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        User actor = propertyAccessService.currentUser();
        authorize(order, actor);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        expireOrderIfDue(order, now);
        validateOrderState(order);

        String provider = normalizeCode(command.provider(), 40);
        String method = normalizeCode(command.method(), 40);
        String idempotencyKey = normalizeText(command.idempotencyKey(), 160);
        String requestHash = sha256(order.getPublicId() + '|' + provider + '|' + method);
        var existing = attemptRepository.findByOrderIdAndIdempotencyKey(order.getId(), idempotencyKey);
        if (existing.isPresent()) {
            verifyReplay(existing.get(), requestHash);
            return response(existing.get(), true);
        }
        boolean activeAttemptExists = attemptRepository.findByOrderIdOrderByCreatedAtAsc(order.getId()).stream()
                .anyMatch(attempt -> !attempt.terminal());
        if (activeAttemptExists) {
            throw new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION,
                    "An active platform payment attempt already exists for this order.");
        }

        PlatformPaymentConfigurationService.ReadyConfiguration ready = configurationService.requireReady(provider);
        PlatformPaymentConfiguration configuration = ready.configuration();
        validateMethod(configuration, method);
        String publicId = UUID.randomUUID().toString();
        PlatformPaymentAttempt attempt = PlatformPaymentAttempt.create(
                publicId,
                order,
                configuration,
                method,
                order.priceMoney(),
                idempotencyKey,
                requestHash,
                order.getExpiresAt());
        attempt.markPending(publicId);
        if (order.getStatus() == SubscriptionOrderState.CREATED) {
            order.transitionTo(SubscriptionOrderState.PENDING_PAYMENT, now);
            orderRepository.save(order);
        }
        return response(attemptRepository.saveAndFlush(attempt), false);
    }

    private void expireOrderIfDue(SubscriptionOrder order, LocalDateTime now) {
        if (!order.expiredAt(now)) {
            return;
        }
        if (order.getStatus() == SubscriptionOrderState.CREATED
                || order.getStatus() == SubscriptionOrderState.PENDING_PAYMENT) {
            order.transitionTo(SubscriptionOrderState.EXPIRED, now);
            orderRepository.saveAndFlush(order);
        }
        throw new FinancialException(
                FinancialErrorCode.ATTEMPT_EXPIRED,
                "The subscription order has expired.",
                null,
                order.getStatus().name(),
                null);
    }

    private void validateOrderState(SubscriptionOrder order) {
        if (order.getStatus() != SubscriptionOrderState.CREATED
                && order.getStatus() != SubscriptionOrderState.PENDING_PAYMENT) {
            throw new FinancialException(
                    FinancialErrorCode.INVALID_STATE_TRANSITION,
                    "The subscription order cannot create a payment attempt in its current state.",
                    null,
                    order.getStatus().name(),
                    null);
        }
        if (order.getPrice().signum() <= 0 || !"VND".equals(order.getCurrency())) {
            throw new FinancialException(FinancialErrorCode.INVALID_AMOUNT);
        }
    }

    private void validateMethod(PlatformPaymentConfiguration configuration, String method) {
        if (configuration.getEnvironment()
                != com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment.SIMULATOR
                && !configuration.getProvider().equals(method)) {
            throw new FinancialException(FinancialErrorCode.POLICY_NOT_CONFIGURED,
                    "The selected payment method does not match the configured platform provider.");
        }
    }

    private void authorize(SubscriptionOrder order, User actor) {
        Long ownerId = order.getOwner() == null ? null : order.getOwner().getId();
        if (actor == null || actor.getId() == null || !actor.getId().equals(ownerId)) {
            throw new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private void verifyReplay(PlatformPaymentAttempt attempt, String requestHash) {
        if (!MessageDigest.isEqual(
                attempt.getRequestHash().getBytes(StandardCharsets.UTF_8),
                requestHash.getBytes(StandardCharsets.UTF_8))) {
            throw new FinancialException(FinancialErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
    }

    private AttemptResponse response(PlatformPaymentAttempt attempt, boolean replayed) {
        PlatformPaymentConfiguration configuration = attempt.getConfiguration();
        return new AttemptResponse(
                attempt.getId(),
                attempt.getPublicId(),
                attempt.getOrder().getPublicId(),
                attempt.getStatus(),
                attempt.getProvider(),
                attempt.getMethod(),
                attempt.getEnvironment(),
                attempt.getExpectedAmount(),
                attempt.getCurrency(),
                attempt.getProviderOrderReference(),
                attempt.getExpiresAt(),
                configuration.getMerchantReferenceMasked(),
                replayed);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private void validate(CreateAttemptCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Platform payment attempt command is required.");
        }
    }

    private String normalizeCode(String value, int maxLength) {
        return normalizeText(value, maxLength).toUpperCase(Locale.ROOT);
    }

    private String normalizeText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Required platform payment value is missing.");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("Platform payment value is too long.");
        }
        return normalized;
    }

    public record CreateAttemptCommand(
            String orderPublicId,
            String provider,
            String method,
            String idempotencyKey) {
    }

    public record AttemptResponse(
            Long id,
            String publicId,
            String orderPublicId,
            PlatformPaymentAttempt.Status status,
            String provider,
            String method,
            com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment environment,
            BigDecimal expectedAmount,
            String currency,
            String providerOrderReference,
            LocalDateTime expiresAt,
            String merchantReferenceMasked,
            boolean replayed) {
    }
}
