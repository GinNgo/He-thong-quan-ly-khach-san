package com.hotel.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "owner_invitations")
@Getter @Setter @NoArgsConstructor
public class OwnerInvitation extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "invited_by_user_id", nullable = false)
    private User invitedBy;
    @Column(name = "invited_email", nullable = false, length = 320)
    private String invitedEmail;
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;
    @Column(nullable = false, length = 20)
    private String status = "PENDING";
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "accepted_by_user_id")
    private User acceptedBy;
    @Column(name = "owner_terms_accepted_at")
    private LocalDateTime ownerTermsAcceptedAt;
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;
}
