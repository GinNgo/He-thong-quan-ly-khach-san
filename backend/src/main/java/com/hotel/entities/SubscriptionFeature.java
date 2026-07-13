package com.hotel.entities;

import lombok.*;
import jakarta.persistence.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "subscription_features")
public class SubscriptionFeature extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code;

    @Column(name = "name_vi", nullable = false, columnDefinition = "NVARCHAR(255)")
    private String nameVi;

    @Column(name = "name_en", columnDefinition = "NVARCHAR(255)")
    private String nameEn;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String description;

    @Column(name = "value_type")
    private String valueType = "BOOLEAN"; // BOOLEAN, NUMERIC
}
