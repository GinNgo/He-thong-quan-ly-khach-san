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

import java.time.Duration;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final OperationalMetrics operationalMetrics;

    @Value("${spring.mail.username:noreply@hotel.com}")
    private String fromEmail;

    public EmailService(JavaMailSender mailSender, OperationalMetrics operationalMetrics) {
        this.mailSender = mailSender;
        this.operationalMetrics = operationalMetrics;
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

    private Duration elapsed(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt);
    }
}
