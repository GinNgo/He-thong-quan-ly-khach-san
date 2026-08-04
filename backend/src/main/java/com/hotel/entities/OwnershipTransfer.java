package com.hotel.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "ownership_transfers")
@Getter @Setter @NoArgsConstructor
public class OwnershipTransfer extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "from_user_id", nullable = false)
    private User fromUser;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "to_user_id", nullable = false)
    private User toUser;
    @Column(nullable = false, length = 20)
    private String status = "PENDING";
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;
    @Column(name = "responsibility_accepted_at")
    private LocalDateTime responsibilityAcceptedAt;
}
