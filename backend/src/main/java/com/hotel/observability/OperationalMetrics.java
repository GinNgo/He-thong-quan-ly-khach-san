package com.hotel.observability;

import com.hotel.exceptions.CorrelationIdSupport;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class OperationalMetrics {

    private static final Logger log = LoggerFactory.getLogger(OperationalMetrics.class);
    private static final String UNKNOWN = "unknown";

    private final MeterRegistry registry;

    public OperationalMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordHttp(String method, int status, Duration duration) {
        String outcome = status >= 500 ? "server_error" : status >= 400 ? "client_error" : "success";
        Timer.builder("hotel.http.server.requests")
                .description("Hotel API request duration without high-cardinality route or user tags")
                .tags("method", safeTag(method), "outcome", outcome)
                .register(registry)
                .record(duration);
        if (status >= 500) {
            failureCounter("http", "status_5xx").increment();
        }
    }

    public void recordStomp(String frameType, boolean failed, Duration duration) {
        String outcome = failed ? "failure" : "success";
        Timer.builder("hotel.stomp.frames")
                .description("Inbound STOMP frame duration by low-cardinality frame type")
                .tags("frameType", safeTag(frameType), "outcome", outcome)
                .register(registry)
                .record(duration);
        if (failed) {
            failureCounter("stomp", "frame_rejected").increment();
        }
    }

    public void recordExternal(String channel, String operation, boolean failed, Duration duration) {
        String outcome = failed ? "failure" : "success";
        Timer.builder("hotel.external.operations")
                .description("External adapter duration without recipient, payload or provider-secret tags")
                .tags("channel", safeTag(channel), "operation", safeTag(operation), "outcome", outcome)
                .register(registry)
                .record(duration);
        if (failed) {
            failureCounter("external", safeTag(channel) + "_" + safeTag(operation)).increment();
        }
    }

    public void recordAuthLogin(String outcome, String reason) {
        Counter.builder("hotel.auth.login.attempts")
                .description("Credential login outcomes with low-cardinality, pseudonym-free tags")
                .tags("outcome", safeTag(outcome), "reason", safeTag(reason))
                .register(registry)
                .increment();
    }

    public Object observeJob(String jobName, ObservedOperation operation) throws Throwable {
        String safeJobName = safeTag(jobName);
        String correlationId = CorrelationIdSupport.generate();
        long startedAt = System.nanoTime();
        try (MDC.MDCCloseable ignored = MDC.putCloseable("correlationId", correlationId)) {
            try {
                Object result = operation.run();
                recordJob(safeJobName, "success", startedAt);
                log.info("SCHEDULED_JOB name={} outcome=success durationMs={} correlationId={}",
                        safeJobName, elapsedMillis(startedAt), correlationId);
                return result;
            } catch (Throwable exception) {
                recordJob(safeJobName, "failure", startedAt);
                failureCounter("scheduled_job", safeJobName).increment();
                log.error("SCHEDULED_JOB name={} outcome=failure type={} durationMs={} correlationId={}",
                        safeJobName, exception.getClass().getSimpleName(), elapsedMillis(startedAt), correlationId);
                throw exception;
            }
        }
    }

    public void observeJobRun(String jobName, Runnable operation) {
        try {
            observeJob(jobName, () -> {
                operation.run();
                return null;
            });
        } catch (RuntimeException | Error exception) {
            throw exception;
        } catch (Throwable exception) {
            throw new IllegalStateException("Scheduled job failed", exception);
        }
    }

    private void recordJob(String jobName, String outcome, long startedAt) {
        Timer.builder("hotel.scheduled.jobs")
                .description("Scheduled job duration and outcome")
                .tags("job", jobName, "outcome", outcome)
                .register(registry)
                .record(Duration.ofNanos(System.nanoTime() - startedAt));
    }

    private Counter failureCounter(String layer, String reason) {
        return Counter.builder("hotel.operational.failures")
                .description("Alertable operational failures with secret-safe low-cardinality tags")
                .tags("layer", safeTag(layer), "reason", safeTag(reason))
                .register(registry);
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private String safeTag(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        String normalized = value.toLowerCase().replaceAll("[^a-z0-9._-]", "-");
        return normalized.substring(0, Math.min(64, normalized.length()));
    }

    @FunctionalInterface
    public interface ObservedOperation {
        Object run() throws Throwable;
    }
}
