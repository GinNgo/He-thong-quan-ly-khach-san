package com.hotel.propertycommerce.review;

import com.hotel.security.ActionCode;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
public class StayReviewController {
    private final StayReviewService service;
    public StayReviewController(StayReviewService service) { this.service = service; }

    @PostMapping("/api/reservations/{reservationId}/review")
    public ReviewResponse create(@PathVariable Long reservationId, @RequestBody ReviewRequest request) {
        return ReviewResponse.from(service.create(reservationId, request.rating(), request.title(), request.comment()));
    }

    @GetMapping("/api/reviews/mine")
    public List<ReviewResponse> mine() { return service.myReviews().stream().map(ReviewResponse::from).toList(); }

    @GetMapping("/api/management/properties/{hotelId}/reviews")
    @Permission(function = FunctionCode.REVIEW, action = ActionCode.VIEW)
    public List<ReviewResponse> property(@PathVariable Long hotelId) {
        return service.propertyReviews(hotelId).stream().map(ReviewResponse::from).toList();
    }

    @PostMapping("/api/management/reviews/{reviewId}/moderation")
    @Permission(function = FunctionCode.REVIEW, action = ActionCode.APPROVE)
    public ReviewResponse moderate(@PathVariable Long reviewId, @RequestBody ModerationRequest request) {
        return ReviewResponse.from(service.moderate(reviewId, request.status(), request.reason()));
    }

    @PostMapping("/api/management/reviews/{reviewId}/response")
    @Permission(function = FunctionCode.REVIEW, action = ActionCode.UPDATE)
    public ReviewResponse respond(@PathVariable Long reviewId, @RequestBody ResponseRequest request) {
        return ReviewResponse.from(service.respond(reviewId, request.response()));
    }

    public record ReviewRequest(int rating, String title, String comment) {}
    public record ModerationRequest(String status, String reason) {}
    public record ResponseRequest(String response) {}
    public record ReviewResponse(Long id, Long reservationId, Long hotelId, int rating, String title,
            String comment, String status, String moderationReason, String propertyResponse,
            LocalDateTime respondedAt, LocalDateTime createdAt) {
        static ReviewResponse from(StayReview review) {
            return new ReviewResponse(review.getId(), review.getReservation().getId(), review.getHotel().getId(),
                    review.getRating(), review.getTitle(), review.getComment(), review.getStatus().name(),
                    review.getModerationReason(), review.getPropertyResponse(), review.getRespondedAt(), review.getCreatedAt());
        }
    }
}
