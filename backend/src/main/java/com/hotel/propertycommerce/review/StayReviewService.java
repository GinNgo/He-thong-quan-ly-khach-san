package com.hotel.propertycommerce.review;

import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.services.PropertyAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

@Service
public class StayReviewService {
    private final StayReviewRepository reviewRepository;
    private final ReservationRepository reservationRepository;
    private final HotelRepository hotelRepository;
    private final PropertyAccessService propertyAccessService;
    private final Clock clock;

    public StayReviewService(StayReviewRepository reviewRepository, ReservationRepository reservationRepository,
            HotelRepository hotelRepository, PropertyAccessService propertyAccessService) {
        this(reviewRepository, reservationRepository, hotelRepository, propertyAccessService, Clock.systemUTC());
    }

    StayReviewService(StayReviewRepository reviewRepository, ReservationRepository reservationRepository,
            HotelRepository hotelRepository, PropertyAccessService propertyAccessService, Clock clock) {
        this.reviewRepository = reviewRepository; this.reservationRepository = reservationRepository;
        this.hotelRepository = hotelRepository; this.propertyAccessService = propertyAccessService; this.clock = clock;
    }

    @Transactional
    public StayReview create(Long reservationId, int rating, String title, String comment) {
        User actor = propertyAccessService.currentUser();
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> notFound());
        if (actor == null || reservation.getUser() == null || !actor.getId().equals(reservation.getUser().getId())) throw notFound();
        if (!"CHECKED_OUT".equals(reservation.getStatus())) throw new IllegalStateException("Only a completed stay can be reviewed.");
        if (reviewRepository.findByReservationId(reservationId).isPresent()) throw new IllegalStateException("This stay already has a review.");
        LocalDateTime now = now();
        if (reviewRepository.countByCustomerIdAndCreatedAtAfter(actor.getId(), now.minusDays(1)) >= 5) {
            throw new IllegalStateException("Daily review submission limit reached.");
        }
        validateContent(comment);
        StayReview saved;
        try {
            saved = reviewRepository.saveAndFlush(StayReview.create(reservation, actor, rating, title, comment, now));
        } catch (DataIntegrityViolationException conflict) {
            throw new IllegalStateException("This stay already has a review.", conflict);
        }
        refreshAggregate(reservation.getHotel().getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<StayReview> myReviews() {
        User actor = propertyAccessService.currentUser();
        return actor == null ? List.of() : reviewRepository.findByCustomerIdOrderByCreatedAtDesc(actor.getId());
    }

    @Transactional(readOnly = true)
    public List<StayReview> propertyReviews(Long hotelId) {
        propertyAccessService.requireAccessibleOrNotFound(hotelId, "property reviews");
        return reviewRepository.findByHotelIdOrderByCreatedAtDesc(hotelId);
    }

    @Transactional
    public StayReview moderate(Long reviewId, String status, String reason) {
        StayReview review = lockedAccessible(reviewId);
        StayReview.Status next;
        try { next = StayReview.Status.valueOf(status == null ? "" : status.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Unsupported review status."); }
        review.moderate(next, reason, propertyAccessService.currentUser(), now());
        reviewRepository.saveAndFlush(review);
        refreshAggregate(review.getHotel().getId());
        return review;
    }

    @Transactional
    public StayReview respond(Long reviewId, String response) {
        StayReview review = lockedAccessible(reviewId);
        validateContent(response);
        review.respond(response, propertyAccessService.currentUser(), now());
        return reviewRepository.saveAndFlush(review);
    }

    private StayReview lockedAccessible(Long reviewId) {
        StayReview review = reviewRepository.findByIdForUpdate(reviewId).orElseThrow(() -> notFound());
        propertyAccessService.requireAccessibleOrNotFound(review.getHotel().getId(), "review");
        return review;
    }

    private void refreshAggregate(Long hotelId) {
        Hotel hotel = hotelRepository.findByIdForUpdate(hotelId).orElseThrow(() -> notFound());
        Object[] aggregate = reviewRepository.publishedAggregate(hotelId);
        Number average = aggregate != null && aggregate.length > 0 ? (Number) aggregate[0] : null;
        Number count = aggregate != null && aggregate.length > 1 ? (Number) aggregate[1] : null;
        hotel.setAverageRating(average == null ? null : Math.round(average.doubleValue() * 10.0) / 10.0);
        hotel.setReviewCount(count == null ? 0 : count.intValue());
        hotelRepository.saveAndFlush(hotel);
    }

    private void validateContent(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("http://") || normalized.contains("https://") || normalized.contains("www.")) {
            throw new IllegalArgumentException("Review content cannot contain links.");
        }
        if (normalized.matches(".*(.)\\1{9,}.*")) throw new IllegalArgumentException("Review content looks abusive or automated.");
    }

    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private FinancialException notFound() { return new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND); }
}
