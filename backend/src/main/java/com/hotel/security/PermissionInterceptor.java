package com.hotel.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.exceptions.ApiErrorResponse;
import com.hotel.exceptions.CorrelationIdSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
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
                        permissionDeniedMessage(permission.function(), permission.action()));
                return false;
            }

            if ((userMask & permission.action()) != permission.action()) {
                writeJsonError(response, request, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN_PERMISSION",
                        permissionDeniedMessage(permission.function(), permission.action()));
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

    private String permissionDeniedMessage(FunctionCode function, int actionMask) {
        String action = switch (actionMask) {
            case ActionCode.VIEW -> "xem";
            case ActionCode.CREATE -> "thêm";
            case ActionCode.UPDATE -> "chỉnh sửa";
            case ActionCode.DELETE -> "xóa";
            case ActionCode.EXPORT -> "xuất dữ liệu";
            case ActionCode.APPROVE -> "duyệt";
            case ActionCode.TASK_EXECUTE -> "thực hiện tác vụ";
            default -> "thực hiện thao tác trên";
        };
        return "Bạn không có quyền " + action + " " + functionLabel(function) + ".";
    }

    private String functionLabel(FunctionCode function) {
        return switch (function) {
            case ROOM -> "phòng";
            case ROOM_TYPE -> "loại phòng";
            case RESERVATION -> "đặt phòng";
            case RESERVATION_ASSIGNMENT -> "phân phòng";
            case RESERVATION_CANCEL -> "hủy đặt phòng";
            case RESERVATION_NO_SHOW -> "khách không đến";
            case CHECKIN -> "nhận phòng";
            case CHECKOUT -> "trả phòng";
            case INVOICE -> "hóa đơn";
            case REPORT -> "báo cáo";
            case USER -> "người dùng";
            case ROLE -> "vai trò";
            case ROLE_PERMISSION -> "phân quyền";
            case HOTEL -> "cơ sở lưu trú";
            case HOTEL_SERVICE, RESERVATION_SERVICE -> "dịch vụ";
            case HOUSEKEEPING -> "dọn phòng";
            case PROPERTY_REFUND, PLATFORM_REFUND -> "yêu cầu hoàn tiền";
            case PROPERTY_PAYMENT_CONFIG -> "cấu hình thanh toán";
            case CUSTOMER -> "khách hàng";
            case AUDIT_LOG -> "nhật ký hệ thống";
            default -> "chức năng này";
        };
    }

    private void writeJsonError(HttpServletResponse response, HttpServletRequest request,
                                int status, String code, String message) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(status);
        String correlationId = CorrelationIdSupport.resolve(request);
        response.setHeader(CorrelationIdSupport.HEADER, correlationId);
        ApiErrorResponse body = new ApiErrorResponse(
                status, code, message, correlationId, Map.of(), false, null, request.getRequestURI());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
