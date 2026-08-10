package com.hotel.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.exceptions.ApiErrorResponse;
import com.hotel.exceptions.CorrelationIdSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JwtAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpStatus.FORBIDDEN.value());
        String correlationId = CorrelationIdSupport.resolve(request);
        response.setHeader(CorrelationIdSupport.HEADER, correlationId);
        ApiErrorResponse body = new ApiErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                "ACCESS_DENIED",
                "Bạn không có quyền thực hiện thao tác này.",
                correlationId,
                Map.of(),
                false,
                null,
                request.getRequestURI());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
