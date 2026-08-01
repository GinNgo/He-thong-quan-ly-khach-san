package com.hotel.platformbilling.subscription;

import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.platformbilling.order.SubscriptionOrder;
import com.hotel.platformbilling.order.SubscriptionOrderService;
import com.hotel.services.PropertyAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionPolicyService {

    static final String DOWNGRADE_POLICY = "SUBSCRIPTION_DOWNGRADE";
    static final String PRORATION_POLICY = "SUBSCRIPTION_PRORATION";

    private static final String DOWNGRADE_MESSAGE =
            "Automatic subscription downgrade is not configured; no order or entitlement change was created.";
    private static final String PRORATION_MESSAGE =
            "Proration and automatic subscription credits are not configured; no financial change was created.";

    private final PropertyAccessService propertyAccessService;

    public SubscriptionPolicyService(PropertyAccessService propertyAccessService) {
        this.propertyAccessService = propertyAccessService;
    }

    @Transactional(readOnly = true)
    public SubscriptionOrderService.OrderResponse createDowngradeOrder(DowngradeOrderCommand command) {
        validateDowngrade(command);
        propertyAccessService.requireManagedHotel(command.targetHotelId());
        throw blocked(DOWNGRADE_POLICY, DOWNGRADE_MESSAGE);
    }

    @Transactional(readOnly = true)
    public void requireProrationPolicy(ProrationPolicyCommand command) {
        validateProration(command);
        propertyAccessService.requireManagedHotel(command.targetHotelId());
        throw blocked(PRORATION_POLICY, PRORATION_MESSAGE);
    }

    public PolicyAvailability availability() {
        return new PolicyAvailability(
                false,
                false,
                FinancialErrorCode.POLICY_NOT_CONFIGURED,
                DOWNGRADE_MESSAGE,
                PRORATION_MESSAGE);
    }

    private void validateDowngrade(DowngradeOrderCommand command) {
        if (command == null || command.targetHotelId() == null || command.targetPlanId() == null) {
            throw new IllegalArgumentException("Target property and downgrade plan are required.");
        }
        requireText(command.idempotencyKey(), "idempotencyKey");
    }

    private void validateProration(ProrationPolicyCommand command) {
        if (command == null || command.targetHotelId() == null || command.operation() == null) {
            throw new IllegalArgumentException("Target property and lifecycle operation are required.");
        }
        if (command.operation() != SubscriptionOrder.Operation.UPGRADE
                && command.operation() != SubscriptionOrder.Operation.DOWNGRADE) {
            throw new IllegalArgumentException("Proration is only relevant to upgrade or downgrade operations.");
        }
    }

    private FinancialException blocked(String policy, String message) {
        return new FinancialException(
                FinancialErrorCode.POLICY_NOT_CONFIGURED,
                message,
                null,
                "POLICY=" + policy + ";STATUS=NOT_CONFIGURED",
                null);
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        String normalized = value.trim();
        if (normalized.length() > 160) {
            throw new IllegalArgumentException(field + " is too long.");
        }
        return normalized;
    }

    public record DowngradeOrderCommand(Long targetHotelId, Long targetPlanId, String idempotencyKey) {
    }

    public record ProrationPolicyCommand(Long targetHotelId, SubscriptionOrder.Operation operation) {
    }

    public record PolicyAvailability(
            boolean downgradeConfigured,
            boolean prorationConfigured,
            FinancialErrorCode errorCode,
            String downgradeMessage,
            String prorationMessage) {
    }
}
