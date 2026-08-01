package com.hotel.platformbilling.config;

import com.hotel.entities.User;
import com.hotel.paymentprovider.config.PaymentEnvironmentGuard.PaymentEnvironment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Objects;

@Getter
@Entity
@Table(name = "platform_payment_configurations", uniqueConstraints = @UniqueConstraint(
        name = "UQ_platform_config_provider_environment",
        columnNames = {"provider", "environment"}))
public class PlatformPaymentConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentEnvironment environment;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "merchant_reference_masked", length = 160, columnDefinition = "nvarchar(160)")
    private String merchantReferenceMasked;

    @Column(name = "secret_reference", length = 500, columnDefinition = "nvarchar(500)")
    private String secretReference;

    @Column(name = "bank_name", length = 160, columnDefinition = "nvarchar(160)")
    private String bankName;

    @Column(name = "bank_account_masked", length = 80, columnDefinition = "nvarchar(80)")
    private String bankAccountMasked;

    @Column(name = "callback_url", length = 1000, columnDefinition = "nvarchar(1000)")
    private String callbackUrl;

    @Column(name = "production_approved_at")
    private LocalDateTime productionApprovedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "production_approved_by")
    private User productionApprovedBy;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected PlatformPaymentConfiguration() {
    }

    public static PlatformPaymentConfiguration create(String provider, PaymentEnvironment environment) {
        PlatformPaymentConfiguration configuration = new PlatformPaymentConfiguration();
        configuration.provider = normalizeCode(provider, "provider", 40);
        configuration.environment = Objects.requireNonNull(environment, "environment must not be null");
        configuration.enabled = false;
        return configuration;
    }

    public void configure(
            boolean enabled,
            String merchantReferenceMasked,
            String secretReference,
            String bankName,
            String bankAccountMasked,
            String callbackUrl) {
        this.enabled = enabled;
        this.merchantReferenceMasked = normalizeOptional(merchantReferenceMasked, 160);
        this.secretReference = normalizeOptional(secretReference, 500);
        this.bankName = normalizeOptional(bankName, 160);
        this.bankAccountMasked = normalizeOptional(bankAccountMasked, 80);
        this.callbackUrl = normalizeOptional(callbackUrl, 1000);
        validate();
    }

    public void recordProductionApproval(User approver, LocalDateTime approvedAt) {
        if (environment != PaymentEnvironment.PRODUCTION) {
            throw new IllegalStateException("Only production configuration accepts production approval evidence.");
        }
        productionApprovedBy = Objects.requireNonNull(approver, "approver must not be null");
        productionApprovedAt = Objects.requireNonNull(approvedAt, "approvedAt must not be null");
    }

    public boolean productionApproved() {
        return productionApprovedAt != null && productionApprovedBy != null;
    }

    @PrePersist
    void created() {
        validate();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void updated() {
        validate();
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    private void validate() {
        if (environment == PaymentEnvironment.PRODUCTION && !productionApproved()) {
            throw new IllegalStateException("Production platform payment configuration requires approval evidence.");
        }
        if (enabled && environment != PaymentEnvironment.SIMULATOR
                && (secretReference == null || merchantReferenceMasked == null)) {
            throw new IllegalStateException("Enabled sandbox or production configuration requires masked merchant and secret references.");
        }
    }

    private static String normalizeCode(String value, String field, int maxLength) {
        String normalized = requireText(value, field, maxLength);
        return normalized.toUpperCase(Locale.ROOT);
    }

    private static String normalizeOptional(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("value is too long.");
        }
        return normalized;
    }

    private static String requireText(String value, String field, int maxLength) {
        String normalized = normalizeOptional(value, maxLength);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " must not be blank.");
        }
        return normalized;
    }
}
