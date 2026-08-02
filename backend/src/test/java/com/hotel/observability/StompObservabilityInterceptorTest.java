package com.hotel.observability;

import com.hotel.exceptions.CorrelationIdSupport;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class StompObservabilityInterceptorTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void reusesTheSessionCorrelationIdAndRecordsFrameOutcome() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        StompObservabilityInterceptor interceptor = new StompObservabilityInterceptor(
                new OperationalMetrics(registry));
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.CONNECT);
        accessor.setSessionAttributes(new HashMap<>());
        accessor.setNativeHeader(CorrelationIdSupport.HEADER, "stomp / correlation");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        MessageChannel channel = mock(MessageChannel.class);

        interceptor.preSend(message, channel);

        assertThat(MDC.get("correlationId")).isEqualTo("stomp-correlation");
        assertThat(accessor.getSessionAttributes())
                .containsEntry(StompObservabilityInterceptor.SESSION_CORRELATION_ATTRIBUTE, "stomp-correlation");

        interceptor.afterSendCompletion(message, channel, true, null);

        assertThat(MDC.get("correlationId")).isNull();
        assertThat(registry.get("hotel.stomp.frames")
                .tags("frameType", "connect", "outcome", "success")
                .timer().count()).isEqualTo(1);
    }

    @Test
    void incrementsTheAlertCounterWhenAFrameIsRejected() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        StompObservabilityInterceptor interceptor = new StompObservabilityInterceptor(
                new OperationalMetrics(registry));
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.SUBSCRIBE);
        accessor.setSessionAttributes(new HashMap<>());
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        MessageChannel channel = mock(MessageChannel.class);

        interceptor.preSend(message, channel);
        interceptor.afterSendCompletion(message, channel, false, new SecurityException("denied"));

        assertThat(registry.get("hotel.operational.failures")
                .tags("layer", "stomp", "reason", "frame_rejected")
                .counter().count()).isEqualTo(1);
    }
}
