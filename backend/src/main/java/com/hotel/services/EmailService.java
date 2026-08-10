package com.hotel.services;

import com.hotel.observability.OperationalMetrics;
import com.hotel.emailoutbox.EmailOutboxDtos;
import com.hotel.emailoutbox.EmailOutboxService;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import org.springframework.core.io.ByteArrayResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.util.HtmlUtils;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final String BOOKING_TEMPLATE_KEY = "booking_confirmation";
    private static final String BOOKING_TEMPLATE_VERSION = "v2";

    private final JavaMailSender mailSender;
    private final OperationalMetrics operationalMetrics;
    private final EmailOutboxService emailOutboxService;

    @Value("${spring.mail.username:noreply@hotel.com}")
    private String fromEmail;

    @Value("${app.mail.sender-name:LuxeStay}")
    private String senderName;

    @Value("${app.mail.registration-enabled:true}")
    private boolean registrationEmailEnabled;

    @Value("${app.mail.password-reset-enabled:true}")
    private boolean passwordResetEmailEnabled;

    @Value("${app.mail.outbox.enabled:true}")
    private boolean outboxEnabled;

    @Value("${app.mail.login-url:http://localhost:4200/login}")
    private String loginUrl;

    @Value("${app.mail.admin-recipient:}")
    private String adminRecipient;

    @org.springframework.beans.factory.annotation.Autowired
    public EmailService(JavaMailSender mailSender, OperationalMetrics operationalMetrics,
                        EmailOutboxService emailOutboxService) {
        this.mailSender = mailSender;
        this.operationalMetrics = operationalMetrics;
        this.emailOutboxService = emailOutboxService;
    }

    /** Compatibility constructor retained for isolated legacy unit tests. */
    public EmailService(JavaMailSender mailSender, OperationalMetrics operationalMetrics) {
        this(mailSender, operationalMetrics, null);
    }

    public boolean sendRegistrationSuccess(String toEmail, String customerName) {
        if (!registrationEmailEnabled) {
            log.info("MAIL_DELIVERY template=registration outcome=disabled");
            return false;
        }

        long startedAt = System.nanoTime();
        try {
            String safeName = HtmlUtils.htmlEscape(customerName == null || customerName.isBlank()
                    ? "Quý khách"
                    : customerName.trim());
            String safeLoginUrl = HtmlUtils.htmlEscape(loginUrl);

            if (useOutbox()) {
                emailOutboxService.enqueue(new EmailOutboxDtos.EnqueueRequest(
                        null, "registration:" + toEmail.trim().toLowerCase(java.util.Locale.ROOT),
                        "registration", "v2", toEmail, "Chào mừng bạn đến với LuxeStay",
                        registrationTemplate(safeName, safeLoginUrl), null, null, null, null, 5));
                operationalMetrics.recordExternal("mail", "registration", false, elapsed(startedAt));
                return true;
            }

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromEmail, senderName);
            helper.setTo(toEmail);
            helper.setSubject("Chào mừng bạn đến với LuxeStay");
            helper.setText(registrationTemplate(safeName, safeLoginUrl), true);
            mailSender.send(message);
            operationalMetrics.recordExternal("mail", "registration", false, elapsed(startedAt));
            return true;
        } catch (Exception exception) {
            operationalMetrics.recordExternal("mail", "registration", true, elapsed(startedAt));
            log.warn("MAIL_DELIVERY template=registration outcome=failure type={}",
                    exception.getClass().getSimpleName());
            return false;
        }
    }

    /** Queues a partner-registration alert for the system administrator after commit. */
    public boolean sendPartnerRegistrationAlert(Long propertyId, String ownerName, String ownerEmail,
                                                String propertyName, String propertyAddress) {
        if (!useOutbox() || propertyId == null) {
            return false;
        }
        String recipient = normalizeDeliverableRecipient(adminRecipient);
        if (recipient == null || (ownerEmail != null && recipient.equalsIgnoreCase(ownerEmail.trim()))) {
            log.warn("MAIL_DELIVERY template=partner_registration_alert outcome=disabled_or_invalid_recipient");
            return false;
        }
        String safeOwner = HtmlUtils.htmlEscape(ownerName == null ? "-" : ownerName.trim());
        String safeEmail = HtmlUtils.htmlEscape(ownerEmail == null ? "-" : ownerEmail.trim());
        String safeProperty = HtmlUtils.htmlEscape(propertyName == null ? "-" : propertyName.trim());
        String safeAddress = HtmlUtils.htmlEscape(propertyAddress == null ? "-" : propertyAddress.trim());
        EmailOutboxDtos.EnqueueRequest request = new EmailOutboxDtos.EnqueueRequest(
                propertyId,
                "partner-registration-alert:" + propertyId,
                "partner_registration_alert",
                "v1",
                recipient,
                "Có cơ sở mới đang chờ duyệt - LuxeStay",
                "<p>Có một cơ sở mới vừa đăng ký và đang chờ quản trị viên duyệt.</p>"
                        + "<p><strong>Cơ sở:</strong> " + safeProperty + "<br>"
                        + "<strong>Chủ cơ sở:</strong> " + safeOwner + "<br>"
                        + "<strong>Email:</strong> " + safeEmail + "<br>"
                        + "<strong>Địa chỉ:</strong> " + safeAddress + "</p>",
                "Có cơ sở mới đang chờ duyệt. Cơ sở: " + safeProperty + "; chủ cơ sở: " + safeOwner
                        + "; email: " + safeEmail + "; địa chỉ: " + safeAddress,
                null, null, null, 5);
        try {
            if (TransactionSynchronizationManager.isActualTransactionActive()
                    && TransactionSynchronizationManager.isSynchronizationActive()) {
                emailOutboxService.enqueueAfterCommit(request);
            } else {
                emailOutboxService.enqueue(request);
            }
            operationalMetrics.recordExternal("mail", "partner_registration_alert", false, Duration.ZERO);
            return true;
        } catch (Exception exception) {
            operationalMetrics.recordExternal("mail", "partner_registration_alert", true, Duration.ZERO);
            log.warn("MAIL_DELIVERY template=partner_registration_alert outcome=failure type={}",
                    exception.getClass().getSimpleName());
            return false;
        }
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

            if (useOutbox()) {
                emailOutboxService.enqueue(new EmailOutboxDtos.EnqueueRequest(
                        null, "password-reset:" + digest(resetUrl), "password_reset", "v1", toEmail,
                        "Đặt lại mật khẩu LuxeStay", passwordResetTemplate(safeName, safeResetUrl, expiresInMinutes),
                        "Xin chào " + safeName + ", liên kết đặt lại mật khẩu có hiệu lực trong "
                                + expiresInMinutes + " phút.",
                        null, null, null, 5));
                operationalMetrics.recordExternal("mail", "password_reset", false, elapsed(startedAt));
                return true;
            }

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromEmail, senderName);
            helper.setTo(toEmail.strip());
            helper.setSubject("Đặt lại mật khẩu LuxeStay");
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

    public void sendBookingConfirmation(
            String toEmail,
            String customerName,
            Long reservationId,
            String checkIn,
            String checkOut) {
        sendBookingConfirmation(toEmail, customerName, reservationId, checkIn, checkOut, "vi");
    }

    public void sendBookingConfirmation(
            String toEmail,
            String customerName,
            Long reservationId,
            String checkIn,
            String checkOut,
            String locale) {
        BookingConfirmationContent content = bookingConfirmationContent(
                toEmail, customerName, reservationId, checkIn, checkOut, locale);

        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deliverBookingConfirmationSafely(content);
                }
            });
            return;
        }

        deliverBookingConfirmationSafely(content);
    }

    public boolean sendInvoiceEmail(String toEmail, String invoiceNumber, byte[] pdf) {
        if (toEmail == null || toEmail.isBlank() || pdf == null || pdf.length == 0) {
            return false;
        }
        long startedAt = System.nanoTime();
        try {
            if (useOutbox()) {
                emailOutboxService.enqueue(new EmailOutboxDtos.EnqueueRequest(
                        null, "invoice:" + invoiceNumber + ":" + digest(pdf), "invoice", "v1", toEmail,
                        "Hóa đơn lưu trú " + invoiceNumber, invoiceTemplate(invoiceNumber),
                        "Kính gửi quý khách, hóa đơn " + invoiceNumber + " được đính kèm.",
                        invoiceNumber + ".pdf", "application/pdf", pdf.clone(), 5));
                operationalMetrics.recordExternal("mail", "invoice", false, elapsed(startedAt));
                return true;
            }
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, senderName);
            helper.setTo(toEmail.trim());
            helper.setSubject("Hóa đơn lưu trú " + invoiceNumber);
            helper.setText(invoiceTemplate(invoiceNumber), true);
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

    private Duration elapsed(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt);
    }

    private boolean useOutbox() {
        return outboxEnabled && emailOutboxService != null;
    }

    private void deliverBookingConfirmationSafely(BookingConfirmationContent content) {
        long startedAt = System.nanoTime();
        try {
            if (useOutbox()) {
                emailOutboxService.enqueueAfterCommit(new EmailOutboxDtos.EnqueueRequest(
                        null,
                        content.idempotencyKey(),
                        BOOKING_TEMPLATE_KEY,
                        content.templateVersion(),
                        content.recipientEmail(),
                        content.subject(),
                        content.bodyHtml(),
                        content.bodyText(),
                        null,
                        null,
                        null,
                        5));
            } else {
                MimeMessage message = mailSender.createMimeMessage();
                // Alternative text+HTML bodies require multipart mode with real JavaMail providers.
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(fromEmail, senderName);
                helper.setTo(content.recipientEmail());
                helper.setSubject(content.subject());
                helper.setText(content.bodyText(), content.bodyHtml());
                mailSender.send(message);
            }
            operationalMetrics.recordExternal("mail", "booking_confirmation", false, elapsed(startedAt));
        } catch (Exception exception) {
            operationalMetrics.recordExternal("mail", "booking_confirmation", true, elapsed(startedAt));
            log.warn("MAIL_DELIVERY template=booking_confirmation outcome=failure type={}",
                    exception.getClass().getSimpleName());
        }
    }

    private BookingConfirmationContent bookingConfirmationContent(
            String toEmail,
            String customerName,
            Long reservationId,
            String checkIn,
            String checkOut,
            String requestedLocale) {
        String locale = normalizeBookingLocale(requestedLocale);
        String safeCustomerText = normalizeTemplateValue(
                customerName,
                "en".equals(locale) ? "Guest" : "Quý khách");
        String safeCheckInText = normalizeTemplateValue(checkIn, "-");
        String safeCheckOutText = normalizeTemplateValue(checkOut, "-");
        String reservationNumber = reservationId == null ? "-" : reservationId.toString();
        String subject;
        String bodyText;

        if ("en".equals(locale)) {
            subject = "Booking confirmed - Reservation #" + reservationNumber;
            bodyText = "Hello " + safeCustomerText + ",\n\n"
                    + "Your LuxeStay reservation is confirmed.\n"
                    + "Reservation: #" + reservationNumber + "\n"
                    + "Check-in: " + safeCheckInText + "\n"
                    + "Check-out: " + safeCheckOutText + "\n\n"
                    + "Keep this email for check-in.\n\n"
                    + "Regards,\nLuxeStay";
        } else {
            subject = "Xác nhận đặt phòng thành công - Mã đặt phòng: #" + reservationNumber;
            bodyText = "Kính gửi " + safeCustomerText + ",\n\n"
                    + "Đặt phòng LuxeStay của bạn đã được xác nhận.\n"
                    + "Mã đặt phòng: #" + reservationNumber + "\n"
                    + "Ngày nhận phòng: " + safeCheckInText + "\n"
                    + "Ngày trả phòng: " + safeCheckOutText + "\n\n"
                    + "Vui lòng giữ email này để làm thủ tục check-in.\n\n"
                    + "Trân trọng,\nLuxeStay";
        }

        String bodyHtml = bookingConfirmationHtml(
                locale,
                HtmlUtils.htmlEscape(safeCustomerText),
                HtmlUtils.htmlEscape(reservationNumber),
                HtmlUtils.htmlEscape(safeCheckInText),
                HtmlUtils.htmlEscape(safeCheckOutText));
        String dateFingerprint = digest(safeCheckInText + "\u001f" + safeCheckOutText).substring(0, 16);
        return new BookingConfirmationContent(
                toEmail,
                "booking-confirmation:" + BOOKING_TEMPLATE_VERSION + ":" + locale + ":"
                        + reservationNumber + ":" + dateFingerprint,
                BOOKING_TEMPLATE_VERSION + "." + locale,
                subject,
                bodyHtml,
                bodyText);
    }

    private String bookingConfirmationHtml(
            String locale,
            String customerName,
            String reservationNumber,
            String checkIn,
            String checkOut) {
        boolean english = "en".equals(locale);
        String heading = english ? "Your stay is confirmed" : "Đặt phòng đã được xác nhận";
        String greeting = english ? "Hello" : "Kính gửi";
        String intro = english
                ? "We have reserved your stay. Keep this message available when you arrive."
                : "LuxeStay đã giữ chỗ cho kỳ nghỉ của bạn. Vui lòng giữ email này khi đến nhận phòng.";
        String reservationLabel = english ? "Reservation" : "Mã đặt phòng";
        String checkInLabel = english ? "Check-in" : "Ngày nhận phòng";
        String checkOutLabel = english ? "Check-out" : "Ngày trả phòng";
        String footer = english ? "LuxeStay guest services" : "Bộ phận chăm sóc khách hàng LuxeStay";
        return """
                <!doctype html>
                <html lang="{{locale}}">
                  <body style="margin:0;background:#f5f0e8;font-family:Arial,sans-serif;color:#17332d">
                    <div style="display:none;max-height:0;overflow:hidden;opacity:0">Thông tin xác nhận kỳ nghỉ LuxeStay của bạn.</div>
                    <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="padding:38px 14px;background:#f5f0e8">
                      <tr><td align="center">
                        <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="max-width:620px;background:#fffdf9;border:1px solid #e5dccd;border-radius:24px;overflow:hidden;box-shadow:0 18px 50px rgba(23,51,45,.10)">
                          <tr><td style="padding:16px 32px;background:#d8a84e;color:#17332d;font-size:12px;font-weight:700;letter-spacing:2.4px;text-transform:uppercase">LuxeStay · Kỳ nghỉ đáng nhớ</td></tr>
                          <tr><td style="padding:34px 32px 30px;background:#173f37;color:#ffffff">
                            <div style="display:inline-block;padding:7px 11px;border:1px solid rgba(255,255,255,.28);border-radius:999px;font-size:11px;letter-spacing:1.5px;text-transform:uppercase">Đã xác nhận</div>
                            <h1 style="margin:16px 0 0;font-family:Georgia,serif;font-size:32px;line-height:1.18;font-weight:normal">{{heading}}</h1>
                          </td></tr>
                          <tr><td style="padding:32px">
                            <p style="margin:0 0 16px;font-size:17px">{{greeting}} <strong>{{customerName}}</strong>,</p>
                            <p style="margin:0 0 24px;line-height:1.65;color:#49635d">{{intro}}</p>
                            <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="border-collapse:separate;background:#f1eee7;border:1px solid #e5dccd;border-radius:16px">
                              <tr><td style="padding:14px 16px;color:#49635d">{{reservationLabel}}</td><td style="padding:14px 16px;text-align:right;font-weight:700">#{{reservationNumber}}</td></tr>
                              <tr><td style="padding:14px 16px;color:#49635d;border-top:1px solid #d9e4df">{{checkInLabel}}</td><td style="padding:14px 16px;text-align:right;border-top:1px solid #d9e4df">{{checkIn}}</td></tr>
                              <tr><td style="padding:14px 16px;color:#49635d;border-top:1px solid #d9e4df">{{checkOutLabel}}</td><td style="padding:14px 16px;text-align:right;border-top:1px solid #d9e4df">{{checkOut}}</td></tr>
                            </table>
                            <p style="margin:24px 0 0;padding:14px 16px;border-left:4px solid #d8a84e;background:#fff8e8;color:#5e5138;line-height:1.55">Vui lòng giữ email này để làm thủ tục nhận phòng thuận tiện hơn.</p>
                          </td></tr>
                          <tr><td style="padding:20px 32px;background:#173f37;font-size:12px;color:#dce9e5">{{footer}} · luxestay.local</td></tr>
                        </table>
                      </td></tr>
                    </table>
                  </body>
                </html>
                """
                .replace("{{locale}}", locale)
                .replace("{{heading}}", heading)
                .replace("{{greeting}}", greeting)
                .replace("{{customerName}}", customerName)
                .replace("{{intro}}", intro)
                .replace("{{reservationLabel}}", reservationLabel)
                .replace("{{reservationNumber}}", reservationNumber)
                .replace("{{checkInLabel}}", checkInLabel)
                .replace("{{checkIn}}", checkIn)
                .replace("{{checkOutLabel}}", checkOutLabel)
                .replace("{{checkOut}}", checkOut)
                .replace("{{footer}}", footer);
    }

    private String normalizeBookingLocale(String locale) {
        if (locale == null || locale.isBlank()) return "vi";
        String normalized = locale.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return normalized.equals("en") || normalized.startsWith("en-") ? "en" : "vi";
    }

    private String normalizeDeliverableRecipient(String recipient) {
        if (recipient == null || recipient.isBlank()) return null;
        String normalized = recipient.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) return null;
        String domain = normalized.substring(normalized.lastIndexOf('@') + 1);
        if (domain.equals("example.com") || domain.equals("guest.local")
                || domain.endsWith(".test") || domain.endsWith(".local")) return null;
        return normalized;
    }

    private String normalizeTemplateValue(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String normalized = value.replace('\r', ' ').replace('\n', ' ').trim();
        return normalized.length() <= 200 ? normalized : normalized.substring(0, 200);
    }

    private String digest(String value) {
        return digest(value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8));
    }

    private String digest(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception exception) {
            throw new IllegalStateException("Email idempotency hashing is unavailable.", exception);
        }
    }

    private String registrationTemplate(String customerName, String safeLoginUrl) {
        return """
                <!doctype html>
                <html lang="vi">
                  <body style="margin:0;background:#f5f0e8;font-family:Arial,sans-serif;color:#17332d">
                    <div style="display:none;max-height:0;overflow:hidden;opacity:0">Chào mừng bạn đến với LuxeStay.</div>
                    <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="padding:38px 14px;background:#f5f0e8">
                      <tr><td align="center">
                        <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="max-width:620px;background:#fffdf9;border:1px solid #e5dccd;border-radius:24px;overflow:hidden;box-shadow:0 18px 50px rgba(23,51,45,.10)">
                          <tr><td style="padding:16px 32px;background:#d8a84e;color:#17332d;font-size:12px;font-weight:700;letter-spacing:2.4px;text-transform:uppercase">LuxeStay · Thành viên mới</td></tr>
                          <tr><td style="padding:38px 32px;background:#173f37;color:#ffffff">
                            <div style="font-size:13px;letter-spacing:2px;text-transform:uppercase;color:#f0cf8a">Chào mừng</div>
                            <h1 style="margin:10px 0 0;font-family:Georgia,serif;font-size:34px;line-height:1.16;font-weight:normal">Tài khoản của bạn đã sẵn sàng</h1>
                          </td></tr>
                          <tr><td style="padding:32px">
                            <p style="margin:0 0 16px;font-size:17px">Xin chào <strong>{{customerName}}</strong>,</p>
                            <p style="margin:0 0 24px;line-height:1.65;color:#49635d">Cảm ơn bạn đã đăng ký LuxeStay. Bạn có thể đăng nhập để quản lý hồ sơ, theo dõi đặt phòng và nhận ưu đãi dành cho thành viên.</p>
                            <a href="{{loginUrl}}" style="display:inline-block;padding:14px 24px;border-radius:999px;background:#d8a84e;color:#17332d;text-decoration:none;font-weight:700">Khám phá LuxeStay →</a>
                            <p style="margin:26px 0 0;font-size:13px;line-height:1.55;color:#71837f">Nếu bạn không thực hiện đăng ký này, vui lòng bỏ qua email và liên hệ bộ phận hỗ trợ.</p>
                          </td></tr>
                          <tr><td style="padding:20px 32px;background:#173f37;font-size:12px;color:#dce9e5">© 2026 LuxeStay Hospitality Group</td></tr>
                        </table>
                      </td></tr>
                    </table>
                  </body>
                </html>
                """
                .replace("{{customerName}}", customerName)
                .replace("{{loginUrl}}", safeLoginUrl);
    }

    private String passwordResetTemplate(String customerName, String safeResetUrl, long expiresInMinutes) {
        return """
                <!doctype html>
                <html lang="vi">
                  <body style="margin:0;background:#f5f0e8;font-family:Arial,sans-serif;color:#17332d">
                    <div style="display:none;max-height:0;overflow:hidden;opacity:0">Yêu cầu đặt lại mật khẩu LuxeStay.</div>
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="padding:38px 14px;background:#f5f0e8">
                      <tr><td align="center">
                        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:620px;background:#fffdf9;border:1px solid #e5dccd;border-radius:24px;overflow:hidden;box-shadow:0 18px 50px rgba(23,51,45,.10)">
                          <tr><td style="padding:16px 32px;background:#d8a84e;color:#17332d;font-size:12px;font-weight:700;letter-spacing:2.4px;text-transform:uppercase">LuxeStay · Bảo mật tài khoản</td></tr>
                          <tr><td style="padding:36px 32px;background:#173f37;color:#ffffff">
                            <h1 style="margin:0;font-family:Georgia,serif;font-size:34px;line-height:1.16;font-weight:normal">Đặt lại mật khẩu</h1>
                          </td></tr>
                          <tr><td style="padding:32px">
                            <p style="margin:0 0 16px;font-size:17px">Xin chào <strong>%s</strong>,</p>
                            <p style="margin:0 0 20px;line-height:1.65;color:#49635d">Chúng tôi nhận được yêu cầu đặt lại mật khẩu LuxeStay. Liên kết chỉ sử dụng được một lần và có hiệu lực trong <strong>%d phút</strong>.</p>
                            <a href="%s" style="display:inline-block;padding:14px 24px;border-radius:999px;background:#d8a84e;color:#17332d;text-decoration:none;font-weight:700">Tạo mật khẩu mới →</a>
                            <p style="margin:26px 0 0;padding:14px 16px;background:#fff8e8;border-left:4px solid #d8a84e;font-size:13px;line-height:1.55;color:#5e5138">Nếu bạn không yêu cầu thao tác này, hãy bỏ qua email. Mật khẩu hiện tại của bạn vẫn an toàn.</p>
                          </td></tr>
                          <tr><td style="padding:20px 32px;background:#173f37;font-size:12px;color:#dce9e5">LuxeStay Hospitality Group · Hỗ trợ an toàn tài khoản</td></tr>
                        </table>
                      </td></tr>
                    </table>
                  </body>
                </html>
                """.formatted(customerName, expiresInMinutes, safeResetUrl);
    }

    private String invoiceTemplate(String invoiceNumber) {
        return """
                <!doctype html>
                <html lang="vi"><body style="margin:0;background:#f5f0e8;font-family:Arial,sans-serif;color:#17332d">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="padding:38px 14px;background:#f5f0e8"><tr><td align="center">
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:620px;background:#fffdf9;border:1px solid #e5dccd;border-radius:24px;overflow:hidden">
                      <tr><td style="padding:16px 32px;background:#d8a84e;font-size:12px;font-weight:700;letter-spacing:2.4px;text-transform:uppercase">LuxeStay · Chứng từ lưu trú</td></tr>
                      <tr><td style="padding:36px 32px;background:#173f37;color:#fff"><h1 style="margin:0;font-family:Georgia,serif;font-size:32px;font-weight:normal">Hóa đơn của bạn đã sẵn sàng</h1></td></tr>
                      <tr><td style="padding:32px"><p style="margin:0 0 18px;line-height:1.65">Kính gửi quý khách,</p><p style="line-height:1.65;color:#49635d">Hóa đơn <strong>%s</strong> được đính kèm trong email này. Vui lòng lưu lại để đối chiếu khi cần.</p><div style="margin-top:24px;padding:16px;border:1px solid #e5dccd;border-radius:14px;background:#f1eee7"><strong>Tệp đính kèm:</strong> %s.pdf</div></td></tr>
                      <tr><td style="padding:20px 32px;background:#173f37;font-size:12px;color:#dce9e5">LuxeStay Hospitality Group</td></tr>
                    </table>
                  </td></tr></table>
                </body></html>
                """.formatted(invoiceNumber, invoiceNumber);
    }

    private record BookingConfirmationContent(
            String recipientEmail,
            String idempotencyKey,
            String templateVersion,
            String subject,
            String bodyHtml,
            String bodyText) {
    }
}
