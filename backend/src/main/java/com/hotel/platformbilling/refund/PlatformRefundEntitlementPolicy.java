package com.hotel.platformbilling.refund;

import com.hotel.platformbilling.payment.PlatformFinancialTransaction;

public interface PlatformRefundEntitlementPolicy {

    String version();

    PolicyEffect apply(
            PlatformRefundRequest refund,
            PlatformFinancialTransaction refundTransaction,
            String correlationId);

    record PolicyEffect(String contractPublicId, String entitlementStatus, String historyAction) {
    }
}
