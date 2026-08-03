package com.hotel.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.exceptions.ApiErrorResponse;
import com.hotel.exceptions.CorrelationIdSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        boolean accountDisabled = AccountDisabledAuthenticationException.causedByAccountDisabled(authException);
        boolean sessionRevoked = authException instanceof SessionRevokedAuthenticationException;
        String code = accountDisabled
                ? AccountDisabledAuthenticationException.ERROR_CODE
                : sessionRevoked ? SessionRevokedAuthenticationException.ERROR_CODE : "UNAUTHORIZED";
        String message = accountDisabled
                ? AccountDisabledAuthenticationException.DEFAULT_MESSAGE
                : sessionRevoked ? SessionRevokedAuthenticationException.DEFAULT_MESSAGE
                : "Full authentication is required to access this resource";
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        String correlationId = CorrelationIdSupport.resolve(request);
        response.setHeader(CorrelationIdSupport.HEADER, correlationId);
        ApiErrorResponse body = new ApiErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                code,
                message,
                correlationId,
                Map.of(),
                false,
                null,
                request.getRequestURI());
        objectMapper.writeValue(response.getOutputStream(), body);
    }

}
