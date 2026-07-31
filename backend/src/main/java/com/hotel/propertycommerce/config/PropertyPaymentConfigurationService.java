package com.hotel.propertycommerce.config;

import com.hotel.entities.Hotel;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.ProviderCredentials;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.services.PropertyAccessService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PropertyPaymentConfigurationService {
    private static final Set<String> ALLOWED_METHODS = Set.of("MANUAL_TRANSFER", "QR_TRANSFER", "VNPAY", "MOMO", "ZALOPAY", "CASH", "CARD_TERMINAL", "OTHER");
    private static final Set<String> BANK_METHODS = Set.of("MANUAL_TRANSFER", "QR_TRANSFER");
    private static final Set<String> PROVIDER_METHODS = Set.of("VNPAY", "MOMO", "ZALOPAY");
    private static final Set<String> LOCAL_METHODS = Set.of("CASH", "CARD_TERMINAL");

    private final PropertyPaymentConfigurationRepository repository;
    private final PropertyAccessService propertyAccessService;
    private final PaymentEnvironmentGuard environmentGuard;
    private final FinancialAuditService auditService;
    private final String encryptionKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public PropertyPaymentConfigurationService(PropertyPaymentConfigurationRepository repository,
                                               PropertyAccessService propertyAccessService,
                                               PaymentEnvironmentGuard environmentGuard,
                                               FinancialAuditService auditService,
                                               @Value("${payment.property.encryption-key:}") String encryptionKey) {
        this.repository = repository; this.propertyAccessService = propertyAccessService; this.environmentGuard = environmentGuard;
        this.auditService = auditService; this.encryptionKey = encryptionKey;
    }

    @Transactional(readOnly = true)
    public ConfigurationResponse get(Long propertyId) {
        Hotel hotel = propertyAccessService.requireManagedHotel(propertyId);
        return repository.findByHotelId(hotel.getId())
                .map(configuration -> response(configuration, readiness(configuration)))
                .orElseGet(() -> response(new PropertyPaymentConfiguration(hotel),
                        new ReadinessResponse(false, "SIMULATOR", List.of("configuration_not_saved"), List.of())));
    }

    @Transactional
    public ConfigurationResponse update(Long propertyId, UpdateRequest request) {
        Hotel hotel = propertyAccessService.requireManagedHotel(propertyId);
        PropertyPaymentConfiguration configuration = repository.findByHotelId(hotel.getId())
                .orElseGet(() -> new PropertyPaymentConfiguration(hotel));
        apply(configuration, request);
        ReadinessResponse readiness = readiness(configuration);
        if (configuration.isEnabled() && !readiness.ready()) {
            FinancialErrorCode code = readiness.blockers().stream().anyMatch(item -> item.endsWith("production_not_approved"))
                    ? FinancialErrorCode.PRODUCTION_NOT_APPROVED : FinancialErrorCode.PAYMENT_ENVIRONMENT_DISABLED;
            throw new FinancialException(code,
                    "Payment configuration is incomplete or disabled.",
                    readiness.blockers().stream().collect(java.util.stream.Collectors.toMap(item -> item, item -> item, (a, b) -> a)),
                    configuration.getEnvironment(), null);
        }
        configuration = repository.saveAndFlush(configuration);
        Long actorId = propertyAccessService.currentUser().getId();
        auditService.append(new FinancialAuditService.AuditCommand(
                "PROPERTY_COMMERCE", hotel.getId(), "PAYMENT_CONFIGURATION", String.valueOf(configuration.getId()),
                "USER", actorId, "MANAGEMENT_API", null, configuration.isEnabled() ? "ENABLED" : "DISABLED",
                "Configuration updated", "CONFIG:" + hotel.getId() + ":" + configuration.getVersion(), null,
                UUID.randomUUID().toString(),
                Map.of("environment", configuration.getEnvironment(), "account",
                        configuration.getAccountNumberMasked() == null ? "not-configured" : configuration.getAccountNumberMasked())));
        return response(configuration, readiness);
    }

    @Transactional(readOnly = true)
    public ReadinessResponse validate(Long propertyId, UpdateRequest request) {
        Hotel hotel = propertyAccessService.requireManagedHotel(propertyId);
        PropertyPaymentConfiguration candidate = repository.findByHotelId(hotel.getId())
                .orElseGet(() -> new PropertyPaymentConfiguration(hotel));
        if (request != null) apply(candidate, request);
        return readiness(candidate);
    }

    private void apply(PropertyPaymentConfiguration configuration, UpdateRequest request) {
        if (request == null) throw new IllegalArgumentException("Payment configuration is required");
        String environment = normalizeEnum(request.environment(), "SIMULATOR");
        PaymentEnvironment.valueOf(environment);
        String policy = normalizeEnum(request.depositPolicyType(), "NONE");
        BigDecimal depositValue = validateDeposit(policy, request.depositValue());
        int expiry = request.paymentExpiryMinutes() == null ? 15 : request.paymentExpiryMinutes();
        if (expiry < 1 || expiry > 10080) throw new IllegalArgumentException("Payment expiry must be between 1 and 10080 minutes");
        List<PropertyPaymentConfigurationMethod> methods = normalizeMethods(request.methods());
        String accountNumber = normalizeAccount(request.accountNumber());
        String encrypted = configuration.getAccountNumberEncrypted();
        String masked = configuration.getAccountNumberMasked();
        if (accountNumber != null) { encrypted = encrypt(accountNumber); masked = mask(accountNumber); }
        configuration.apply(Boolean.TRUE.equals(request.enabled()), environment, trim(request.bankName()), trim(request.bankCode()),
                trim(request.accountName()), encrypted, masked, policy, depositValue, expiry, trim(request.transferTemplate()),
                trim(request.qrProvider()), trim(request.instructionsVi()), trim(request.instructionsEn()));
        configuration.replaceMethods(methods);
    }

    private BigDecimal validateDeposit(String policy, BigDecimal value) {
        return switch (policy) {
            case "NONE" -> null;
            case "FIXED" -> {
                if (value == null || value.signum() <= 0) throw new FinancialException(FinancialErrorCode.INVALID_AMOUNT, "Fixed deposit must be positive.");
                yield VndMoney.of(value).amount();
            }
            case "PERCENTAGE" -> {
                if (value == null || value.scale() > 0 || value.compareTo(BigDecimal.ONE) < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0)
                    throw new FinancialException(FinancialErrorCode.INVALID_AMOUNT, "Deposit percentage must be an integer from 1 to 100.");
                yield value;
            }
            default -> throw new IllegalArgumentException("Unsupported deposit policy: " + policy);
        };
    }

    private List<PropertyPaymentConfigurationMethod> normalizeMethods(List<MethodRequest> requests) {
        if (requests == null) return List.of();
        Set<String> seen = new HashSet<>(); List<PropertyPaymentConfigurationMethod> methods = new ArrayList<>();
        for (MethodRequest request : requests) {
            String method = normalizeEnum(request.method(), null);
            if (!ALLOWED_METHODS.contains(method)) throw new IllegalArgumentException("Unsupported payment method: " + method);
            if (!seen.add(method)) throw new IllegalArgumentException("Duplicate payment method: " + method);
            methods.add(new PropertyPaymentConfigurationMethod(method, request.enabled(), trim(request.provider()), maskReference(request.merchantReference())));
        }
        return methods;
    }

    private ReadinessResponse readiness(PropertyPaymentConfiguration configuration) {
        List<String> blockers = new ArrayList<>(); List<MethodReadiness> methods = new ArrayList<>();
        List<PropertyPaymentConfigurationMethod> enabledMethods = configuration.getMethods().stream().filter(PropertyPaymentConfigurationMethod::isEnabled).toList();
        if (enabledMethods.isEmpty()) blockers.add("enabled_method_required");
        if (configuration.getInstructionsVi() == null || configuration.getInstructionsEn() == null) blockers.add("bilingual_instructions_required");
        if (configuration.getTransferTemplate() == null || !configuration.getTransferTemplate().contains("{paymentCode}")) blockers.add("payment_code_placeholder_required");
        for (PropertyPaymentConfigurationMethod method : enabledMethods) {
            List<String> methodBlockers = new ArrayList<>();
            if (BANK_METHODS.contains(method.getMethod()) && (configuration.getBankName() == null || configuration.getBankCode() == null
                    || configuration.getAccountName() == null || configuration.getAccountNumberMasked() == null)) methodBlockers.add("bank_receiver_incomplete");
            if (PROVIDER_METHODS.contains(method.getMethod()) && !"SIMULATOR".equals(configuration.getEnvironment()) && method.getMerchantReferenceMasked() == null) methodBlockers.add("merchant_reference_required");
            try {
                environmentGuard.validate(PaymentEnvironment.valueOf(configuration.getEnvironment()), method.getProvider() == null ? method.getMethod() : method.getProvider(), credentials(configuration, method));
            } catch (FinancialException exception) {
                if (exception.fieldErrors().isEmpty()) {
                    methodBlockers.add(exception.code().name().toLowerCase(Locale.ROOT));
                } else {
                    methodBlockers.addAll(exception.fieldErrors().keySet());
                }
            }
            methods.add(new MethodReadiness(method.getMethod(), method.getProvider(), methodBlockers.isEmpty(), methodBlockers));
            blockers.addAll(methodBlockers.stream().map(item -> method.getMethod().toLowerCase(Locale.ROOT) + "." + item).toList());
        }
        return new ReadinessResponse(blockers.isEmpty(), configuration.getEnvironment(), List.copyOf(blockers), List.copyOf(methods));
    }

    private ProviderCredentials credentials(PropertyPaymentConfiguration configuration, PropertyPaymentConfigurationMethod method) {
        if ("SIMULATOR".equals(configuration.getEnvironment())) return null;
        if (BANK_METHODS.contains(method.getMethod())) return new ProviderCredentials(configuration.getBankCode(), Map.of("receiver", "configured"), URI.create("https://bank-transfer.invalid"));
        if (LOCAL_METHODS.contains(method.getMethod())) {
            return new ProviderCredentials(method.getMethod(), Map.of("local", "configured"), URI.create("https://local-payment.invalid"));
        }
        // Provider secrets are not stored in this aggregate; readiness stays blocked until a
        // property-scoped secret reference or vault-backed adapter is implemented.
        return null;
    }

    private ConfigurationResponse response(PropertyPaymentConfiguration configuration, ReadinessResponse readiness) {
        return new ConfigurationResponse(configuration.getId(), configuration.getHotel().getId(), configuration.isEnabled(), configuration.getEnvironment(),
                configuration.getBankName(), configuration.getBankCode(), configuration.getAccountName(), configuration.getAccountNumberMasked(),
                configuration.getDepositPolicyType(), configuration.getDepositValue(), configuration.getPaymentExpiryMinutes(), configuration.getTransferTemplate(),
                configuration.getQrProvider(), configuration.getInstructionsVi(), configuration.getInstructionsEn(), configuration.getVersion(),
                configuration.getMethods().stream().map(method -> new MethodResponse(method.getMethod(), method.isEnabled(), method.getProvider(), method.getMerchantReferenceMasked())).toList(), readiness);
    }

    private String normalizeAccount(String value) { if (value == null || value.isBlank()) return null; String normalized = value.replaceAll("\\s+", "").toUpperCase(Locale.ROOT); if (!normalized.matches("[0-9A-Z]{6,34}")) throw new IllegalArgumentException("Bank account number is invalid"); return normalized; }
    private String encrypt(String value) {
        if (encryptionKey == null || encryptionKey.isBlank()) throw new FinancialException(FinancialErrorCode.PAYMENT_ENVIRONMENT_DISABLED, "Property payment encryption key is not configured.");
        try { byte[] key = MessageDigest.getInstance("SHA-256").digest(encryptionKey.getBytes(StandardCharsets.UTF_8)); byte[] iv = new byte[12]; secureRandom.nextBytes(iv); Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv)); byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)); return "v1:" + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array()); }
        catch (Exception exception) { throw new IllegalStateException("Unable to protect the property bank account", exception); }
    }
    private String mask(String value) { return value.length() <= 4 ? "****" : "****" + value.substring(value.length() - 4); }
    private String maskReference(String value) { String normalized = trim(value); return normalized == null ? null : mask(normalized); }
    private String normalizeEnum(String value, String fallback) { String normalized = trim(value); if (normalized == null) { if (fallback == null) throw new IllegalArgumentException("Required configuration value is missing"); return fallback; } return normalized.toUpperCase(Locale.ROOT); }
    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public record UpdateRequest(Boolean enabled, String environment, List<MethodRequest> methods, String bankName, String bankCode, String accountName, String accountNumber, String depositPolicyType, BigDecimal depositValue, Integer paymentExpiryMinutes, String transferTemplate, String qrProvider, String instructionsVi, String instructionsEn) { }
    public record MethodRequest(String method, boolean enabled, String provider, String merchantReference) { }
    public record ConfigurationResponse(Long id, Long propertyId, boolean enabled, String environment, String bankName, String bankCode, String accountName, String accountNumberMasked, String depositPolicyType, BigDecimal depositValue, int paymentExpiryMinutes, String transferTemplate, String qrProvider, String instructionsVi, String instructionsEn, long version, List<MethodResponse> methods, ReadinessResponse readiness) { }
    public record MethodResponse(String method, boolean enabled, String provider, String merchantReferenceMasked) { }
    public record ReadinessResponse(boolean ready, String environment, List<String> blockers, List<MethodReadiness> methods) { }
    public record MethodReadiness(String method, String provider, boolean ready, List<String> blockers) { }
}
