package com.hotel.observability;

import com.hotel.services.EmailService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class EmailObservabilityTest {

    @Test
    void recordsMailFailureWithoutRecipientOrExceptionMessageTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        JavaMailSender mailSender = mock(JavaMailSender.class);
        doThrow(new IllegalStateException("smtp-password=secret"))
                .when(mailSender).send(any(org.springframework.mail.SimpleMailMessage.class));
        EmailService service = new EmailService(mailSender, new OperationalMetrics(registry));
        ReflectionTestUtils.setField(service, "fromEmail", "noreply@example.test");

        service.sendBookingConfirmation(
                "guest@example.com", "Guest Name", 42L, "2026-08-10", "2026-08-11");

        assertThat(registry.get("hotel.external.operations")
                .tags("channel", "mail", "operation", "booking_confirmation", "outcome", "failure")
                .timer().count()).isEqualTo(1);
        assertThat(registry.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags()).allSatisfy(tag ->
                        assertThat(tag.getValue())
                                .doesNotContain("guest@example.com")
                                .doesNotContain("smtp-password")));
    }
}
