package com.hotel.services;

import com.hotel.entities.EmailVerificationPurpose;
import com.hotel.observability.OperationalMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationMailerTest {

    @Mock private JavaMailSender mailSender;
    private EmailVerificationMailer mailer;

    @BeforeEach
    void setUp() {
        mailer = new EmailVerificationMailer(mailSender, new OperationalMetrics(new SimpleMeterRegistry()));
        ReflectionTestUtils.setField(mailer, "fromEmail", "noreply@luxestay.test");
        ReflectionTestUtils.setField(mailer, "enabled", true);
    }

    @Test
    void verificationTemplateEscapesUserControlledValues() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);

        boolean sent = mailer.send(
                "guest@example.com",
                "Guest <script>",
                "https://luxestay.test/verify-email?token=safe-token&next=<script>",
                EmailVerificationPurpose.EMAIL_CHANGE,
                60);

        assertTrue(sent);
        assertTrue(message.getSubject().contains("email mới"));
        assertTrue(message.getContent().toString().contains("Guest &lt;script&gt;"));
        assertTrue(message.getContent().toString().contains("next=&lt;script&gt;"));
        verify(mailSender).send(message);
    }
}
