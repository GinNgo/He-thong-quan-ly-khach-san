package com.hotel.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.*;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "property_images")
@org.hibernate.annotations.FilterDef(name = "propertyImageTenantFilter", parameters = @org.hibernate.annotations.ParamDef(name = "hotelId", type = Long.class))
@org.hibernate.annotations.Filter(name = "propertyImageTenantFilter", condition = "hotel_id = :hotelId")
public class PropertyImage extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id", nullable = false)
    @JsonIgnore
    private Hotel hotel;

    @Column(name = "image_url", nullable = false, columnDefinition = "nvarchar(1000)")
    private String imageUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_id")
    @JsonIgnore
    private PropertyMedia media;

    @Column(name = "is_primary")
    private Boolean isPrimary = false;

    @Column(name = "alt_text_vi", columnDefinition = "nvarchar(255)")
    private String altTextVi;

    @Column(name = "alt_text_en", columnDefinition = "nvarchar(255)")
    private String altTextEn;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "is_demo", nullable = false)
    private Boolean isDemo = false;
}
