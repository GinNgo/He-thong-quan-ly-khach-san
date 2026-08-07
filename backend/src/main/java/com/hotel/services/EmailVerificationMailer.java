package com.hotel.services;

import com.hotel.entities.EmailVerificationPurpose;
import com.hotel.observability.OperationalMetrics;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
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

    @Value("${app.mail.sender-name:LuxeStay}")
    private String senderName;

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
            helper.setFrom(fromEmail, senderName);
            helper.setTo(targetEmail.strip());
            helper.setSubject(emailChange ? "Xác nhận địa chỉ email mới - LuxeStay" : "Xác thực email LuxeStay");
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
        String title = emailChange ? "Xác nhận email mới" : "Xác thực địa chỉ email";
        String copy = emailChange
                ? "Hãy xác nhận địa chỉ này trước khi thay thế email hiện tại trên tài khoản của bạn."
                : "Xác thực địa chỉ này để bảo vệ tài khoản và nhận thông tin đặt phòng đầy đủ.";
        String action = emailChange ? "Xác nhận email mới" : "Xác thực email";
        return """
                <!doctype html>
                <html lang="vi">
                  <body style="margin:0;background:#f5f0e8;font-family:Arial,sans-serif;color:#17332d">
                    <div style="display:none;max-height:0;overflow:hidden;opacity:0">Xác thực địa chỉ email LuxeStay của bạn.</div>
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="padding:38px 14px;background:#f5f0e8">
                      <tr><td align="center">
                        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:620px;background:#fffdf9;border:1px solid #e5dccd;border-radius:24px;overflow:hidden;box-shadow:0 18px 50px rgba(23,51,45,.10)">
                          <tr><td style="padding:16px 32px;background:#d8a84e;color:#17332d;font-size:12px;font-weight:700;letter-spacing:2.4px;text-transform:uppercase">LuxeStay · Xác minh danh tính</td></tr>
                          <tr><td style="padding:36px 32px;background:#173f37;color:#fff"><h1 style="margin:0;font-family:Georgia,serif;font-size:34px;font-weight:normal">%s</h1></td></tr>
                          <tr><td style="padding:32px"><p>Xin chào <strong>%s</strong>,</p><p style="line-height:1.65;color:#49635d">%s Liên kết bảo mật này chỉ dùng một lần và hết hạn sau <strong>%d phút</strong>.</p><a href="%s" style="display:inline-block;padding:14px 24px;border-radius:999px;background:#d8a84e;color:#17332d;text-decoration:none;font-weight:700">%s →</a><p style="margin-top:26px;padding:14px 16px;background:#fff8e8;border-left:4px solid #d8a84e;font-size:13px;line-height:1.55;color:#5e5138">Nếu bạn không yêu cầu thao tác này, hãy bỏ qua email và giữ nguyên địa chỉ hiện tại.</p></td></tr>
                          <tr><td style="padding:20px 32px;background:#173f37;font-size:12px;color:#dce9e5">LuxeStay Hospitality Group · Bảo mật tài khoản</td></tr>
                        </table>
                      </td></tr>
                    </table>
                  </body>
                </html>
                """.formatted(title, customerName, copy, expiresInMinutes, url, action);
    }
}
