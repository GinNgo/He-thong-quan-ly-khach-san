package com.hotel.domain.lifecycle;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class BookingLifecyclePolicy {
    private static final Map<ReservationStatus, Set<ReservationStatus>> RESERVATION_TRANSITIONS = Map.of(
            ReservationStatus.PENDING_PAYMENT, Set.of(
                    ReservationStatus.CONFIRMED,
                    ReservationStatus.CANCELLED,
                    ReservationStatus.EXPIRED,
                    ReservationStatus.REJECTED),
            ReservationStatus.CONFIRMED, Set.of(
                    ReservationStatus.CHECKED_IN,
                    ReservationStatus.CANCELLED,
                    ReservationStatus.NO_SHOW),
            ReservationStatus.CHECKED_IN, Set.of(ReservationStatus.CHECKED_OUT),
            ReservationStatus.CHECKED_OUT, Set.of(ReservationStatus.COMPLETED));

    private static final Map<PaymentStatus, Set<PaymentStatus>> PAYMENT_TRANSITIONS = Map.of(
            PaymentStatus.CREATED, Set.of(
                    PaymentStatus.PENDING,
                    PaymentStatus.SUCCEEDED,
                    PaymentStatus.FAILED,
                    PaymentStatus.EXPIRED),
            PaymentStatus.PENDING, Set.of(
                    PaymentStatus.SUCCEEDED,
                    PaymentStatus.FAILED,
                    PaymentStatus.EXPIRED));

    private static final Map<RefundStatus, Set<RefundStatus>> REFUND_TRANSITIONS = Map.of(
            RefundStatus.REQUESTED, Set.of(
                    RefundStatus.PENDING_PROVIDER,
                    RefundStatus.SUCCEEDED,
                    RefundStatus.FAILED),
            RefundStatus.PENDING_PROVIDER, Set.of(
                    RefundStatus.SUCCEEDED,
                    RefundStatus.FAILED));

    private BookingLifecyclePolicy() {
    }

    public static TransitionDecision reservationTransition(ReservationStatus current, ReservationStatus target) {
        return decide(current, target, RESERVATION_TRANSITIONS);
    }

    public static TransitionDecision paymentTransition(PaymentStatus current, PaymentStatus target) {
        return decide(current, target, PAYMENT_TRANSITIONS);
    }

    public static TransitionDecision refundTransition(RefundStatus current, RefundStatus target) {
        return decide(current, target, REFUND_TRANSITIONS);
    }

    public static TransitionDecision paymentSuccess(
            ReservationStatus reservationStatus,
            PaymentStatus paymentStatus) {
        Objects.requireNonNull(reservationStatus, "reservationStatus");
        Objects.requireNonNull(paymentStatus, "paymentStatus");

        if (paymentStatus == PaymentStatus.SUCCEEDED) {
            return TransitionDecision.IDEMPOTENT;
        }
        if (reservationStatus == ReservationStatus.CANCELLED
                || reservationStatus == ReservationStatus.EXPIRED
                || paymentStatus == PaymentStatus.FAILED
                || paymentStatus == PaymentStatus.EXPIRED) {
            return TransitionDecision.RECONCILIATION_REQUIRED;
        }
        if (paymentTransition(paymentStatus, PaymentStatus.SUCCEEDED) != TransitionDecision.APPLY) {
            return TransitionDecision.REJECT;
        }
        return reservationStatus == ReservationStatus.PENDING_PAYMENT
                || reservationStatus == ReservationStatus.CONFIRMED
                ? TransitionDecision.APPLY
                : TransitionDecision.REJECT;
    }

    private static <S> TransitionDecision decide(S current, S target, Map<S, Set<S>> transitions) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(target, "target");
        if (current.equals(target)) {
            return TransitionDecision.IDEMPOTENT;
        }
        return transitions.getOrDefault(current, Set.of()).contains(target)
                ? TransitionDecision.APPLY
                : TransitionDecision.REJECT;
    }
}
