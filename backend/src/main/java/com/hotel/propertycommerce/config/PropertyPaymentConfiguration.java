package com.hotel.propertycommerce.config;

import com.hotel.entities.Hotel;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "property_payment_configurations")
@FilterDef(name = "propertyPaymentConfigurationTenantFilter", parameters = @ParamDef(name = "hotelId", type = Long.class))
@Filter(name = "propertyPaymentConfigurationTenantFilter", condition = "hotel_id = :hotelId")
public class PropertyPaymentConfiguration {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "hotel_id", nullable = false) private Hotel hotel;
    @Column(nullable = false) private boolean enabled;
    @Column(nullable = false, length = 20) private String environment = "SIMULATOR";
    @Column(name = "bank_name", length = 160) private String bankName;
    @Column(name = "bank_code", length = 40) private String bankCode;
    @Column(name = "account_name", length = 160) private String accountName;
    @Column(name = "account_number_encrypted", length = 1000) private String accountNumberEncrypted;
    @Column(name = "account_number_masked", length = 80) private String accountNumberMasked;
    @Column(name = "deposit_policy_type", nullable = false, length = 20) private String depositPolicyType = "NONE";
    @Column(name = "deposit_value", precision = 19, scale = 0) private BigDecimal depositValue;
    @Column(name = "payment_expiry_minutes", nullable = false) private int paymentExpiryMinutes = 15;
    @Column(name = "transfer_template", length = 500) private String transferTemplate;
    @Column(name = "qr_provider", length = 40) private String qrProvider;
    @Column(name = "instructions_vi", length = 2000) private String instructionsVi;
    @Column(name = "instructions_en", length = 2000) private String instructionsEn;
    @Column(name = "production_approved_at") private LocalDateTime productionApprovedAt;
    @Column(name = "production_approved_by") private Long productionApprovedBy;
    @Version @Column(nullable = false) private long version;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @OneToMany(mappedBy = "configuration", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PropertyPaymentConfigurationMethod> methods = new ArrayList<>();

    protected PropertyPaymentConfiguration() { }
    public PropertyPaymentConfiguration(Hotel hotel) { this.hotel = hotel; }

    void apply(boolean enabled, String environment, String bankName, String bankCode, String accountName,
               String accountNumberEncrypted, String accountNumberMasked, String depositPolicyType,
               BigDecimal depositValue, int paymentExpiryMinutes, String transferTemplate,
               String qrProvider, String instructionsVi, String instructionsEn) {
        this.enabled = enabled; this.environment = environment; this.bankName = bankName; this.bankCode = bankCode;
        this.accountName = accountName; this.accountNumberEncrypted = accountNumberEncrypted;
        this.accountNumberMasked = accountNumberMasked; this.depositPolicyType = depositPolicyType;
        this.depositValue = depositValue; this.paymentExpiryMinutes = paymentExpiryMinutes;
        this.transferTemplate = transferTemplate; this.qrProvider = qrProvider;
        this.instructionsVi = instructionsVi; this.instructionsEn = instructionsEn;
    }

    void replaceMethods(List<PropertyPaymentConfigurationMethod> replacements) {
        methods.clear();
        replacements.forEach(method -> { method.attach(this, hotel); methods.add(method); });
    }

    @PrePersist void created() { LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void updated() { updatedAt = LocalDateTime.now(); }
}
