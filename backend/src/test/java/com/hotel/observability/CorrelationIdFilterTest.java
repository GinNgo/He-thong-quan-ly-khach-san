package com.hotel.observability;

import com.hotel.exceptions.CorrelationIdSupport;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void propagatesOneSanitizedCorrelationIdAndRecordsLowCardinalityHttpMetrics() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CorrelationIdFilter filter = new CorrelationIdFilter(new OperationalMetrics(registry));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/hotels/42");
        request.addHeader(CorrelationIdSupport.HEADER, "request / correlation");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            servletRequest.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/hotels/{id}");
            ((MockHttpServletResponse) servletResponse).setStatus(503);
            assertThat(MDC.get("correlationId")).isEqualTo("request-correlation");
            assertThat(CorrelationIdSupport.resolve((MockHttpServletRequest) servletRequest))
                    .isEqualTo("request-correlation");
        });

        assertThat(response.getHeader(CorrelationIdSupport.HEADER)).isEqualTo("request-correlation");
        assertThat(MDC.get("correlationId")).isNull();
        assertThat(registry.get("hotel.http.server.requests")
                .tags("method", "get", "outcome", "server_error")
                .timer().count()).isEqualTo(1);
        assertThat(registry.get("hotel.operational.failures")
                .tags("layer", "http", "reason", "status_5xx")
                .counter().count()).isEqualTo(1);
        assertThat(registry.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags()).allSatisfy(tag ->
                        assertThat(tag.getValue()).doesNotContain("/api/hotels/42")));
    }

    @Test
    void recordsSuccessfulRequestsWithoutCreatingFailureCounters() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OperationalMetrics metrics = new OperationalMetrics(registry);
        CorrelationIdFilter filter = new CorrelationIdFilter(metrics);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
        });

        assertThat(response.getHeader(CorrelationIdSupport.HEADER)).isNotBlank();
        assertThat(registry.get("hotel.http.server.requests")
                .tags("method", "post", "outcome", "success")
                .timer().totalTime(java.util.concurrent.TimeUnit.NANOSECONDS)).isGreaterThanOrEqualTo(0);
        assertThat(registry.find("hotel.operational.failures").counters()).isEmpty();
        metrics.recordHttp("GET", 200, Duration.ZERO);
    }
}
