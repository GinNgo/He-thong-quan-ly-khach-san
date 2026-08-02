package com.hotel.observability;

import com.hotel.exceptions.CorrelationIdSupport;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.time.Duration;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    private final OperationalMetrics metrics;

    public CorrelationIdFilter(OperationalMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String correlationId = CorrelationIdSupport.resolve(request);
        response.setHeader(CorrelationIdSupport.HEADER, correlationId);
        long startedAt = System.nanoTime();
        Throwable failure = null;

        try (MDC.MDCCloseable ignored = MDC.putCloseable("correlationId", correlationId)) {
            try {
                filterChain.doFilter(request, response);
            } catch (ServletException | IOException | RuntimeException exception) {
                failure = exception;
                throw exception;
            } finally {
                int status = failure == null ? response.getStatus() : HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
                Duration duration = Duration.ofNanos(System.nanoTime() - startedAt);
                metrics.recordHttp(request.getMethod(), status, duration);
                log.info("HTTP_REQUEST method={} route={} status={} durationMs={} correlationId={}",
                        request.getMethod(), routeTemplate(request), status, duration.toMillis(), correlationId);
            }
        }
    }

    private String routeTemplate(HttpServletRequest request) {
        Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return route instanceof String value && !value.isBlank() ? value : "unmatched";
    }
}
