package com.hotel.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

@Component
public class PermissionInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        Permission permission = handlerMethod.getMethodAnnotation(Permission.class);
        
        if (permission == null) {
            permission = handlerMethod.getBeanType().getAnnotation(Permission.class);
        }

        RequireFeature requireFeature = handlerMethod.getMethodAnnotation(RequireFeature.class);
        if (requireFeature == null) {
            requireFeature = handlerMethod.getBeanType().getAnnotation(RequireFeature.class);
        }

        if (permission == null && requireFeature == null) {
            return true;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
            return false;
        }

        boolean isSuperAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "SUPER_ADMIN".equals(authority.getAuthority())
                        || "ROLE_SUPER_ADMIN".equals(authority.getAuthority()));

        if (!(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            if (isSuperAdmin) {
                return true;
            }
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden");
            return false;
        }

        if (permission != null && !isSuperAdmin) {
            Map<FunctionCode, Integer> masks = userDetails.getPermissionMasks();
            Integer userMask = masks == null ? null : masks.get(permission.function());
            if (userMask == null) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "Forbidden: No access to function " + permission.function());
                return false;
            }

            if ((userMask & permission.action()) != permission.action()) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden: Missing required action mask");
                return false;
            }
        }

        if (requireFeature != null && !isSuperAdmin) {
            Map<String, Integer> featureLimits = userDetails.getFeatureLimits();
            Integer limit = featureLimits == null ? null : featureLimits.get(requireFeature.value());
            if (limit == null || (limit != -1 && limit <= 0)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "Forbidden: Upgrade your subscription to use this feature (" + requireFeature.value() + ")");
                return false;
            }
        }

        return true;
    }
}
