package com.hotel.propertycommerce.review;

import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Entity
@Table(name = "stay_reviews", uniqueConstraints = @UniqueConstraint(
        name = "uq_stay_reviews_reservation", columnNames = "reservation_id"), indexes = {
        @Index(name = "ix_stay_reviews_hotel_status_created", columnList = "hotel_id,status,created_at"),
        @Index(name = "ix_stay_reviews_customer_created", columnList = "customer_id,created_at")
})
@FilterDef(name = "stayReviewTenantFilter", parameters = @ParamDef(name = "hotelId", type = Long.class))
@Filter(name = "stayReviewTenantFilter", condition = "hotel_id = :hotelId")
public class StayReview {
    public enum Status { PUBLISHED, HIDDEN }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "hotel_id", nullable = false, updatable = false) private Hotel hotel;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "reservation_id", nullable = false, updatable = false) private Reservation reservation;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "customer_id", nullable = false, updatable = false) private User customer;
    @Column(nullable = false, updatable = false) private int rating;
    @Column(length = 150, updatable = false) private String title;
    @Column(nullable = false, length = 2000, updatable = false) private String comment;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(name = "moderation_reason", length = 500) private String moderationReason;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "moderated_by") private User moderatedBy;
    @Column(name = "moderated_at") private LocalDateTime moderatedAt;
    @Column(name = "property_response", length = 1000) private String propertyResponse;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "responded_by") private User respondedBy;
    @Column(name = "responded_at") private LocalDateTime respondedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Version private long version;

    protected StayReview() {}

    public static StayReview create(Reservation reservation, User customer, int rating,
                                    String title, String comment, LocalDateTime now) {
        StayReview review = new StayReview();
        review.reservation = Objects.requireNonNull(reservation);
        review.hotel = Objects.requireNonNull(reservation.getHotel());
        review.customer = Objects.requireNonNull(customer);
        review.rating = rating;
        review.title = normalize(title);
        review.comment = require(comment);
        review.status = Status.PUBLISHED;
        review.createdAt = Objects.requireNonNull(now);
        review.validate();
        return review;
    }

    public void moderate(Status next, String reason, User actor, LocalDateTime now) {
        status = Objects.requireNonNull(next);
        moderationReason = require(reason);
        moderatedBy = Objects.requireNonNull(actor);
        moderatedAt = Objects.requireNonNull(now);
        validate();
    }

    public void respond(String response, User actor, LocalDateTime now) {
        propertyResponse = require(response);
        respondedBy = Objects.requireNonNull(actor);
        respondedAt = Objects.requireNonNull(now);
        validate();
    }

    private void validate() {
        if (rating < 1 || rating > 10) throw new IllegalArgumentException("Rating must be between 1 and 10.");
        if (comment.length() < 10 || comment.length() > 2000) throw new IllegalArgumentException("Review comment must contain 10 to 2000 characters.");
        if (title != null && title.length() > 150) throw new IllegalArgumentException("Review title is too long.");
        if (moderationReason != null && moderationReason.length() > 500) throw new IllegalArgumentException("Moderation reason is too long.");
        if (propertyResponse != null && (propertyResponse.length() < 2 || propertyResponse.length() > 1000)) throw new IllegalArgumentException("Property response must contain 2 to 1000 characters.");
    }

    private static String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String require(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Text is required.");
        return value.trim();
    }
}
