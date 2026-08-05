package com.hotel.propertycommerce.review;

import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.services.PropertyAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StayReviewServiceTest {
    @Mock StayReviewRepository reviewRepository;
    @Mock ReservationRepository reservationRepository;
    @Mock HotelRepository hotelRepository;
    @Mock PropertyAccessService propertyAccessService;
    StayReviewService service;

    @BeforeEach
    void setUp() {
        service = new StayReviewService(reviewRepository, reservationRepository, hotelRepository,
                propertyAccessService, Clock.fixed(Instant.parse("2026-08-04T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void completedStayOwnerCreatesOneVerifiedReviewAndRefreshesAggregate() {
        Fixture fixture = fixture("CHECKED_OUT");
        when(propertyAccessService.currentUser()).thenReturn(fixture.customer());
        when(reservationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(fixture.reservation()));
        when(reviewRepository.findByReservationId(42L)).thenReturn(Optional.empty());
        when(reviewRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            StayReview review = invocation.getArgument(0); ReflectionTestUtils.setField(review, "id", 71L); return review;
        });
        when(hotelRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(fixture.hotel()));
        when(reviewRepository.publishedAggregate(3L)).thenReturn(new Object[]{8.5, 2L});

        StayReview review = service.create(42L, 9, "Great stay", "Helpful staff and a clean room.");

        assertThat(review.getReservation().getId()).isEqualTo(42L);
        assertThat(review.getStatus()).isEqualTo(StayReview.Status.PUBLISHED);
        assertThat(fixture.hotel().getAverageRating()).isEqualTo(8.5);
        assertThat(fixture.hotel().getReviewCount()).isEqualTo(2);
    }

    @Test
    void rejectsAnotherCustomerIncompleteStayAndDuplicateSubmission() {
        Fixture fixture = fixture("CHECKED_OUT");
        User other = new User(); other.setId(99L);
        when(propertyAccessService.currentUser()).thenReturn(other);
        when(reservationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(fixture.reservation()));
        assertThatThrownBy(() -> service.create(42L, 8, null, "A legitimate review comment."))
                .isInstanceOf(FinancialException.class);

        when(propertyAccessService.currentUser()).thenReturn(fixture.customer());
        fixture.reservation().setStatus("CHECKED_IN");
        assertThatThrownBy(() -> service.create(42L, 8, null, "A legitimate review comment."))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("completed");

        fixture.reservation().setStatus("CHECKED_OUT");
        when(reviewRepository.findByReservationId(42L)).thenReturn(Optional.of(review(fixture)));
        assertThatThrownBy(() -> service.create(42L, 8, null, "A legitimate review comment."))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("already");
    }

    @Test
    void appliesDailyAbuseLimitAndRejectsLinks() {
        Fixture fixture = fixture("CHECKED_OUT");
        when(propertyAccessService.currentUser()).thenReturn(fixture.customer());
        when(reservationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(fixture.reservation()));
        when(reviewRepository.findByReservationId(42L)).thenReturn(Optional.empty());
        when(reviewRepository.countByCustomerIdAndCreatedAtAfter(any(), any())).thenReturn(5L);
        assertThatThrownBy(() -> service.create(42L, 8, null, "A legitimate review comment."))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("limit");

        when(reviewRepository.countByCustomerIdAndCreatedAtAfter(any(), any())).thenReturn(0L);
        assertThatThrownBy(() -> service.create(42L, 8, null, "Visit https://spam.example now"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("links");
        verify(reviewRepository, never()).saveAndFlush(any());
    }

    @Test
    void translatesConcurrentUniqueReservationConflictIntoAStableDuplicateResult() {
        Fixture fixture = fixture("CHECKED_OUT");
        when(propertyAccessService.currentUser()).thenReturn(fixture.customer());
        when(reservationRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(fixture.reservation()));
        when(reviewRepository.findByReservationId(42L)).thenReturn(Optional.empty());
        when(reviewRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_stay_reviews_reservation"));

        assertThatThrownBy(() -> service.create(42L, 8, null, "A legitimate review comment."))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("already");
        verify(hotelRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void moderationAndResponseRequirePropertyAccessAndRefreshPublishedAggregate() {
        Fixture fixture = fixture("CHECKED_OUT");
        StayReview review = review(fixture);
        User manager = new User(); manager.setId(9L);
        when(reviewRepository.findByIdForUpdate(71L)).thenReturn(Optional.of(review));
        when(propertyAccessService.currentUser()).thenReturn(manager);
        when(reviewRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(hotelRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(fixture.hotel()));
        when(reviewRepository.publishedAggregate(3L)).thenReturn(new Object[]{null, 0L});

        StayReview hidden = service.moderate(71L, "HIDDEN", "Contains personal information");
        assertThat(hidden.getStatus()).isEqualTo(StayReview.Status.HIDDEN);
        assertThat(fixture.hotel().getReviewCount()).isZero();
        verify(propertyAccessService).requireAccessibleOrNotFound(3L, "review");

        StayReview responded = service.respond(71L, "Thank you for your feedback.");
        assertThat(responded.getPropertyResponse()).contains("Thank you");
    }

    private Fixture fixture(String status) {
        Hotel hotel = new Hotel(); hotel.setId(3L);
        User customer = new User(); customer.setId(8L);
        Reservation reservation = new Reservation(); reservation.setId(42L); reservation.setHotel(hotel);
        reservation.setUser(customer); reservation.setStatus(status);
        return new Fixture(hotel, customer, reservation);
    }

    private StayReview review(Fixture fixture) {
        StayReview review = StayReview.create(fixture.reservation(), fixture.customer(), 8,
                "Good", "A legitimate review comment.", java.time.LocalDateTime.of(2026, 8, 4, 12, 0));
        ReflectionTestUtils.setField(review, "id", 71L);
        return review;
    }

    private record Fixture(Hotel hotel, User customer, Reservation reservation) {}
}
