package com.hotel.security;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hibernate.Session;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

@Component
public class TenantFilterInterceptor implements HandlerInterceptor {

    private static final List<String> FILTER_NAMES = List.of(
            "chatMessageTenantFilter",
            "paymentSessionTenantFilter",
            "refundRequestTenantFilter",
            "refundAttemptTenantFilter",
            "reservationHoldTenantFilter",
            "supportConversationTenantFilter",
            "supportConversationEventTenantFilter",
            "financialAuditTenantFilter",
            "housekeepingTaskTenantFilter",
            "maintenanceWorkOrderTenantFilter",
            "maintenanceWorkOrderHistoryTenantFilter",
            "hotelServiceTenantFilter",
            "propertyMediaTenantFilter",
            "propertyImageTenantFilter",
            "propertyAmenityTenantFilter",
            "roomTypeAmenityTenantFilter",
            "propertyPolicyTenantFilter",
            "roomTenantFilter",
            "roomTypeTenantFilter",
            "reservationTenantFilter",
<<<<<<< HEAD
            "reservationAmendmentTenantFilter",
=======
            "promotionCampaignTenantFilter",
            "promotionRedemptionTenantFilter",
            "membershipTierTenantFilter",
            "customerMembershipTenantFilter",
            "sponsoredPlacementTenantFilter",
>>>>>>> codex/ui-functional-audit-polish
            "propertyPaymentConfigurationTenantFilter",
            "propertyPaymentMethodTenantFilter",
            "propertyPaymentAttemptTenantFilter",
            "propertyFinancialTransactionTenantFilter",
            "bookingFinancialSummaryTenantFilter",
            "reservationChargeLineTenantFilter",
            "checkoutOverrideTenantFilter",
            "propertyInvoiceTenantFilter",
            "propertyInvoiceLineTenantFilter",
            "propertyInvoiceAllocationTenantFilter",
            "propertyCreditNoteTenantFilter",
            "propertyCreditNoteLineTenantFilter");

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (isPublicPath(request.getRequestURI())) {
            return true;
        }
        Long hotelId = currentHotelId();
        if (hotelId == null || isSystemAdministrator()) {
            return true;
        }
        Session session = entityManager.unwrap(Session.class);
        FILTER_NAMES.forEach(name -> {
            if (session.getSessionFactory().getDefinedFilterNames().contains(name)) {
                session.enableFilter(name).setParameter("hotelId", hotelId);
            }
        });
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        if (!entityManager.isJoinedToTransaction()) {
            return;
        }
        Session session = entityManager.unwrap(Session.class);
        FILTER_NAMES.forEach(name -> {
            if (session.getSessionFactory().getDefinedFilterNames().contains(name) && session.getEnabledFilter(name) != null) {
                session.disableFilter(name);
            }
        });
    }

    private Long currentHotelId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails details)) {
            return null;
        }
        return details.getHotelId();
    }

    private boolean isSystemAdministrator() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority().replace("ROLE_", ""))
                .anyMatch("SUPER_ADMIN"::equals);
    }

    private boolean isPublicPath(String path) {
        return path.startsWith("/api/public/")
                || path.startsWith("/api/v1/hotels/public/")
                || path.startsWith("/api/room-types/public/")
                || path.startsWith("/api/reservations/public/")
                || path.equals("/api/rooms/search")
                || path.equals("/api/subscriptions/plans");
    }
}
