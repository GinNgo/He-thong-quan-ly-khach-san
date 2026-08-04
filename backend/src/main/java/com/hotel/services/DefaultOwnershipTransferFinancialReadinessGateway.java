package com.hotel.services;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultOwnershipTransferFinancialReadinessGateway implements OwnershipTransferFinancialReadinessGateway {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public Readiness assess(Long propertyId) {
        try {
            int refunds = count("SELECT COUNT_BIG(*) FROM platform_refund_requests r JOIN platform_subscription_orders o ON o.id=r.order_id WHERE o.target_hotel_id=? AND r.status IN ('REQUESTED','PENDING_APPROVAL','PENDING_PROVIDER')", propertyId);
            int contracts = count("SELECT COUNT_BIG(*) FROM platform_subscription_orders WHERE target_hotel_id=? AND operation IN ('UPGRADE','DOWNGRADE','RENEW') AND status IN ('CREATED','PENDING_PAYMENT','PAID')", propertyId);
            Disclosure disclosure = new Disclosure(null, null, 0, 0, refunds, contracts);
            if (refunds > 0 || contracts > 0) return new Readiness(State.BLOCKED, disclosure, "Pending subscription financial operations exist.");
            return new Readiness(State.UNAVAILABLE, disclosure, "Overdue invoice and open subscription dispute sources are unavailable.");
        } catch (RuntimeException unavailable) {
            return new Readiness(State.UNAVAILABLE, null, "Subscription financial readiness could not be verified.");
        }
    }

    private int count(String sql, Long propertyId) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, propertyId);
        return value == null ? 0 : Math.toIntExact(value);
    }
}
