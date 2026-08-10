package com.hotel.emailoutbox;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class JavaMailEmailDeliveryAdapterTest {

    @Test
    void disabledAdapterFailsClosedWithoutContactingSmtp() {
        JavaMailSender sender = mock(JavaMailSender.class);
        JavaMailEmailDeliveryAdapter adapter = new JavaMailEmailDeliveryAdapter(
                sender, false, "sandbox@example.test", "LuxeStay");
        EmailOutboxMessage message = new EmailOutboxMessage(null, "disabled-test", "hash", "test", "v1",
                "guest@example.com", "Subject", null, "body", null, null, null, 1,
                java.time.LocalDateTime.now());

        var result = adapter.deliver(message);

        assertEquals(EmailDeliveryOutcome.FAILED, result.outcome());
        assertEquals("DELIVERY_DISABLED", result.errorCode());
        verify(sender, never()).createMimeMessage();
        verify(sender, never()).send(org.mockito.ArgumentMatchers.any(jakarta.mail.internet.MimeMessage.class));
    }
}
