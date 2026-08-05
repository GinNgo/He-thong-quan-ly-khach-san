package com.hotel.propertyreview;

import com.hotel.observability.OperationalMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class PropertyReviewMailer {

    private static final Logger log = LoggerFactory.getLogger(PropertyReviewMailer.class);

    private final JavaMailSender mailSender;
    private final OperationalMetrics operationalMetrics;
    private final boolean enabled;
    private final String fromEmail;

    public PropertyReviewMailer(
            JavaMailSender mailSender,
            OperationalMetrics operationalMetrics,
            @Value("${app.mail.property-review-enabled:false}") boolean enabled,
            @Value("${spring.mail.username:noreply@hotel.com}") String fromEmail) {
        this.mailSender = mailSender;
        this.operationalMetrics = operationalMetrics;
        this.enabled = enabled;
        this.fromEmail = fromEmail;
    }

    public boolean send(String recipientEmail, String subject, String bodyText) {
        if (!enabled || recipientEmail == null || recipientEmail.isBlank()) {
            log.info("MAIL_DELIVERY template=property_review outcome=disabled_or_invalid");
            return false;
        }
        long startedAt = System.nanoTime();
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(recipientEmail.trim());
            message.setSubject(subject);
            message.setText(bodyText);
            mailSender.send(message);
            operationalMetrics.recordExternal("mail", "property_review", false, elapsed(startedAt));
            return true;
        } catch (Exception deliveryFailure) {
            operationalMetrics.recordExternal("mail", "property_review", true, elapsed(startedAt));
            log.warn("MAIL_DELIVERY template=property_review outcome=failure type={}",
                    deliveryFailure.getClass().getSimpleName());
            return false;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    private Duration elapsed(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt);
    }
}
