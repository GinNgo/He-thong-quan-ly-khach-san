package com.hotel.emailoutbox;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.util.UUID;

/** Sandbox/test-only adapter. It never falls back to a live provider when disabled. */
@Component
public class JavaMailEmailDeliveryAdapter implements EmailDeliveryPort {

    private final JavaMailSender mailSender;
    private final boolean deliveryEnabled;
    private final String fromEmail;
    private final String senderName;

    public JavaMailEmailDeliveryAdapter(
            JavaMailSender mailSender,
            @Value("${app.mail.outbox.delivery-enabled:false}") boolean deliveryEnabled,
            @Value("${spring.mail.username:noreply@hotel.com}") String fromEmail,
            @Value("${app.mail.sender-name:LuxeStay}") String senderName) {
        this.mailSender = mailSender;
        this.deliveryEnabled = deliveryEnabled;
        this.fromEmail = fromEmail;
        this.senderName = senderName;
    }

    @Override
    public DeliveryResult deliver(EmailOutboxMessage message) {
        if (!deliveryEnabled) {
            return DeliveryResult.failed("DELIVERY_DISABLED");
        }
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage, message.getAttachmentBytes() != null, "UTF-8");
            helper.setFrom(fromEmail, senderName);
            helper.setTo(message.getRecipientEmail());
            helper.setSubject(message.getSubject());
            if (message.getBodyHtml() != null && !message.getBodyHtml().isBlank()) {
                helper.setText(message.getBodyText(), message.getBodyHtml());
            } else {
                helper.setText(message.getBodyText() == null ? "" : message.getBodyText(), false);
            }
            if (message.getAttachmentBytes() != null && message.getAttachmentBytes().length > 0) {
                helper.addAttachment(message.getAttachmentName() == null ? "attachment" : message.getAttachmentName(),
                        new ByteArrayResource(message.getAttachmentBytes()),
                        message.getAttachmentContentType() == null ? "application/octet-stream" : message.getAttachmentContentType());
            }
            mailSender.send(mimeMessage);
            return DeliveryResult.sent(mimeMessage.getMessageID() == null
                    ? UUID.randomUUID().toString() : mimeMessage.getMessageID());
        } catch (Exception ignored) {
            return DeliveryResult.failed("SMTP_DELIVERY_FAILED");
        }
    }
}
