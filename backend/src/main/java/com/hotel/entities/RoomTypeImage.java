package com.hotel.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "room_type_images")
public class RoomTypeImage extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_type_id", nullable = false)
    @JsonIgnore
    private RoomType roomType;

    @Column(name = "image_url", nullable = false, columnDefinition = "nvarchar(1000)")
    private String imageUrl;

    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary = false;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "alt_text_vi", columnDefinition = "nvarchar(255)")
    private String altTextVi;

    @Column(name = "alt_text_en", columnDefinition = "nvarchar(255)")
    private String altTextEn;

    @Column(name = "is_demo", nullable = false)
    private Boolean isDemo = false;
}
