package com.hotel.paymentprovider.domain;

import org.junit.jupiter.api.Test;

import static com.hotel.paymentprovider.domain.FinancialStates.PaymentState;
import static com.hotel.paymentprovider.domain.FinancialStates.SubscriptionOrderState;
import static com.hotel.paymentprovider.domain.FinancialTransitionPolicy.Decision;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FinancialTransitionPolicyTest {

    @Test
    void acceptsSuccessAndMakesReplayIdempotent() {
        assertEquals(Decision.APPLY, FinancialTransitionPolicy.payment(PaymentState.PENDING, PaymentState.SUCCESS));
        assertEquals(Decision.IDEMPOTENT, FinancialTransitionPolicy.payment(PaymentState.SUCCESS, PaymentState.SUCCESS));
    }

    @Test
    void rejectsResurrectionFromExpiredPayment() {
        assertEquals(Decision.REJECT, FinancialTransitionPolicy.payment(PaymentState.EXPIRED, PaymentState.SUCCESS));
    }

    @Test
    void appliesSubscriptionOnlyAfterPayment() {
        assertEquals(Decision.APPLY, FinancialTransitionPolicy.subscription(SubscriptionOrderState.PAID, SubscriptionOrderState.APPLIED));
        assertEquals(Decision.REJECT, FinancialTransitionPolicy.subscription(SubscriptionOrderState.FAILED, SubscriptionOrderState.APPLIED));
    }
}
