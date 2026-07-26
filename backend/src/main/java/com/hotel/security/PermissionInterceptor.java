package com.hotel.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class PermissionInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;

    public PermissionInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

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
            writeJsonError(response, request, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "Full authentication is required to access this resource");
            return false;
        }

        boolean isSuperAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "SUPER_ADMIN".equals(authority.getAuthority())
                        || "ROLE_SUPER_ADMIN".equals(authority.getAuthority()));

        if (!(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            if (isSuperAdmin) {
                return true;
            }
            writeJsonError(response, request, HttpServletResponse.SC_FORBIDDEN, "ACCESS_DENIED", "Access is denied");
            return false;
        }

        if (permission != null && !isSuperAdmin) {
            Map<FunctionCode, Integer> masks = userDetails.getPermissionMasks();
            Integer userMask = masks == null ? null : masks.get(permission.function());
            if (userMask == null) {
                writeJsonError(response, request, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN_PERMISSION",
                        "No access to function " + permission.function());
                return false;
            }

            if ((userMask & permission.action()) != permission.action()) {
                writeJsonError(response, request, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN_PERMISSION",
                        "Missing required action mask");
                return false;
            }
        }

        if (requireFeature != null && !isSuperAdmin) {
            Map<String, Integer> featureLimits = userDetails.getFeatureLimits();
            Integer limit = featureLimits == null ? null : featureLimits.get(requireFeature.value());
            if (limit == null || (limit != -1 && limit <= 0)) {
                writeJsonError(response, request, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN_FEATURE",
                        "Upgrade your subscription to use this feature (" + requireFeature.value() + ")");
                return false;
            }
        }

        return true;
    }

    private void writeJsonError(HttpServletResponse response, HttpServletRequest request,
                                int status, String code, String message) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(status);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("code", code);
        body.put("message", message);
        body.put("path", request.getRequestURI());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
