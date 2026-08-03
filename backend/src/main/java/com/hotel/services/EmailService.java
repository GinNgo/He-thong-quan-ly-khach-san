package com.hotel.services;

import com.hotel.observability.OperationalMetrics;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.Duration;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final OperationalMetrics operationalMetrics;

    @Value("${spring.mail.username:noreply@hotel.com}")
    private String fromEmail;

    @Value("${app.mail.password-reset-enabled:true}")
    private boolean passwordResetEmailEnabled;

    public EmailService(JavaMailSender mailSender, OperationalMetrics operationalMetrics) {
        this.mailSender = mailSender;
        this.operationalMetrics = operationalMetrics;
    }

    public boolean sendPasswordResetEmail(
            String toEmail,
            String customerName,
            String resetUrl,
            long expiresInMinutes) {
        if (!passwordResetEmailEnabled || toEmail == null || toEmail.isBlank()) {
            log.info("MAIL_DELIVERY template=password_reset outcome=disabled_or_invalid");
            return false;
        }

        long startedAt = System.nanoTime();
        try {
            String safeName = HtmlUtils.htmlEscape(customerName == null || customerName.isBlank()
                    ? "Guest"
                    : customerName.strip());
            String safeResetUrl = HtmlUtils.htmlEscape(resetUrl);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail.strip());
            helper.setSubject("LuxeStay password reset");
            helper.setText(passwordResetTemplate(safeName, safeResetUrl, expiresInMinutes), true);
            mailSender.send(message);
            operationalMetrics.recordExternal("mail", "password_reset", false, elapsed(startedAt));
            return true;
        } catch (Exception exception) {
            operationalMetrics.recordExternal("mail", "password_reset", true, elapsed(startedAt));
            log.warn("MAIL_DELIVERY template=password_reset outcome=failure type={}",
                    exception.getClass().getSimpleName());
            return false;
        }
    }

    public void sendBookingConfirmation(String toEmail, String customerName, Long reservationId, String checkIn, String checkOut) {
        long startedAt = System.nanoTime();
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Xác nhận đặt phòng thành công - Mã Đặt Phòng: #" + reservationId);
            
            String body = "Kính gửi " + customerName + ",\n\n"
                    + "Cảm ơn bạn đã đặt phòng tại khách sạn của chúng tôi.\n"
                    + "Thông tin đặt phòng của bạn như sau:\n"
                    + "- Mã đặt phòng: #" + reservationId + "\n"
                    + "- Ngày nhận phòng: " + checkIn + "\n"
                    + "- Ngày trả phòng: " + checkOut + "\n\n"
                    + "Vui lòng giữ lại email này để làm thủ tục check-in.\n\n"
                    + "Trân trọng,\nBan quản lý khách sạn";
            
            message.setText(body);
            mailSender.send(message);
            operationalMetrics.recordExternal("mail", "booking_confirmation", false, elapsed(startedAt));
        } catch (Exception exception) {
            operationalMetrics.recordExternal("mail", "booking_confirmation", true, elapsed(startedAt));
            log.warn("MAIL_DELIVERY template=booking_confirmation outcome=failure type={}",
                    exception.getClass().getSimpleName());
        }
    }

    public boolean sendInvoiceEmail(String toEmail, String invoiceNumber, byte[] pdf) {
        if (toEmail == null || toEmail.isBlank() || pdf == null || pdf.length == 0) {
            return false;
        }
        long startedAt = System.nanoTime();
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail.trim());
            helper.setSubject("Invoice " + invoiceNumber);
            helper.setText("Your finalized invoice " + invoiceNumber + " is attached.", false);
            helper.addAttachment(invoiceNumber + ".pdf", new ByteArrayResource(pdf), "application/pdf");
            mailSender.send(message);
            operationalMetrics.recordExternal("mail", "invoice", false, elapsed(startedAt));
            return true;
        } catch (Exception exception) {
            operationalMetrics.recordExternal("mail", "invoice", true, elapsed(startedAt));
            log.warn("MAIL_DELIVERY template=invoice outcome=failure type={}",
                    exception.getClass().getSimpleName());
            return false;
        }
    }

    private String passwordResetTemplate(String customerName, String safeResetUrl, long expiresInMinutes) {
        return """
                <!doctype html>
                <html lang="en">
                  <body style="margin:0;background:#f3f7f5;font-family:Arial,sans-serif;color:#17332d">
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="padding:32px 16px;background:#f3f7f5">
                      <tr><td align="center">
                        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:600px;background:#ffffff;border:1px solid #d9e4df;border-radius:18px;overflow:hidden">
                          <tr><td style="padding:26px 32px;background:#0f766e;color:#ffffff">
                            <div style="font-size:13px;letter-spacing:2px;text-transform:uppercase;opacity:.82">LuxeStay</div>
                            <h1 style="margin:8px 0 0;font-size:28px;line-height:1.2">Reset your password</h1>
                          </td></tr>
                          <tr><td style="padding:32px">
                            <p style="margin:0 0 16px;font-size:17px">Hello <strong>%s</strong>,</p>
                            <p style="margin:0 0 24px;line-height:1.65;color:#49635d">We received a request to reset your LuxeStay password. The link is valid for %d minutes and can be used once.</p>
                            <a href="%s" style="display:inline-block;padding:13px 22px;border-radius:999px;background:#0f766e;color:#ffffff;text-decoration:none;font-weight:700">Reset password</a>
                            <p style="margin:26px 0 0;font-size:13px;line-height:1.55;color:#71837f">If you did not request this, you can safely ignore this email.</p>
                          </td></tr>
                          <tr><td style="padding:18px 32px;background:#edf5f2;font-size:12px;color:#71837f">LuxeStay Hospitality Group</td></tr>
                        </table>
                      </td></tr>
                    </table>
                  </body>
                </html>
                """.formatted(customerName, expiresInMinutes, safeResetUrl);
    }

    private Duration elapsed(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt);
    }
}
