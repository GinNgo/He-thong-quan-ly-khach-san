package com.hotel.observability;

import com.hotel.exceptions.CorrelationIdSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
public class StompObservabilityInterceptor implements ChannelInterceptor {

    public static final String SESSION_CORRELATION_ATTRIBUTE = "luxestay.correlationId";

    private static final Logger log = LoggerFactory.getLogger(StompObservabilityInterceptor.class);

    private final OperationalMetrics metrics;
    private final ThreadLocal<FrameObservation> observation = new ThreadLocal<>();

    public StompObservabilityInterceptor(OperationalMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        SimpMessageHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message, SimpMessageHeaderAccessor.class);
        String frameType = accessor == null || accessor.getMessageType() == null
                ? "unknown"
                : accessor.getMessageType().name();
        String correlationId = correlationId(accessor);
        observation.set(new FrameObservation(frameType, correlationId, System.nanoTime()));
        MDC.put("correlationId", correlationId);
        return message;
    }

    @Override
    public void afterSendCompletion(
            Message<?> message,
            MessageChannel channel,
            boolean sent,
            Exception exception) {
        FrameObservation current = observation.get();
        try {
            if (current == null) {
                return;
            }
            boolean failed = exception != null || !sent;
            Duration duration = Duration.ofNanos(System.nanoTime() - current.startedAt());
            metrics.recordStomp(current.frameType(), failed, duration);
            if (failed) {
                log.warn("STOMP_FRAME type={} outcome=failure reason={} durationMs={} correlationId={}",
                        current.frameType(), exception == null ? "not_sent" : exception.getClass().getSimpleName(),
                        duration.toMillis(), current.correlationId());
            }
        } finally {
            observation.remove();
            MDC.remove("correlationId");
        }
    }

    private String correlationId(SimpMessageHeaderAccessor accessor) {
        String supplied = accessor == null ? null : accessor.getFirstNativeHeader(CorrelationIdSupport.HEADER);
        Map<String, Object> sessionAttributes = accessor == null ? null : accessor.getSessionAttributes();
        if ((supplied == null || supplied.isBlank()) && sessionAttributes != null) {
            Object existing = sessionAttributes.get(SESSION_CORRELATION_ATTRIBUTE);
            supplied = existing instanceof String value ? value : null;
        }
        String correlationId = CorrelationIdSupport.normalize(supplied);
        if (sessionAttributes != null) {
            sessionAttributes.put(SESSION_CORRELATION_ATTRIBUTE, correlationId);
        }
        return correlationId;
    }

    private record FrameObservation(String frameType, String correlationId, long startedAt) {
    }
}
