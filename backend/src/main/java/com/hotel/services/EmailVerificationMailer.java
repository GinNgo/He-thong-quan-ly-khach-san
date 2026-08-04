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
            helper.setSubject(emailChange
                    ? "Xác nhận email LuxeStay mới / Confirm your new email"
                    : "Xác thực tài khoản LuxeStay / Verify your account");
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
        String title = emailChange ? "Xác nhận email mới" : "Chào mừng đến với LuxeStay";
        String subtitle = emailChange ? "Confirm your new email" : "Verify your account email";
        String copy = emailChange
                ? "Xác nhận địa chỉ này trước khi thay thế email hiện tại trên tài khoản."
                : "Xác thực email để bảo vệ tài khoản và nhận đầy đủ thông tin đặt phòng, thanh toán.";
        return """
                <!doctype html>
                <html lang="en">
                  <body style="margin:0;background:#f3f7f5;font-family:Arial,sans-serif;color:#17332d">
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="padding:32px 16px;background:#f3f7f5">
                      <tr><td align="center">
                        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:600px;background:#fff;border:1px solid #d9e4df;border-radius:18px;overflow:hidden">
                          <tr><td style="padding:26px 32px;background:#124e43;color:#fff"><div style="font-size:13px;letter-spacing:2px;text-transform:uppercase;opacity:.82">LuxeStay</div><h1 style="margin:8px 0 0">%s</h1><p style="margin:7px 0 0;opacity:.85">%s</p></td></tr>
                          <tr><td style="padding:32px"><p>Xin chào / Hello <strong>%s</strong>,</p><p style="line-height:1.65;color:#49635d">%s Liên kết dùng một lần này hết hạn sau %d phút.<br>This one-time link expires in %d minutes.</p><a href="%s" style="display:inline-block;padding:13px 22px;border-radius:999px;background:#0f766e;color:#fff;text-decoration:none;font-weight:700">Xác nhận email / Verify email</a><p style="margin-top:26px;font-size:13px;line-height:1.55;color:#71837f">Nếu bạn không thực hiện yêu cầu này, hãy bỏ qua email và không chia sẻ liên kết.<br>If you did not request this, ignore the email and do not share the link.</p></td></tr>
                        </table>
                      </td></tr>
                    </table>
                  </body>
                </html>
                """.formatted(title, subtitle, customerName, copy, expiresInMinutes, expiresInMinutes, url);
    }
}
