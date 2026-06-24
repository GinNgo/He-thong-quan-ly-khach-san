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

        if (permission == null) {
            return true; // No permission required
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
            return false;
        }

        // Allow SUPER_ADMIN, ADMIN or username 'admin' bypass
        boolean isAdminRole = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("SUPER_ADMIN") || a.getAuthority().equals("ADMIN"));
        
        if (isAdminRole || authentication.getName().equals("admin")) {
            return true;
        }

        if (!(authentication.getPrincipal() instanceof CustomUserDetails)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden");
            return false;
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Map<FunctionCode, Integer> masks = userDetails.getPermissionMasks();

        Integer userMask = masks.get(permission.function());
        if (userMask == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden: No access to function " + permission.function());
            return false;
        }

        if ((userMask & permission.action()) != permission.action()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden: Missing required action mask");
            return false;
        }

        return true;
    }
}
