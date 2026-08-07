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
<<<<<<< HEAD
            helper.setSubject(emailChange
                    ? "Xác nhận email LuxeStay mới / Confirm your new email"
                    : "Xác thực tài khoản LuxeStay / Verify your account");
=======
            helper.setSubject(emailChange ? "Xác nhận địa chỉ email mới - LuxeStay" : "Xác thực email LuxeStay");
>>>>>>> codex/ui-functional-audit-polish
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
<<<<<<< HEAD
        String title = emailChange ? "Xác nhận email mới" : "Chào mừng đến với LuxeStay";
        String subtitle = emailChange ? "Confirm your new email" : "Verify your account email";
        String copy = emailChange
                ? "Xác nhận địa chỉ này trước khi thay thế email hiện tại trên tài khoản."
                : "Xác thực email để bảo vệ tài khoản và nhận đầy đủ thông tin đặt phòng, thanh toán.";
=======
        String title = emailChange ? "Xác nhận email mới" : "Xác thực địa chỉ email";
        String copy = emailChange
                ? "Hãy xác nhận địa chỉ này trước khi thay thế email hiện tại trên tài khoản của bạn."
                : "Xác thực địa chỉ này để bảo vệ tài khoản và nhận thông tin đặt phòng đầy đủ.";
        String action = emailChange ? "Xác nhận email mới" : "Xác thực email";
>>>>>>> codex/ui-functional-audit-polish
        return """
                <!doctype html>
                <html lang="vi">
                  <body style="margin:0;background:#f5f0e8;font-family:Arial,sans-serif;color:#17332d">
                    <div style="display:none;max-height:0;overflow:hidden;opacity:0">Xác thực địa chỉ email LuxeStay của bạn.</div>
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="padding:38px 14px;background:#f5f0e8">
                      <tr><td align="center">
<<<<<<< HEAD
                        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:600px;background:#fff;border:1px solid #d9e4df;border-radius:18px;overflow:hidden">
                          <tr><td style="padding:26px 32px;background:#124e43;color:#fff"><div style="font-size:13px;letter-spacing:2px;text-transform:uppercase;opacity:.82">LuxeStay</div><h1 style="margin:8px 0 0">%s</h1><p style="margin:7px 0 0;opacity:.85">%s</p></td></tr>
                          <tr><td style="padding:32px"><p>Xin chào / Hello <strong>%s</strong>,</p><p style="line-height:1.65;color:#49635d">%s Liên kết dùng một lần này hết hạn sau %d phút.<br>This one-time link expires in %d minutes.</p><a href="%s" style="display:inline-block;padding:13px 22px;border-radius:999px;background:#0f766e;color:#fff;text-decoration:none;font-weight:700">Xác nhận email / Verify email</a><p style="margin-top:26px;font-size:13px;line-height:1.55;color:#71837f">Nếu bạn không thực hiện yêu cầu này, hãy bỏ qua email và không chia sẻ liên kết.<br>If you did not request this, ignore the email and do not share the link.</p></td></tr>
=======
                        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:620px;background:#fffdf9;border:1px solid #e5dccd;border-radius:24px;overflow:hidden;box-shadow:0 18px 50px rgba(23,51,45,.10)">
                          <tr><td style="padding:16px 32px;background:#d8a84e;color:#17332d;font-size:12px;font-weight:700;letter-spacing:2.4px;text-transform:uppercase">LuxeStay · Xác minh danh tính</td></tr>
                          <tr><td style="padding:36px 32px;background:#173f37;color:#fff"><h1 style="margin:0;font-family:Georgia,serif;font-size:34px;font-weight:normal">%s</h1></td></tr>
                          <tr><td style="padding:32px"><p>Xin chào <strong>%s</strong>,</p><p style="line-height:1.65;color:#49635d">%s Liên kết bảo mật này chỉ dùng một lần và hết hạn sau <strong>%d phút</strong>.</p><a href="%s" style="display:inline-block;padding:14px 24px;border-radius:999px;background:#d8a84e;color:#17332d;text-decoration:none;font-weight:700">%s →</a><p style="margin-top:26px;padding:14px 16px;background:#fff8e8;border-left:4px solid #d8a84e;font-size:13px;line-height:1.55;color:#5e5138">Nếu bạn không yêu cầu thao tác này, hãy bỏ qua email và giữ nguyên địa chỉ hiện tại.</p></td></tr>
                          <tr><td style="padding:20px 32px;background:#173f37;font-size:12px;color:#dce9e5">LuxeStay Hospitality Group · Bảo mật tài khoản</td></tr>
>>>>>>> codex/ui-functional-audit-polish
                        </table>
                      </td></tr>
                    </table>
                  </body>
                </html>
<<<<<<< HEAD
                """.formatted(title, subtitle, customerName, copy, expiresInMinutes, expiresInMinutes, url);
=======
                """.formatted(title, customerName, copy, expiresInMinutes, url, action);
>>>>>>> codex/ui-functional-audit-polish
    }
}
