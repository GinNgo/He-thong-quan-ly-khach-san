package com.hotel.services;

import com.hotel.entities.EmailVerificationPurpose;
import com.hotel.observability.OperationalMetrics;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.Duration;

@Service
public class EmailVerificationMailer {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationMailer.class);

    private final JavaMailSender mailSender;
    private final OperationalMetrics operationalMetrics;

    @Value("${spring.mail.username:noreply@hotel.com}")
    private String fromEmail;

    @Value("${app.mail.email-verification-enabled:true}")
    private boolean enabled;

    public EmailVerificationMailer(JavaMailSender mailSender, OperationalMetrics operationalMetrics) {
        this.mailSender = mailSender;
        this.operationalMetrics = operationalMetrics;
    }

    public boolean send(
            String targetEmail,
            String customerName,
            String verificationUrl,
            EmailVerificationPurpose purpose,
            long expiresInMinutes) {
        if (!enabled || targetEmail == null || targetEmail.isBlank()) {
            log.info("MAIL_DELIVERY template=email_verification outcome=disabled_or_invalid");
            return false;
        }

        long startedAt = System.nanoTime();
        try {
            String safeName = HtmlUtils.htmlEscape(
                    customerName == null || customerName.isBlank() ? "Guest" : customerName.strip());
            String safeUrl = HtmlUtils.htmlEscape(verificationUrl);
            boolean emailChange = purpose == EmailVerificationPurpose.EMAIL_CHANGE;

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(targetEmail.strip());
            helper.setSubject(emailChange ? "Confirm your new LuxeStay email" : "Verify your LuxeStay email");
            helper.setText(template(safeName, safeUrl, expiresInMinutes, emailChange), true);
            mailSender.send(message);
            operationalMetrics.recordExternal("mail", "email_verification", false, elapsed(startedAt));
            return true;
        } catch (Exception exception) {
            operationalMetrics.recordExternal("mail", "email_verification", true, elapsed(startedAt));
            log.warn("MAIL_DELIVERY template=email_verification outcome=failure type={}",
                    exception.getClass().getSimpleName());
            return false;
        }
    }

    private Duration elapsed(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt);
    }

    private String template(String customerName, String url, long expiresInMinutes, boolean emailChange) {
        String title = emailChange ? "Confirm your new email" : "Verify your email";
        String copy = emailChange
                ? "Confirm this address before it replaces the email currently attached to your account."
                : "Verify this address to protect your account and receive booking information reliably.";
        return """
                <!doctype html>
                <html lang="en">
                  <body style="margin:0;background:#f3f7f5;font-family:Arial,sans-serif;color:#17332d">
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="padding:32px 16px;background:#f3f7f5">
                      <tr><td align="center">
                        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:600px;background:#fff;border:1px solid #d9e4df;border-radius:18px;overflow:hidden">
                          <tr><td style="padding:26px 32px;background:#0f766e;color:#fff"><strong>LuxeStay</strong><h1 style="margin:8px 0 0">%s</h1></td></tr>
                          <tr><td style="padding:32px"><p>Hello <strong>%s</strong>,</p><p style="line-height:1.65;color:#49635d">%s This one-time link expires in %d minutes.</p><a href="%s" style="display:inline-block;padding:13px 22px;border-radius:999px;background:#0f766e;color:#fff;text-decoration:none;font-weight:700">%s</a><p style="margin-top:26px;font-size:13px;color:#71837f">If you did not request this, ignore this message and keep your current email.</p></td></tr>
                        </table>
                      </td></tr>
                    </table>
                  </body>
                </html>
                """.formatted(title, customerName, copy, expiresInMinutes, url, title);
    }
}
