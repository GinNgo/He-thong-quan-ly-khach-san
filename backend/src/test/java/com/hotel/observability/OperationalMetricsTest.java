package com.hotel.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperationalMetricsTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void observesScheduledJobsWithCorrelationAndAlertableFailureCounters() throws Throwable {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OperationalMetrics metrics = new OperationalMetrics(registry);

        Object result = metrics.observeJob("PaymentRecovery.run", () -> {
            assertThat(MDC.get("correlationId")).isNotBlank();
            return "done";
        });

        assertThat(result).isEqualTo("done");
        assertThat(MDC.get("correlationId")).isNull();
        assertThat(registry.get("hotel.scheduled.jobs")
                .tags("job", "paymentrecovery.run", "outcome", "success")
                .timer().count()).isEqualTo(1);

        assertThatThrownBy(() -> metrics.observeJob("PaymentRecovery.run", () -> {
            throw new IllegalStateException("provider secret must not become a metric tag");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(registry.get("hotel.operational.failures")
                .tags("layer", "scheduled_job", "reason", "paymentrecovery.run")
                .counter().count()).isEqualTo(1);
        assertThat(registry.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags()).allSatisfy(tag ->
                        assertThat(tag.getValue()).doesNotContain("provider secret")));
    }
}
