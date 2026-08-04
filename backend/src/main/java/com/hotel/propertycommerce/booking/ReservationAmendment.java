package com.hotel.propertycommerce.booking;

import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.RoomType;
import com.hotel.entities.User;
import com.hotel.propertycommerce.payment.PropertyPaymentAttempt;
import com.hotel.propertycommerce.refund.PropertyRefundRequest;
import com.hotel.paymentprovider.domain.FinancialStates.PaymentState;
import com.hotel.paymentprovider.domain.VndMoney;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import lombok.Getter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Entity
@Table(name = "reservation_amendments",
        uniqueConstraints = {
                @UniqueConstraint(name = "UQ_reservation_amendment_public", columnNames = "public_id"),
                @UniqueConstraint(name = "UQ_reservation_amendment_idempotency",
                        columnNames = {"hotel_id", "idempotency_key"})
        },
        indexes = {
                @Index(name = "IX_reservation_amendment_reservation_status",
                        columnList = "hotel_id,reservation_id,status,expires_at"),
                @Index(name = "IX_reservation_amendment_hold",
                        columnList = "hotel_id,proposed_room_type_id,status,proposed_check_in,proposed_check_out")
        })
@FilterDef(name = "reservationAmendmentTenantFilter",
        parameters = @ParamDef(name = "hotelId", type = Long.class))
@Filter(name = "reservationAmendmentTenantFilter", condition = "hotel_id = :hotelId")
public class ReservationAmendment {

    public enum Status {
        QUOTED,
        AWAITING_PAYMENT,
        PAYMENT_PENDING,
        APPLIED,
        EXPIRED,
        CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, length = 64, updatable = false)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id", nullable = false, updatable = false)
    private Hotel hotel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, updatable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id", updatable = false)
    private User actor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "original_room_type_id", nullable = false, updatable = false)
    private RoomType originalRoomType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposed_room_type_id", nullable = false, updatable = false)
    private RoomType proposedRoomType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_attempt_id")
    private PropertyPaymentAttempt paymentAttempt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status;

    @Column(name = "policy_version", nullable = false, length = 80, updatable = false)
    private String policyVersion;

    @Column(name = "actor_type", nullable = false, length = 20, updatable = false)
    private String actorType;

    @Column(name = "original_check_in", nullable = false, updatable = false)
    private LocalDate originalCheckIn;

    @Column(name = "original_check_out", nullable = false, updatable = false)
    private LocalDate originalCheckOut;

    @Column(name = "proposed_check_in", nullable = false, updatable = false)
    private LocalDate proposedCheckIn;

    @Column(name = "proposed_check_out", nullable = false, updatable = false)
    private LocalDate proposedCheckOut;

    @Column(name = "original_quantity", nullable = false, updatable = false)
    private Integer originalQuantity;

    @Column(name = "proposed_quantity", nullable = false, updatable = false)
    private Integer proposedQuantity;

    @Column(name = "original_adults", nullable = false, updatable = false)
    private Integer originalAdults;

    @Column(name = "proposed_adults", nullable = false, updatable = false)
    private Integer proposedAdults;

    @Column(name = "original_children", nullable = false, updatable = false)
    private Integer originalChildren;

    @Column(name = "proposed_children", nullable = false, updatable = false)
    private Integer proposedChildren;

    @Column(name = "original_total", nullable = false, precision = 19, scale = 0, updatable = false)
    private BigDecimal originalTotal;

    @Column(name = "proposed_total", nullable = false, precision = 19, scale = 0, updatable = false)
    private BigDecimal proposedTotal;

    @Column(name = "price_delta", nullable = false, precision = 19, scale = 0, updatable = false)
    private BigDecimal priceDelta;

    @Column(name = "original_deposit", nullable = false, precision = 19, scale = 0, updatable = false)
    private BigDecimal originalDeposit;

    @Column(name = "proposed_deposit", nullable = false, precision = 19, scale = 0, updatable = false)
    private BigDecimal proposedDeposit;

    @Column(name = "preserved_discount", nullable = false, precision = 19, scale = 0, updatable = false)
    private BigDecimal preservedDiscount;

    @Column(name = "hold_quantity", nullable = false, updatable = false)
    private Integer holdQuantity;

    @Column(name = "idempotency_key", nullable = false, length = 160, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;

    @Column(name = "apply_idempotency_key", length = 160)
    private String applyIdempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_request_id")
    private PropertyRefundRequest refundRequest;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private long version;

    protected ReservationAmendment() {
    }

    public static ReservationAmendment quote(QuoteSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "quote snapshot must not be null");
        ReservationAmendment amendment = new ReservationAmendment();
        amendment.publicId = requireText(snapshot.publicId(), "publicId");
        amendment.hotel = Objects.requireNonNull(snapshot.reservation().getHotel(), "reservation hotel is required");
        amendment.reservation = snapshot.reservation();
        amendment.actor = snapshot.actor();
        amendment.actorType = requireText(snapshot.actorType(), "actorType");
        amendment.originalRoomType = Objects.requireNonNull(snapshot.originalRoomType(), "originalRoomType is required");
        amendment.proposedRoomType = Objects.requireNonNull(snapshot.proposedRoomType(), "proposedRoomType is required");
        amendment.status = snapshot.priceDelta().signum() > 0 ? Status.AWAITING_PAYMENT : Status.QUOTED;
        amendment.policyVersion = ReservationAmendmentPolicy.VERSION;
        amendment.originalCheckIn = snapshot.originalCheckIn();
        amendment.originalCheckOut = snapshot.originalCheckOut();
        amendment.proposedCheckIn = snapshot.proposedCheckIn();
        amendment.proposedCheckOut = snapshot.proposedCheckOut();
        amendment.originalQuantity = snapshot.originalQuantity();
        amendment.proposedQuantity = snapshot.proposedQuantity();
        amendment.originalAdults = snapshot.originalAdults();
        amendment.proposedAdults = snapshot.proposedAdults();
        amendment.originalChildren = snapshot.originalChildren();
        amendment.proposedChildren = snapshot.proposedChildren();
        amendment.originalTotal = snapshot.originalTotal();
        amendment.proposedTotal = snapshot.proposedTotal();
        amendment.priceDelta = snapshot.priceDelta();
        amendment.originalDeposit = snapshot.originalDeposit();
        amendment.proposedDeposit = snapshot.proposedDeposit();
        amendment.preservedDiscount = snapshot.preservedDiscount();
        amendment.holdQuantity = snapshot.holdQuantity();
        amendment.idempotencyKey = requireText(snapshot.idempotencyKey(), "idempotencyKey");
        amendment.requestHash = requireText(snapshot.requestHash(), "requestHash");
        amendment.expiresAt = Objects.requireNonNull(snapshot.expiresAt(), "expiresAt is required");
        amendment.validateSnapshot();
        return amendment;
    }

    public void bindPaymentAttempt(PropertyPaymentAttempt attempt) {
        PropertyPaymentAttempt candidate = Objects.requireNonNull(attempt, "payment attempt is required");
        if (status != Status.AWAITING_PAYMENT && status != Status.PAYMENT_PENDING) {
            throw new IllegalStateException("This quote does not require a payment attempt.");
        }
        if (candidate.getId() == null) {
            throw new IllegalArgumentException("Payment attempt must be persisted before it is bound.");
        }
        if (candidate.getPurpose() != PropertyPaymentAttempt.Purpose.AMENDMENT_DELTA
                || candidate.getReservationAmendment() == null
                || !Objects.equals(candidate.getReservationAmendment().getId(), id)
                || !Objects.equals(candidate.getHotel().getId(), hotel.getId())
                || !Objects.equals(candidate.getReservation().getId(), reservation.getId())
                || candidate.getExpectedAmount().compareTo(priceDelta) != 0) {
            throw new IllegalArgumentException("Payment attempt does not match this amendment quote.");
        }
        if (paymentAttempt != null && !Objects.equals(paymentAttempt.getId(), candidate.getId())) {
            PaymentState current = paymentAttempt.getStatus();
            if (current == PaymentState.SUCCESS || isActivePaymentState(current)) {
                throw new IllegalStateException("The current payment attempt cannot be replaced.");
            }
        }
        paymentAttempt = candidate;
        status = Status.PAYMENT_PENDING;
    }

    public void markExpired(LocalDateTime now) {
        if (isActive() && !Objects.requireNonNull(now, "current time is required").isBefore(expiresAt)) {
            status = Status.EXPIRED;
        }
    }

    public void markCancelled() {
        if (isActive()) {
            status = Status.CANCELLED;
        }
    }

    public void markApplied(String applyKey, PropertyRefundRequest refund, LocalDateTime now) {
        LocalDateTime appliedTime = Objects.requireNonNull(now, "applied time is required");
        if (!isActive()) {
            throw new IllegalStateException("Only an active quote can be applied.");
        }
        if (!appliedTime.isBefore(expiresAt)) {
            throw new IllegalStateException("The amendment quote has expired.");
        }
        applyIdempotencyKey = requireText(applyKey, "apply idempotency key");
        refundRequest = refund;
        status = Status.APPLIED;
        appliedAt = appliedTime;
    }

    public boolean isActive() {
        return status == Status.QUOTED || status == Status.AWAITING_PAYMENT || status == Status.PAYMENT_PENDING;
    }

    public boolean structuralChange() {
        return !originalRoomType.getId().equals(proposedRoomType.getId())
                || !originalCheckIn.equals(proposedCheckIn)
                || !originalCheckOut.equals(proposedCheckOut)
                || !originalQuantity.equals(proposedQuantity);
    }

    @PrePersist
    void created() {
        validateSnapshot();
        createdAt = LocalDateTime.now(java.time.Clock.systemUTC());
        updatedAt = createdAt;
    }

    @PreUpdate
    void updated() {
        validateSnapshot();
        updatedAt = LocalDateTime.now(java.time.Clock.systemUTC());
    }

    private void validateSnapshot() {
        if (reservation == null || hotel == null || reservation.getHotel() == null
                || !Objects.equals(hotel.getId(), reservation.getHotel().getId())) {
            throw new IllegalArgumentException("Reservation amendment must remain within one property.");
        }
        if (!Objects.equals(hotel.getId(), originalRoomType.getHotel().getId())
                || !Objects.equals(hotel.getId(), proposedRoomType.getHotel().getId())) {
            throw new IllegalArgumentException("Reservation amendment room types must belong to the property.");
        }
        if (proposedCheckIn == null || proposedCheckOut == null || !proposedCheckOut.isAfter(proposedCheckIn)) {
            throw new IllegalArgumentException("Proposed stay dates are invalid.");
        }
        if (originalCheckIn == null || originalCheckOut == null || !originalCheckOut.isAfter(originalCheckIn)) {
            throw new IllegalArgumentException("Original stay dates are invalid.");
        }
        if (originalQuantity == null || proposedQuantity == null || originalQuantity < 1 || proposedQuantity < 1) {
            throw new IllegalArgumentException("Reservation amendment quantities must be positive.");
        }
        if (originalAdults == null || proposedAdults == null || originalAdults < 1 || proposedAdults < 1
                || originalChildren == null || proposedChildren == null
                || originalChildren < 0 || proposedChildren < 0) {
            throw new IllegalArgumentException("Reservation amendment guest counts are invalid.");
        }
        requireMoney(originalTotal, true, "originalTotal");
        requireMoney(proposedTotal, true, "proposedTotal");
        requireMoney(originalDeposit, true, "originalDeposit");
        requireMoney(proposedDeposit, true, "proposedDeposit");
        requireMoney(preservedDiscount, true, "preservedDiscount");
        if (priceDelta == null
                || priceDelta.compareTo(proposedTotal.subtract(originalTotal)) != 0) {
            throw new IllegalArgumentException("Reservation amendment price delta is invalid.");
        }
        VndMoney.of(priceDelta.abs());
        if (originalDeposit.compareTo(originalTotal) > 0 || proposedDeposit.compareTo(proposedTotal) > 0) {
            throw new IllegalArgumentException("Reservation amendment deposit cannot exceed its booking total.");
        }
        if (preservedDiscount.compareTo(proposedTotal.add(preservedDiscount)) > 0) {
            throw new IllegalArgumentException("Reservation amendment discount is invalid.");
        }
        if (holdQuantity == null || holdQuantity < 0 || holdQuantity > proposedQuantity) {
            throw new IllegalArgumentException("Reservation amendment hold quantity is invalid.");
        }
        requireText(publicId, "publicId");
        requireText(policyVersion, "policyVersion");
        requireText(actorType, "actorType");
        requireText(idempotencyKey, "idempotencyKey");
        String hash = requireText(requestHash, "requestHash");
        if (hash.length() != 64) {
            throw new IllegalArgumentException("Reservation amendment request hash is invalid.");
        }
        if (!java.util.Set.of("CUSTOMER", "STAFF", "SYSTEM").contains(actorType)) {
            throw new IllegalArgumentException("Reservation amendment actor type is invalid.");
        }
    }

    private static void requireMoney(BigDecimal value, boolean allowZero, String field) {
        if (value == null || (allowZero ? value.signum() < 0 : value.signum() <= 0)) {
            throw new IllegalArgumentException(field + " must be an integer VND amount.");
        }
        VndMoney.of(value);
    }

    private static boolean isActivePaymentState(PaymentState state) {
        return state == PaymentState.CREATED
                || state == PaymentState.PENDING
                || state == PaymentState.PENDING_VERIFICATION
                || state == PaymentState.PROCESSING;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank.");
        }
        return value.trim();
    }

    public record QuoteSnapshot(
            String publicId,
            Reservation reservation,
            User actor,
            String actorType,
            RoomType originalRoomType,
            RoomType proposedRoomType,
            LocalDate originalCheckIn,
            LocalDate originalCheckOut,
            LocalDate proposedCheckIn,
            LocalDate proposedCheckOut,
            int originalQuantity,
            int proposedQuantity,
            int originalAdults,
            int proposedAdults,
            int originalChildren,
            int proposedChildren,
            BigDecimal originalTotal,
            BigDecimal proposedTotal,
            BigDecimal priceDelta,
            BigDecimal originalDeposit,
            BigDecimal proposedDeposit,
            BigDecimal preservedDiscount,
            int holdQuantity,
            String idempotencyKey,
            String requestHash,
            LocalDateTime expiresAt) {
    }
}
