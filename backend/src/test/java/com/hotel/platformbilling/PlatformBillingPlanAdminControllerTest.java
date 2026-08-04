package com.hotel.platformbilling;

import com.hotel.platformbilling.config.PlatformPaymentConfigurationService;
import com.hotel.platformbilling.order.SubscriptionOrderService;
import com.hotel.platformbilling.payment.PlatformPaymentAttemptService;
import com.hotel.platformbilling.subscription.*;
import com.hotel.security.ActionCode;
import com.hotel.security.Permission;
import com.hotel.services.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformBillingPlanAdminControllerTest {
    @Test void delegatesListCreateActivateAndReasonedDeactivateContracts() {
        SubscriptionPlanAdministrationService admin=mock(SubscriptionPlanAdministrationService.class);
        PlatformBillingController controller=controller(admin);
        var command=new SubscriptionPlanAdministrationService.CreateVersionCommand("PRO","Pro","Pro","YEARLY",
                BigDecimal.TEN,1,"YEAR",List.of(new SubscriptionPlanAdministrationService.FeatureLimit("MAX_ROOMS",10)));
        controller.planVersions(); controller.createPlanVersion(command,"create-key","corr");
        controller.activatePlanVersion(2L,"activate-key","corr");
        controller.deactivatePlanVersion(2L,new PlatformBillingController.PlanDeactivationRequest("Retired after replacement"),"deactivate-key","corr");
        verify(admin).list(); verify(admin).createVersion(command,"create-key","corr");
        verify(admin).activate(2L,"activate-key","corr");
        verify(admin).deactivate(2L,"Retired after replacement","deactivate-key","corr");
    }

    @Test void everyPlanAdminRouteRequiresPlatformBillingUpdatePermission() {
        for(String name:List.of("planVersions","createPlanVersion","activatePlanVersion","deactivatePlanVersion")){
            Method method=java.util.Arrays.stream(PlatformBillingController.class.getDeclaredMethods())
                    .filter(item->item.getName().equals(name)).findFirst().orElseThrow();
            Permission permission=method.getAnnotation(Permission.class);
            assertNotNull(permission); assertEquals(ActionCode.UPDATE,permission.action());
        }
    }

    private PlatformBillingController controller(SubscriptionPlanAdministrationService admin){
        return new PlatformBillingController(mock(SubscriptionCatalogService.class),mock(SubscriptionOrderService.class),
                mock(PlatformPaymentAttemptService.class),mock(SubscriptionRenewalService.class),
                mock(SubscriptionUpgradeService.class),mock(SubscriptionPolicyService.class),
                mock(PlatformPaymentConfigurationService.class),mock(PlatformBillingQueryService.class),
                mock(PropertySubscriptionEntitlementService.class),mock(SubscriptionLifecycleService.class),admin);
    }
}
