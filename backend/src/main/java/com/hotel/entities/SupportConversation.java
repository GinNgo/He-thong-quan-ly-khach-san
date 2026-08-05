package com.hotel.entities;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "support_conversations",
        uniqueConstraints = @UniqueConstraint(
                name = "UQ_support_conversations_public_id", columnNames = "public_id"),
        indexes = {
                @Index(name = "IX_support_conversations_hotel_status_activity",
                        columnList = "hotel_id,status,last_activity_at"),
                @Index(name = "IX_support_conversations_customer_activity",
                        columnList = "customer_id,last_activity_at"),
                @Index(name = "IX_support_conversations_agent_status",
                        columnList = "assigned_agent_id,status")
        })
public class SupportConversation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, length = 64)
    private String publicId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", insertable = false, updatable = false)
    private User customer;

    @Column(nullable = false, length = 120)
    private String subject;

    @Column(name = "hotel_id")
    private Long hotelId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id", insertable = false, updatable = false)
    private Hotel hotel;

    @Column(name = "reservation_id")
    private Long reservationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", insertable = false, updatable = false)
    private Reservation reservation;

    @Column(name = "assigned_agent_id")
    private Long assignedAgentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_agent_id", insertable = false, updatable = false)
    private User assignedAgent;

    @Column(nullable = false, length = 20)
    private String channel;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @Column(name = "sla_deadline_at")
    private Instant slaDeadlineAt;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "escalated_at")
    private Instant escalatedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "first_response_at")
    private Instant firstResponseAt;

    @Column(name = "last_customer_message_at")
    private Instant lastCustomerMessageAt;

    @Column(name = "last_support_reply_at")
    private Instant lastSupportReplyAt;

    @Column(name = "closed_reason", length = 500)
    private String closedReason;

    @Column(name = "reopened_at")
    private Instant reopenedAt;

    @Column(name = "reopen_reason", length = 500)
    private String reopenReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (publicId == null || publicId.isBlank()) publicId = UUID.randomUUID().toString();
        if (channel == null || channel.isBlank()) channel = "IN_APP";
        if (status == null || status.isBlank()) status = "OPEN";
        if (lastActivityAt == null) lastActivityAt = now;
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public User getCustomer() { return customer; }
    public void setCustomer(User customer) {
        this.customer = customer;
        this.customerId = customer == null ? null : customer.getId();
    }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public Long getHotelId() { return hotelId; }
    public void setHotelId(Long hotelId) { this.hotelId = hotelId; }
    public Hotel getHotel() { return hotel; }
    public void setHotel(Hotel hotel) {
        this.hotel = hotel;
        this.hotelId = hotel == null ? null : hotel.getId();
    }
    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long reservationId) { this.reservationId = reservationId; }
    public Reservation getReservation() { return reservation; }
    public void setReservation(Reservation reservation) {
        this.reservation = reservation;
        this.reservationId = reservation == null ? null : reservation.getId();
    }
    public Long getAssignedAgentId() { return assignedAgentId; }
    public void setAssignedAgentId(Long assignedAgentId) { this.assignedAgentId = assignedAgentId; }
    public User getAssignedAgent() { return assignedAgent; }
    public void setAssignedAgent(User assignedAgent) {
        this.assignedAgent = assignedAgent;
        this.assignedAgentId = assignedAgent == null ? null : assignedAgent.getId();
    }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getLastActivityAt() { return lastActivityAt; }
    public void setLastActivityAt(Instant lastActivityAt) { this.lastActivityAt = lastActivityAt; }
    public Instant getSlaDeadlineAt() { return slaDeadlineAt; }
    public void setSlaDeadlineAt(Instant slaDeadlineAt) { this.slaDeadlineAt = slaDeadlineAt; }
    public Instant getAssignedAt() { return assignedAt; }
    public void setAssignedAt(Instant assignedAt) { this.assignedAt = assignedAt; }
    public Instant getEscalatedAt() { return escalatedAt; }
    public void setEscalatedAt(Instant escalatedAt) { this.escalatedAt = escalatedAt; }
    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
    public Instant getFirstResponseAt() { return firstResponseAt; }
    public void setFirstResponseAt(Instant firstResponseAt) { this.firstResponseAt = firstResponseAt; }
    public Instant getLastCustomerMessageAt() { return lastCustomerMessageAt; }
    public void setLastCustomerMessageAt(Instant lastCustomerMessageAt) { this.lastCustomerMessageAt = lastCustomerMessageAt; }
    public Instant getLastSupportReplyAt() { return lastSupportReplyAt; }
    public void setLastSupportReplyAt(Instant lastSupportReplyAt) { this.lastSupportReplyAt = lastSupportReplyAt; }
    public String getClosedReason() { return closedReason; }
    public void setClosedReason(String closedReason) { this.closedReason = closedReason; }
    public Instant getReopenedAt() { return reopenedAt; }
    public void setReopenedAt(Instant reopenedAt) { this.reopenedAt = reopenedAt; }
    public String getReopenReason() { return reopenReason; }
    public void setReopenReason(String reopenReason) { this.reopenReason = reopenReason; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
