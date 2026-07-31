package com.hotel.propertycommerce.config;

import com.hotel.entities.Hotel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "property_payment_configuration_methods")
@FilterDef(name = "propertyPaymentMethodTenantFilter", parameters = @ParamDef(name = "hotelId", type = Long.class))
@Filter(name = "propertyPaymentMethodTenantFilter", condition = "hotel_id = :hotelId")
public class PropertyPaymentConfigurationMethod {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "configuration_id", nullable = false) private PropertyPaymentConfiguration configuration;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "hotel_id", nullable = false) private Hotel hotel;
    @Column(nullable = false, length = 40) private String method;
    @Column(nullable = false) private boolean enabled;
    @Column(length = 40) private String provider;
    @Column(name = "merchant_reference_masked", length = 160) private String merchantReferenceMasked;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    protected PropertyPaymentConfigurationMethod() { }
    public PropertyPaymentConfigurationMethod(String method, boolean enabled, String provider, String merchantReferenceMasked) {
        this.method = method; this.enabled = enabled; this.provider = provider; this.merchantReferenceMasked = merchantReferenceMasked;
    }
    void attach(PropertyPaymentConfiguration configuration, Hotel hotel) { this.configuration = configuration; this.hotel = hotel; }
    @PrePersist void created() { LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void updated() { updatedAt = LocalDateTime.now(); }
}
