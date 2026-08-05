package com.hotel.services;

import com.hotel.observability.OperationalMetrics;
import com.hotel.platformbilling.order.SubscriptionOrder;
import com.hotel.platformbilling.payment.PlatformFinancialTransaction;
import com.hotel.platformbilling.payment.PlatformPaymentAttempt;
import com.hotel.propertycommerce.payment.PropertyFinancialTransaction;
import com.hotel.propertycommerce.payment.PropertyPaymentAttempt;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.Locale;

@Service
public class PaymentReceiptEmailService {

    private static final Logger log = LoggerFactory.getLogger(PaymentReceiptEmailService.class);

    private final JavaMailSender mailSender;
    private final OperationalMetrics operationalMetrics;

    @Value("${spring.mail.username:noreply@hotel.com}")
    private String fromEmail;

    @Value("${app.mail.payment-receipt-enabled:true}")
    private boolean enabled;

    public PaymentReceiptEmailService(JavaMailSender mailSender, OperationalMetrics operationalMetrics) {
        this.mailSender = mailSender;
        this.operationalMetrics = operationalMetrics;
    }

    public void sendPropertyReceiptAfterCommit(
            PropertyPaymentAttempt attempt,
            PropertyFinancialTransaction transaction) {
        if (attempt == null || transaction == null) {
            return;
        }
        String recipient = attempt.getReservation().getUser() == null
                ? null : attempt.getReservation().getUser().getEmail();
        String customerName = attempt.getReservation().getUser() == null
                ? null : attempt.getReservation().getUser().getFullName();
        Receipt receipt = new Receipt(
                recipient,
                customerName,
                "Thanh toán đặt phòng thành công / Booking payment received",
                "Đặt phòng / Reservation #" + attempt.getReservation().getId(),
                attempt.getHotel().getName(),
                attempt.getPurpose().name(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getMethod(),
                transaction.getProvider(),
                transaction.getProviderTransactionReference(),
                transaction.getPublicId());
        afterCommit(() -> send(receipt, "property_payment_receipt"));
    }

    public void sendPlatformReceiptAfterCommit(
            PlatformPaymentAttempt attempt,
            PlatformFinancialTransaction transaction) {
        if (attempt == null || transaction == null) {
            return;
        }
        SubscriptionOrder order = attempt.getOrder();
        String recipient = order.getOwner() == null ? null : order.getOwner().getEmail();
        String customerName = order.getOwner() == null ? null : order.getOwner().getFullName();
        Receipt receipt = new Receipt(
                recipient,
                customerName,
                "Thanh toán gói LuxeStay thành công / Subscription payment received",
                order.getPlanName() + " - " + order.getOrderCode(),
                order.getTargetHotel().getName(),
                order.getOperation().name(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getMethod(),
                transaction.getProvider(),
                transaction.getProviderTransactionReference(),
                transaction.getPublicId());
        afterCommit(() -> send(receipt, "platform_payment_receipt"));
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private boolean send(Receipt receipt, String metricOperation) {
        if (!enabled || receipt.recipient() == null || receipt.recipient().isBlank()) {
            log.info("MAIL_DELIVERY template={} outcome=disabled_or_invalid", metricOperation);
            return false;
        }
        long startedAt = System.nanoTime();
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(receipt.recipient().strip());
            helper.setSubject(receipt.subject());
            helper.setText(template(receipt), true);
            mailSender.send(message);
            operationalMetrics.recordExternal("mail", metricOperation, false, elapsed(startedAt));
            return true;
        } catch (Exception exception) {
            operationalMetrics.recordExternal("mail", metricOperation, true, elapsed(startedAt));
            log.warn("MAIL_DELIVERY template={} outcome=failure type={}",
                    metricOperation, exception.getClass().getSimpleName());
            return false;
        }
    }

    private String template(Receipt receipt) {
        String name = safe(defaultText(receipt.customerName(), "Quý khách / Guest"));
        String reference = safe(receipt.reference());
        String property = safe(receipt.propertyName());
        String purpose = safe(receipt.purpose());
        String method = safe(receipt.method());
        String provider = safe(receipt.provider());
        String providerReference = safe(defaultText(receipt.providerReference(), "N/A"));
        String transactionId = safe(receipt.transactionId());
        String amount = safe(formatMoney(receipt.amount(), receipt.currency()));
        return """
                <!doctype html>
                <html lang="vi">
                  <body style="margin:0;background:#f2f7f4;font-family:Arial,sans-serif;color:#17332d">
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="padding:30px 14px;background:#f2f7f4">
                      <tr><td align="center">
                        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:640px;background:#fff;border:1px solid #d8e6df;border-radius:20px;overflow:hidden">
                          <tr><td style="padding:28px 34px;background:#124e43;color:#fff"><div style="font-size:13px;letter-spacing:2px;text-transform:uppercase;opacity:.8">LuxeStay</div><h1 style="margin:8px 0 0;font-size:27px">Thanh toán thành công</h1><p style="margin:8px 0 0;opacity:.85">Payment received successfully</p></td></tr>
                          <tr><td style="padding:32px 34px"><p>Xin chào <strong>%s</strong>,</p><p style="line-height:1.65;color:#526a64">LuxeStay đã ghi nhận giao dịch sau. Vui lòng giữ email này để đối chiếu.<br>We have recorded the transaction below. Please keep this email for reference.</p><div style="margin:24px 0;padding:20px;border-radius:14px;background:#edf6f2"><div style="font-size:12px;text-transform:uppercase;color:#66817a">Số tiền / Amount</div><div style="margin-top:5px;font-size:28px;font-weight:700;color:#0f766e">%s</div></div><table role="presentation" width="100%%" cellspacing="0" cellpadding="8" style="font-size:14px"><tr><td style="color:#66817a">Nội dung / Reference</td><td align="right"><strong>%s</strong></td></tr><tr><td style="color:#66817a">Cơ sở / Property</td><td align="right">%s</td></tr><tr><td style="color:#66817a">Loại / Purpose</td><td align="right">%s</td></tr><tr><td style="color:#66817a">Phương thức / Method</td><td align="right">%s · %s</td></tr><tr><td style="color:#66817a">Mã nhà cung cấp / Provider ref</td><td align="right">%s</td></tr><tr><td style="color:#66817a">Mã LuxeStay / Transaction ID</td><td align="right">%s</td></tr></table><p style="margin:26px 0 0;font-size:13px;line-height:1.55;color:#71837f">Email này là thông báo xác nhận, không yêu cầu bạn cung cấp mật khẩu, OTP hoặc thông tin thẻ.<br>This confirmation never asks for your password, OTP, or card details.</p></td></tr>
                          <tr><td style="padding:18px 34px;background:#e7f1ed;font-size:12px;color:#66817a">LuxeStay · Nền tảng quản lý và đặt phòng</td></tr>
                        </table>
                      </td></tr>
                    </table>
                  </body>
                </html>
                """.formatted(name, amount, reference, property, purpose, method, provider,
                providerReference, transactionId);
    }

    private String formatMoney(BigDecimal amount, String currency) {
        NumberFormat formatter = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"));
        return formatter.format(amount == null ? BigDecimal.ZERO : amount) + " "
                + defaultText(currency, "VND");
    }

    private String safe(String value) {
        return HtmlUtils.htmlEscape(defaultText(value, "N/A"));
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private Duration elapsed(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt);
    }

    private record Receipt(
            String recipient,
            String customerName,
            String subject,
            String reference,
            String propertyName,
            String purpose,
            BigDecimal amount,
            String currency,
            String method,
            String provider,
            String providerReference,
            String transactionId) {
    }
}
