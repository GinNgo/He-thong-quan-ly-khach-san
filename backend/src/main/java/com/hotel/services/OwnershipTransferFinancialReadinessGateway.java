package com.hotel.services;

import java.time.LocalDateTime;

public interface OwnershipTransferFinancialReadinessGateway {
    Readiness assess(Long propertyId);

    enum State { READY, BLOCKED, UNAVAILABLE }
    record Disclosure(String subscriptionPlan, LocalDateTime renewalAt, int overdueInvoiceCount,
                      int openDisputeCount, int pendingRefundCount, int pendingContractChangeCount) {}
    record Readiness(State state, Disclosure disclosure, String reason) {}
}
