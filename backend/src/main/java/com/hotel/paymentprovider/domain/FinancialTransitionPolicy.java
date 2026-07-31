package com.hotel.paymentprovider.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.hotel.paymentprovider.domain.FinancialStates.PaymentState;
import static com.hotel.paymentprovider.domain.FinancialStates.SubscriptionOrderState;

public final class FinancialTransitionPolicy {

    public enum Decision {
        APPLY,
        IDEMPOTENT,
        REJECT
    }

    private static final Map<PaymentState, Set<PaymentState>> PAYMENT_TRANSITIONS = Map.of(
            PaymentState.CREATED, EnumSet.of(PaymentState.PENDING, PaymentState.PENDING_VERIFICATION, PaymentState.CANCELLED, PaymentState.EXPIRED),
            PaymentState.PENDING, EnumSet.of(PaymentState.PROCESSING, PaymentState.SUCCESS, PaymentState.FAILED, PaymentState.CANCELLED, PaymentState.EXPIRED),
            PaymentState.PENDING_VERIFICATION, EnumSet.of(PaymentState.SUCCESS, PaymentState.FAILED, PaymentState.CANCELLED, PaymentState.EXPIRED),
            PaymentState.PROCESSING, EnumSet.of(PaymentState.SUCCESS, PaymentState.FAILED, PaymentState.CANCELLED, PaymentState.EXPIRED),
            PaymentState.SUCCESS, EnumSet.of(PaymentState.PARTIALLY_REFUNDED, PaymentState.REFUNDED),
            PaymentState.PARTIALLY_REFUNDED, EnumSet.of(PaymentState.PARTIALLY_REFUNDED, PaymentState.REFUNDED),
            PaymentState.REFUNDED, EnumSet.of(PaymentState.REFUNDED),
            PaymentState.FAILED, EnumSet.of(PaymentState.FAILED),
            PaymentState.CANCELLED, EnumSet.of(PaymentState.CANCELLED),
            PaymentState.EXPIRED, EnumSet.of(PaymentState.EXPIRED)
    );

    private static final Map<SubscriptionOrderState, Set<SubscriptionOrderState>> SUBSCRIPTION_TRANSITIONS = Map.of(
            SubscriptionOrderState.CREATED, EnumSet.of(SubscriptionOrderState.PENDING_PAYMENT, SubscriptionOrderState.CANCELLED, SubscriptionOrderState.EXPIRED),
            SubscriptionOrderState.PENDING_PAYMENT, EnumSet.of(SubscriptionOrderState.PAID, SubscriptionOrderState.FAILED, SubscriptionOrderState.CANCELLED, SubscriptionOrderState.EXPIRED),
            SubscriptionOrderState.PAID, EnumSet.of(SubscriptionOrderState.APPLIED, SubscriptionOrderState.REFUNDED),
            SubscriptionOrderState.APPLIED, EnumSet.of(SubscriptionOrderState.REFUNDED),
            SubscriptionOrderState.FAILED, EnumSet.of(SubscriptionOrderState.FAILED),
            SubscriptionOrderState.CANCELLED, EnumSet.of(SubscriptionOrderState.CANCELLED),
            SubscriptionOrderState.EXPIRED, EnumSet.of(SubscriptionOrderState.EXPIRED),
            SubscriptionOrderState.REFUNDED, EnumSet.of(SubscriptionOrderState.REFUNDED)
    );

    private FinancialTransitionPolicy() {
    }

    public static Decision payment(PaymentState current, PaymentState target) {
        return decide(current, target, PAYMENT_TRANSITIONS);
    }

    public static Decision subscription(SubscriptionOrderState current, SubscriptionOrderState target) {
        return decide(current, target, SUBSCRIPTION_TRANSITIONS);
    }

    private static <T extends Enum<T>> Decision decide(T current, T target, Map<T, Set<T>> transitions) {
        if (current == target) {
            return Decision.IDEMPOTENT;
        }
        return transitions.getOrDefault(current, Set.of()).contains(target)
                ? Decision.APPLY
                : Decision.REJECT;
    }
}
