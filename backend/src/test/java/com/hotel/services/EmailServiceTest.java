package com.hotel.services;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import com.hotel.observability.OperationalMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender, new OperationalMetrics(new SimpleMeterRegistry()));
        ReflectionTestUtils.setField(emailService, "fromEmail", "noreply@luxestay.test");
        ReflectionTestUtils.setField(emailService, "loginUrl", "https://luxestay.test/login");
    }

    @Test
    void registrationTemplate_IsSentAsHtmlWhenMailIsEnabled() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        ReflectionTestUtils.setField(emailService, "registrationEmailEnabled", true);

        boolean sent = emailService.sendRegistrationSuccess("guest@example.com", "Nguyễn <An>");

        assertTrue(sent);
        assertTrue(message.getSubject().contains("LuxeStay"));
        assertTrue(message.getContent().toString().contains("Nguyễn &lt;An&gt;"));
        verify(mailSender).send(message);
    }

    @Test
    void registrationTemplate_DoesNotContactSmtpWhenMailIsDisabled() {
        ReflectionTestUtils.setField(emailService, "registrationEmailEnabled", false);

        boolean sent = emailService.sendRegistrationSuccess("guest@example.com", "Guest");

        assertFalse(sent);
        verify(mailSender, never()).createMimeMessage();
        verify(mailSender, never()).send(any(MimeMessage.class));
    }
}
