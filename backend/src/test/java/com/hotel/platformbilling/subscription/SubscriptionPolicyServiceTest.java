package com.hotel.platformbilling.subscription;

import com.hotel.entities.Hotel;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.platformbilling.order.SubscriptionOrder;
import com.hotel.services.PropertyAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionPolicyServiceTest {

    @Mock private PropertyAccessService propertyAccessService;

    private SubscriptionPolicyService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionPolicyService(propertyAccessService);
    }

    @Test
    void blocksDowngradeBeforeAnyOrderCanBeCreated() {
        Hotel hotel = new Hotel();
        hotel.setId(20L);
        when(propertyAccessService.requireAssignedHotel(20L)).thenReturn(hotel);

        FinancialException exception = assertThrows(FinancialException.class, () ->
                service.createDowngradeOrder(new SubscriptionPolicyService.DowngradeOrderCommand(
                        20L, 31L, "downgrade-key")));

        assertEquals(FinancialErrorCode.POLICY_NOT_CONFIGURED, exception.code());
        assertTrue(exception.getMessage().contains("downgrade"));
        assertEquals("POLICY=SUBSCRIPTION_DOWNGRADE;STATUS=NOT_CONFIGURED", exception.currentState());
        verify(propertyAccessService).requireAssignedHotel(20L);
    }

    @Test
    void blocksProrationForUpgradeAndDowngradeWithoutAcceptingClientAmounts() {
        Hotel hotel = new Hotel();
        hotel.setId(20L);
        when(propertyAccessService.requireAssignedHotel(20L)).thenReturn(hotel);

        FinancialException exception = assertThrows(FinancialException.class, () ->
                service.requireProrationPolicy(new SubscriptionPolicyService.ProrationPolicyCommand(
                        20L, SubscriptionOrder.Operation.UPGRADE)));

        assertEquals(FinancialErrorCode.POLICY_NOT_CONFIGURED, exception.code());
        assertTrue(exception.getMessage().contains("Proration"));
        assertEquals("POLICY=SUBSCRIPTION_PRORATION;STATUS=NOT_CONFIGURED", exception.currentState());
        verify(propertyAccessService).requireAssignedHotel(20L);
    }

    @Test
    void reportsTruthfulPolicyAvailabilityForTheFutureUi() {
        SubscriptionPolicyService.PolicyAvailability availability = service.availability();

        assertFalse(availability.downgradeConfigured());
        assertFalse(availability.prorationConfigured());
        assertEquals(FinancialErrorCode.POLICY_NOT_CONFIGURED, availability.errorCode());
        assertTrue(availability.downgradeMessage().contains("not configured"));
        assertTrue(availability.prorationMessage().contains("not configured"));
    }

    @Test
    void preservesTenantAccessDenialInsteadOfLeakingPolicyDetails() {
        when(propertyAccessService.requireAssignedHotel(20L))
                .thenThrow(new FinancialException(FinancialErrorCode.TENANT_ACCESS_DENIED));

        FinancialException exception = assertThrows(FinancialException.class, () ->
                service.createDowngradeOrder(new SubscriptionPolicyService.DowngradeOrderCommand(
                        20L, 31L, "downgrade-key")));

        assertEquals(FinancialErrorCode.TENANT_ACCESS_DENIED, exception.code());
    }
}
