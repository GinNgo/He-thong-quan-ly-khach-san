package com.hotel.services;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@hotel.com}")
    private String fromEmail;

    public void sendBookingConfirmation(String toEmail, String customerName, Long reservationId, String checkIn, String checkOut) {
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
        } catch (Exception e) {
            System.err.println("Failed to send email to " + toEmail + ": " + e.getMessage());
        }
    }

    public boolean sendInvoiceEmail(String toEmail, String invoiceNumber, byte[] pdf) {
        if (toEmail == null || toEmail.isBlank() || pdf == null || pdf.length == 0) {
            return false;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail.trim());
            helper.setSubject("Invoice " + invoiceNumber);
            helper.setText("Your finalized invoice " + invoiceNumber + " is attached.", false);
            helper.addAttachment(invoiceNumber + ".pdf", new ByteArrayResource(pdf), "application/pdf");
            mailSender.send(message);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }
}
