package com.hotel.services;

import com.hotel.domain.lifecycle.BookingLifecyclePolicy;
import com.hotel.domain.lifecycle.PaymentStatus;
import com.hotel.domain.lifecycle.RefundStatus;
import com.hotel.domain.lifecycle.ReservationStatus;
import com.hotel.domain.lifecycle.TransitionDecision;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BookingLifecyclePolicyTest {

    @Test
    void canonicalParsers_MapKnownLegacyValues() {
        assertAll(
                () -> assertEquals(ReservationStatus.PENDING_PAYMENT, ReservationStatus.fromStorage(" pending ")),
                () -> assertEquals(PaymentStatus.SUCCEEDED, PaymentStatus.fromStorage("SUCCESS")),
                () -> assertEquals(PaymentStatus.SUCCEEDED, PaymentStatus.fromStorage("REFUNDED")),
                () -> assertEquals(PaymentStatus.PENDING, PaymentStatus.fromStorage("PROCESSING")),
                () -> assertEquals(RefundStatus.PENDING_PROVIDER, RefundStatus.fromStorage("PENDING_REFUND")),
                () -> assertEquals(RefundStatus.SUCCEEDED, RefundStatus.fromStorage("REFUNDED")));
    }

    @Test
    void canonicalParsers_RejectMissingOrUnknownValues() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> ReservationStatus.fromStorage("")),
                () -> assertThrows(IllegalArgumentException.class, () -> PaymentStatus.fromStorage("UNKNOWN")),
                () -> assertThrows(IllegalArgumentException.class, () -> RefundStatus.fromStorage(null)));
    }

    @Test
    void reservationTransitions_AllowOnlyTheCanonicalForwardTable() {
        assertAll(
                () -> assertEquals(TransitionDecision.APPLY, BookingLifecyclePolicy.reservationTransition(
                        ReservationStatus.PENDING_PAYMENT, ReservationStatus.CONFIRMED)),
                () -> assertEquals(TransitionDecision.APPLY, BookingLifecyclePolicy.reservationTransition(
                        ReservationStatus.CONFIRMED, ReservationStatus.CHECKED_IN)),
                () -> assertEquals(TransitionDecision.APPLY, BookingLifecyclePolicy.reservationTransition(
                        ReservationStatus.CHECKED_IN, ReservationStatus.CHECKED_OUT)),
                () -> assertEquals(TransitionDecision.APPLY, BookingLifecyclePolicy.reservationTransition(
                        ReservationStatus.CHECKED_OUT, ReservationStatus.COMPLETED)),
                () -> assertEquals(TransitionDecision.REJECT, BookingLifecyclePolicy.reservationTransition(
                        ReservationStatus.PENDING_PAYMENT, ReservationStatus.CHECKED_IN)),
                () -> assertEquals(TransitionDecision.REJECT, BookingLifecyclePolicy.reservationTransition(
                        ReservationStatus.CANCELLED, ReservationStatus.CONFIRMED)),
                () -> assertEquals(TransitionDecision.REJECT, BookingLifecyclePolicy.reservationTransition(
                        ReservationStatus.COMPLETED, ReservationStatus.CHECKED_IN)));
    }

    @Test
    void paymentAndRefundTransitions_RejectBackwardTerminalChanges() {
        assertAll(
                () -> assertEquals(TransitionDecision.APPLY, BookingLifecyclePolicy.paymentTransition(
                        PaymentStatus.CREATED, PaymentStatus.PENDING)),
                () -> assertEquals(TransitionDecision.APPLY, BookingLifecyclePolicy.paymentTransition(
                        PaymentStatus.PENDING, PaymentStatus.SUCCEEDED)),
                () -> assertEquals(TransitionDecision.REJECT, BookingLifecyclePolicy.paymentTransition(
                        PaymentStatus.SUCCEEDED, PaymentStatus.PENDING)),
                () -> assertEquals(TransitionDecision.APPLY, BookingLifecyclePolicy.refundTransition(
                        RefundStatus.REQUESTED, RefundStatus.PENDING_PROVIDER)),
                () -> assertEquals(TransitionDecision.APPLY, BookingLifecyclePolicy.refundTransition(
                        RefundStatus.PENDING_PROVIDER, RefundStatus.SUCCEEDED)),
                () -> assertEquals(TransitionDecision.REJECT, BookingLifecyclePolicy.refundTransition(
                        RefundStatus.SUCCEEDED, RefundStatus.REQUESTED)));
    }

    @Test
    void replayedTransitions_AreIdempotent() {
        assertAll(
                () -> assertEquals(TransitionDecision.IDEMPOTENT, BookingLifecyclePolicy.reservationTransition(
                        ReservationStatus.CONFIRMED, ReservationStatus.CONFIRMED)),
                () -> assertEquals(TransitionDecision.IDEMPOTENT, BookingLifecyclePolicy.paymentTransition(
                        PaymentStatus.SUCCEEDED, PaymentStatus.SUCCEEDED)),
                () -> assertEquals(TransitionDecision.IDEMPOTENT, BookingLifecyclePolicy.refundTransition(
                        RefundStatus.FAILED, RefundStatus.FAILED)));
    }

    @Test
    void latePaymentSuccess_RequiresReconciliationInsteadOfResurrectingReservation() {
        assertAll(
                () -> assertEquals(TransitionDecision.APPLY, BookingLifecyclePolicy.paymentSuccess(
                        ReservationStatus.PENDING_PAYMENT, PaymentStatus.PENDING)),
                () -> assertEquals(TransitionDecision.IDEMPOTENT, BookingLifecyclePolicy.paymentSuccess(
                        ReservationStatus.CONFIRMED, PaymentStatus.SUCCEEDED)),
                () -> assertEquals(TransitionDecision.RECONCILIATION_REQUIRED, BookingLifecyclePolicy.paymentSuccess(
                        ReservationStatus.CANCELLED, PaymentStatus.PENDING)),
                () -> assertEquals(TransitionDecision.RECONCILIATION_REQUIRED, BookingLifecyclePolicy.paymentSuccess(
                        ReservationStatus.EXPIRED, PaymentStatus.CREATED)),
                () -> assertEquals(TransitionDecision.RECONCILIATION_REQUIRED, BookingLifecyclePolicy.paymentSuccess(
                        ReservationStatus.PENDING_PAYMENT, PaymentStatus.EXPIRED)));
    }
}
