package com.hotel.propertycommerce.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.domain.lifecycle.ReservationStatus;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.ProviderCredentials;
import com.hotel.paymentprovider.domain.FinancialStates.PaymentState;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.paymentprovider.idempotency.FinancialIdempotencyService;
import com.hotel.propertycommerce.booking.BookingFinancialSummaryService;
import com.hotel.propertycommerce.config.PropertyPaymentConfiguration;
import com.hotel.propertycommerce.config.PropertyPaymentConfigurationMethod;
import com.hotel.propertycommerce.config.PropertyPaymentConfigurationRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.services.PropertyAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PropertyPaymentAttemptService {

    private static final Set<String> BANK_METHODS = Set.of("MANUAL_TRANSFER", "QR_TRANSFER");
    private static final Set<String> MANUAL_METHODS = Set.of(
            "MANUAL_TRANSFER", "QR_TRANSFER", "CASH", "CARD_TERMINAL", "OTHER");

    private final ReservationRepository reservationRepository;
    private final PropertyPaymentConfigurationRepository configurationRepository;
    private final PropertyPaymentAttemptRepository attemptRepository;
    private final BookingFinancialSummaryService summaryService;
    private final PropertyAccessService propertyAccessService;
    private final FinancialIdempotencyService idempotencyService;
    private final PaymentEnvironmentGuard environmentGuard;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public PropertyPaymentAttemptService(
            ReservationRepository reservationRepository,
            PropertyPaymentConfigurationRepository configurationRepository,
            PropertyPaymentAttemptRepository attemptRepository,
            BookingFinancialSummaryService summaryService,
            PropertyAccessService propertyAccessService,
            FinancialIdempotencyService idempotencyService,
            PaymentEnvironmentGuard environmentGuard,
            ObjectMapper objectMapper) {
        this(
                reservationRepository,
                configurationRepository,
                attemptRepository,
                summaryService,
                propertyAccessService,
                idempotencyService,
                environmentGuard,
                objectMapper,
                Clock.systemUTC());
    }

    PropertyPaymentAttemptService(
            ReservationRepository reservationRepository,
            PropertyPaymentConfigurationRepository configurationRepository,
            PropertyPaymentAttemptRepository attemptRepository,
            BookingFinancialSummaryService summaryService,
            PropertyAccessService propertyAccessService,
            FinancialIdempotencyService idempotencyService,
            PaymentEnvironmentGuard environmentGuard,
            ObjectMapper objectMapper,
            Clock clock) {
        this.reservationRepository = reservationRepository;
        this.configurationRepository = configurationRepository;
        this.attemptRepository = attemptRepository;
        this.summaryService = summaryService;
        this.propertyAccessService = propertyAccessService;
        this.idempotencyService = idempotencyService;
        this.environmentGuard = environmentGuard;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public AttemptResponse create(CreateCommand command) {
        validate(command);
        Reservation reservation = reservationRepository.findByIdForUpdate(command.reservationId())
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        User actor = propertyAccessService.currentUser();
        authorize(reservation, actor);
        validateReservationState(reservation, command.purpose());

        String method = normalizeCode(command.method(), "method");
        String idempotencyKey = normalizeIdempotencyKey(command.idempotencyKey());
        AttemptRequestIdentity payload = new AttemptRequestIdentity(reservation.getId(), command.purpose(), method);
        FinancialIdempotencyService.BeginResult begin = idempotencyService.begin(
                new FinancialIdempotencyService.BeginCommand(
                        "PROPERTY_COMMERCE",
                        "CREATE_PAYMENT_ATTEMPT",
                        "HOTEL:" + reservation.getHotel().getId(),
                        idempotencyKey,
                        payload,
                        reservation.getHotel().getId(),
                        actor.getId(),
                        command.correlationId()));

        if (begin instanceof FinancialIdempotencyService.Replay replay) {
            return response(findReplay(replay.responseBody()), true);
        }
        if (begin instanceof FinancialIdempotencyService.InProgress) {
            return attemptRepository.findByHotelIdAndIdempotencyKeyForUpdate(
                            reservation.getHotel().getId(), idempotencyKey)
                    .map(attempt -> response(attempt, true))
                    .orElseThrow(() -> new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION));
        }
        if (begin instanceof FinancialIdempotencyService.RetryableFailure) {
            throw new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION);
        }

        FinancialIdempotencyService.Acquired acquired = (FinancialIdempotencyService.Acquired) begin;
        PropertyPaymentConfiguration configuration = requireConfiguration(reservation.getHotel().getId());
        PropertyPaymentConfigurationMethod selectedMethod = requireMethod(configuration, method);
        validateEnvironment(configuration, selectedMethod, method);
        BookingFinancialSummaryService.Summary summary = summaryService.calculate(reservation.getId());
        VndMoney expectedAmount = expectedAmount(command.purpose(), summary);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        LocalDateTime expiresAt = now.plusMinutes(configuration.getPaymentExpiryMinutes());
        String publicId = UUID.randomUUID().toString();
        String transferContent = transferContent(configuration, method, reservation.getId(), publicId);
        ReceiverSnapshot receiver = receiverSnapshot(configuration, selectedMethod);
        String receiverJson = writeReceiver(receiver);
        String provider = selectedMethod.getProvider() == null || selectedMethod.getProvider().isBlank()
                ? method
                : normalizeCode(selectedMethod.getProvider(), "provider");

        PropertyPaymentAttempt attempt = PropertyPaymentAttempt.create(
                publicId,
                reservation.getHotel(),
                reservation,
                configuration,
                reservation.getUser(),
                command.purpose(),
                method,
                provider,
                PaymentEnvironment.valueOf(configuration.getEnvironment()),
                expectedAmount,
                transferContent,
                receiverJson,
                idempotencyKey,
                acquired.record().getRequestHash(),
                expiresAt);
        attempt.transitionTo(initialState(method), now, null, null);
        attempt = attemptRepository.saveAndFlush(attempt);
        idempotencyService.complete(acquired.recordId(), 201, attempt.getPublicId());
        return response(attempt, false);
    }

    private void authorize(Reservation reservation, User actor) {
        boolean reservationOwner = reservation.getUser() != null
                && reservation.getUser().getId() != null
                && reservation.getUser().getId().equals(actor.getId());
        if (reservationOwner || propertyAccessService.isSystemAdministrator()) {
            return;
        }
        if (!propertyAccessService.accessibleHotelIds().contains(reservation.getHotel().getId())) {
            throw new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private void validateReservationState(Reservation reservation, PropertyPaymentAttempt.Purpose purpose) {
        ReservationStatus status = ReservationStatus.fromStorage(reservation.getStatus());
        boolean allowed = switch (purpose) {
            case DEPOSIT -> status == ReservationStatus.PENDING_PAYMENT || status == ReservationStatus.CONFIRMED;
            case BALANCE -> status == ReservationStatus.PENDING_PAYMENT
                    || status == ReservationStatus.CONFIRMED
                    || status == ReservationStatus.CHECKED_IN;
            default -> false;
        };
        if (!allowed) {
            throw new FinancialException(
                    FinancialErrorCode.INVALID_STATE_TRANSITION,
                    "The reservation cannot create this payment attempt in its current state.",
                    null,
                    status.name(),
                    null);
        }
    }

    private PropertyPaymentConfiguration requireConfiguration(Long hotelId) {
        PropertyPaymentConfiguration configuration = configurationRepository.findByHotelId(hotelId)
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.POLICY_NOT_CONFIGURED));
        if (!configuration.isEnabled()) {
            throw new FinancialException(FinancialErrorCode.PAYMENT_ENVIRONMENT_DISABLED);
        }
        if (configuration.getPaymentExpiryMinutes() < 1 || configuration.getPaymentExpiryMinutes() > 10080) {
            throw new FinancialException(FinancialErrorCode.POLICY_NOT_CONFIGURED,
                    "Payment expiry policy is invalid.");
        }
        if (configuration.getInstructionsVi() == null || configuration.getInstructionsVi().isBlank()
                || configuration.getInstructionsEn() == null || configuration.getInstructionsEn().isBlank()) {
            throw new FinancialException(FinancialErrorCode.POLICY_NOT_CONFIGURED,
                    "Bilingual payment instructions are not configured.");
        }
        PaymentEnvironment environment;
        try {
            environment = PaymentEnvironment.valueOf(configuration.getEnvironment());
        } catch (RuntimeException exception) {
            throw new FinancialException(FinancialErrorCode.PAYMENT_ENVIRONMENT_DISABLED,
                    "Payment environment is invalid.");
        }
        if (environment == PaymentEnvironment.PRODUCTION && configuration.getProductionApprovedAt() == null) {
            throw new FinancialException(FinancialErrorCode.PRODUCTION_NOT_APPROVED);
        }
        return configuration;
    }

    private PropertyPaymentConfigurationMethod requireMethod(
            PropertyPaymentConfiguration configuration,
            String requestedMethod) {
        PropertyPaymentConfigurationMethod method = configuration.getMethods().stream()
                .filter(item -> requestedMethod.equals(item.getMethod()))
                .findFirst()
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.POLICY_NOT_CONFIGURED,
                        "The requested payment method is not configured."));
        if (!method.isEnabled()) {
            throw new FinancialException(FinancialErrorCode.PAYMENT_ENVIRONMENT_DISABLED,
                    "The requested payment method is disabled.");
        }
        if (BANK_METHODS.contains(requestedMethod)) {
            requireText(configuration.getBankName(), "bankName");
            requireText(configuration.getBankCode(), "bankCode");
            requireText(configuration.getAccountName(), "accountName");
            requireText(configuration.getAccountNumberMasked(), "accountNumberMasked");
            if (configuration.getTransferTemplate() == null
                    || !configuration.getTransferTemplate().contains("{paymentCode}")) {
                throw new FinancialException(FinancialErrorCode.POLICY_NOT_CONFIGURED,
                        "Transfer template must contain {paymentCode}.");
            }
        }
        if (PaymentEnvironment.valueOf(configuration.getEnvironment()) != PaymentEnvironment.SIMULATOR
                && !BANK_METHODS.contains(requestedMethod)
                && (method.getMerchantReferenceMasked() == null || method.getMerchantReferenceMasked().isBlank())) {
            throw new FinancialException(FinancialErrorCode.PAYMENT_ENVIRONMENT_DISABLED,
                    "Provider merchant identity is not configured.");
        }
        return method;
    }

    private VndMoney expectedAmount(
            PropertyPaymentAttempt.Purpose purpose,
            BookingFinancialSummaryService.Summary summary) {
        BigDecimal netPaid = summary.successfulPayments().amount().subtract(summary.successfulRefunds().amount());
        BigDecimal amount = switch (purpose) {
            case DEPOSIT -> summary.depositRequired().amount().subtract(netPaid);
            case BALANCE -> summary.remainingBalance();
            default -> throw new FinancialException(FinancialErrorCode.POLICY_NOT_CONFIGURED,
                    "This payment purpose requires an authoritative folio source that is not configured yet.");
        };
        if (amount.signum() <= 0) {
            FinancialErrorCode code = amount.signum() < 0
                    ? FinancialErrorCode.OVERPAYMENT_REQUIRES_RESOLUTION
                    : FinancialErrorCode.INVALID_STATE_TRANSITION;
            throw new FinancialException(code, "There is no positive amount payable for this purpose.");
        }
        return VndMoney.of(amount);
    }

    private void validateEnvironment(
            PropertyPaymentConfiguration configuration,
            PropertyPaymentConfigurationMethod method,
            String requestedMethod) {
        PaymentEnvironment environment = PaymentEnvironment.valueOf(configuration.getEnvironment());
        String provider = method.getProvider() == null || method.getProvider().isBlank()
                ? requestedMethod
                : normalizeCode(method.getProvider(), "provider");
        ProviderCredentials credentials = null;
        if (environment != PaymentEnvironment.SIMULATOR) {
            if (BANK_METHODS.contains(requestedMethod)) {
                credentials = new ProviderCredentials(
                        configuration.getBankCode(),
                        Map.of("receiver", "configured"),
                        URI.create("https://bank-transfer.invalid"));
            } else if (Set.of("CASH", "CARD_TERMINAL").contains(requestedMethod)) {
                credentials = new ProviderCredentials(
                        requestedMethod,
                        Map.of("local", "configured"),
                        URI.create("https://local-payment.invalid"));
            }
        }
        environmentGuard.validate(environment, provider, credentials);
    }

    private String transferContent(
            PropertyPaymentConfiguration configuration,
            String method,
            Long reservationId,
            String publicId) {
        if (!BANK_METHODS.contains(method)) {
            return null;
        }
        String code = "LS" + reservationId + "-" + publicId.substring(0, 8).toUpperCase(Locale.ROOT);
        String content = configuration.getTransferTemplate().replace("{paymentCode}", code).trim();
        if (content.isBlank() || content.length() > 160) {
            throw new FinancialException(FinancialErrorCode.POLICY_NOT_CONFIGURED,
                    "Generated transfer content is invalid.");
        }
        return content;
    }

    private ReceiverSnapshot receiverSnapshot(
            PropertyPaymentConfiguration configuration,
            PropertyPaymentConfigurationMethod method) {
        return new ReceiverSnapshot(
                configuration.getBankName(),
                configuration.getBankCode(),
                configuration.getAccountName(),
                configuration.getAccountNumberMasked(),
                configuration.getQrProvider(),
                method.getMerchantReferenceMasked(),
                configuration.getInstructionsVi(),
                configuration.getInstructionsEn());
    }

    private String writeReceiver(ReceiverSnapshot receiver) {
        try {
            return objectMapper.writeValueAsString(receiver);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to snapshot the payment receiver.", exception);
        }
    }

    private AttemptResponse response(PropertyPaymentAttempt attempt, boolean replayed) {
        try {
            ReceiverSnapshot receiver = objectMapper.readValue(
                    attempt.getReceiverSnapshotJson(), ReceiverSnapshot.class);
            return new AttemptResponse(
                    attempt.getId(),
                    attempt.getPublicId(),
                    attempt.getReservation().getId(),
                    attempt.getPurpose(),
                    attempt.getStatus(),
                    attempt.getEnvironment(),
                    attempt.getExpectedAmount(),
                    attempt.getCurrency(),
                    attempt.getExpiresAt(),
                    attempt.getMethod(),
                    attempt.getProvider(),
                    receiver,
                    attempt.getReceiverSnapshotJson(),
                    attempt.getUniqueTransferContent(),
                    replayed);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored payment receiver snapshot is invalid.", exception);
        }
    }

    private PropertyPaymentAttempt findReplay(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            throw new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION);
        }
        return attemptRepository.findByPublicId(publicId)
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
    }

    private PaymentState initialState(String method) {
        return MANUAL_METHODS.contains(method) ? PaymentState.PENDING_VERIFICATION : PaymentState.PENDING;
    }

    private void validate(CreateCommand command) {
        if (command == null || command.reservationId() == null || command.purpose() == null) {
            throw new IllegalArgumentException("Reservation and payment purpose are required.");
        }
    }

    private String normalizeIdempotencyKey(String value) {
        String normalized = requireRequestText(value, "idempotencyKey");
        if (normalized.length() > 160) {
            throw new IllegalArgumentException("Idempotency key is too long.");
        }
        return normalized;
    }

    private String normalizeCode(String value, String field) {
        return requireRequestText(value, field).toUpperCase(Locale.ROOT);
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new FinancialException(FinancialErrorCode.POLICY_NOT_CONFIGURED,
                    field + " is not configured.");
        }
        return value.trim();
    }

    private String requireRequestText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return value.trim();
    }

    private record AttemptRequestIdentity(
            Long reservationId,
            PropertyPaymentAttempt.Purpose purpose,
            String method) {
    }

    public record CreateCommand(
            Long reservationId,
            PropertyPaymentAttempt.Purpose purpose,
            String method,
            String idempotencyKey,
            String correlationId) {
    }

    public record ReceiverSnapshot(
            String bankName,
            String bankCode,
            String accountName,
            String accountNumberMasked,
            String qrProvider,
            String merchantReferenceMasked,
            String instructionsVi,
            String instructionsEn) {
    }

    public record AttemptResponse(
            Long id,
            String publicId,
            Long reservationId,
            PropertyPaymentAttempt.Purpose purpose,
            PaymentState status,
            PaymentEnvironment environment,
            BigDecimal expectedAmount,
            String currency,
            LocalDateTime expiresAt,
            String method,
            String provider,
            ReceiverSnapshot receiver,
            String receiverSnapshotJson,
            String uniqueTransferContent,
            boolean replayed) {
    }
}
