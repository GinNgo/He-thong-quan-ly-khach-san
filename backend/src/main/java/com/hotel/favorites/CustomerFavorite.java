package com.hotel.favorites;

import com.hotel.entities.Hotel;
import com.hotel.entities.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "customer_favorites",
        uniqueConstraints = @UniqueConstraint(
                name = "UQ_customer_favorites_customer_hotel",
                columnNames = {"customer_id", "hotel_id"}),
        indexes = {
                @Index(name = "IX_customer_favorites_customer_created", columnList = "customer_id,created_at"),
                @Index(name = "IX_customer_favorites_hotel", columnList = "hotel_id")
        })
public class CustomerFavorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    // The hotel is a public catalog target; ownership is always the customer above.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void initializeCreatedAt() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(ZoneOffset.UTC);
        }
    }
}
