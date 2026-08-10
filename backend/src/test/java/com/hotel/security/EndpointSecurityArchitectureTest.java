package com.hotel.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.fail;

class EndpointSecurityArchitectureTest {

    // Controllers skipped entirely (own auth flow or non-production)
    private static final List<String> SKIP_CONTROLLERS = List.of(
            "MockPaymentController", "AuthController"
    );

    // Endpoints intentionally public via SecurityConfig.permitAll()
    // Format: "ControllerSimpleName.methodName"
    private static final Set<String> KNOWN_PUBLIC_ENDPOINTS = Set.of(
            // VNPay callback — permitAll, protected by signature check
            "PaymentController.vnpayCallback",
            // Public hotel browsing — /api/v1/hotels/public/** or permitAll search
            "HotelController.getHotelById",
            "HotelController.searchHotels",
            // Partner registration — POST /api/partner/register
            "PropertyRegistrationController.registerProperty",
            // Public discovery — /api/public/**
            "PublicDiscoveryController.popularDestinations",
            "PublicDiscoveryController.suggestions",
            "PublicDiscoveryController.recommendationDestinations",
            "PublicDiscoveryController.recommendations",
            "PublicHomeSpotlightController.spotlights",
            "PublicPromotionController.list",
            "PublicQuoteController.quote",
            "PropertySearchController.searchProperties",
            // Public customer assistant used by the guest-facing chat widget.
            "AiController.customerChat",
            "AiController.customerChatStream",
            // Location lookup — /api/public/**
            "LocationController.search",
            "LocationController.getPopularProvinces",
            "LocationController.getWards",
            "LocationController.getProvinces",
            // Public room types — /api/room-types/public/**
            "RoomTypeController.getRoomTypesByHotelId",
            // Public reservation — /api/reservations/public/**
            "ReservationController.createPublicReservation",
            // Subscription plans — /api/subscriptions/plans permitAll
            "SubscriptionController.getAllPlans",
            // File serving — static assets
            "FileUploadController.serveFile",
            // Token confirmation is intentionally public; the one-time token is the credential.
            "EmailVerificationController.confirm"
    );

    // Endpoints protected by SecurityConfig.anyRequest().authenticated(). Keep this list narrow so
    // a new mutation cannot silently rely on authentication when role/permission checks are required.
    private static final Set<String> KNOWN_AUTHENTICATED_ENDPOINTS = Set.of(
            "HotelController.getMyHotels"
    );

    @Test
    void allEndpointMethodsShouldHaveSecurityAnnotation() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<String> unsecured = new ArrayList<>();

        for (BeanDefinition bd : scanner.findCandidateComponents("com.hotel.controllers")) {
            Class<?> clazz = Class.forName(bd.getBeanClassName());
            if (SKIP_CONTROLLERS.contains(clazz.getSimpleName())) {
                continue;
            }

            for (Method method : clazz.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic()) {
                    continue;
                }

                boolean isEndpoint = method.isAnnotationPresent(GetMapping.class)
                        || method.isAnnotationPresent(PostMapping.class)
                        || method.isAnnotationPresent(PutMapping.class)
                        || method.isAnnotationPresent(DeleteMapping.class)
                        || method.isAnnotationPresent(PatchMapping.class)
                        || method.isAnnotationPresent(RequestMapping.class);

                if (!isEndpoint) {
                    continue;
                }

                String key = clazz.getSimpleName() + "." + method.getName();

                if (KNOWN_PUBLIC_ENDPOINTS.contains(key) || KNOWN_AUTHENTICATED_ENDPOINTS.contains(key)) {
                    continue; // explicit request-matcher contract in SecurityConfig
                }

                boolean hasSecurity = method.isAnnotationPresent(PreAuthorize.class)
                        || method.isAnnotationPresent(Permission.class)
                        || method.isAnnotationPresent(RequireFeature.class);

                if (!hasSecurity) {
                    hasSecurity = clazz.isAnnotationPresent(PreAuthorize.class)
                            || clazz.isAnnotationPresent(Permission.class)
                            || clazz.isAnnotationPresent(RequireFeature.class);
                }

                boolean hasDeprecatedSecured = method.isAnnotationPresent(
                        org.springframework.security.access.annotation.Secured.class);

                if (hasDeprecatedSecured) {
                    unsecured.add(key + " uses deprecated @Secured — migrate to @PreAuthorize");
                } else if (!hasSecurity) {
                    unsecured.add(key
                            + " is an endpoint WITHOUT security annotation"
                            + " (@PreAuthorize/@Permission/@RequireFeature)");
                }
            }
        }

        if (!unsecured.isEmpty()) {
            fail("Endpoints lacking security annotations:\n" + String.join("\n", unsecured));
        }
    }
}
