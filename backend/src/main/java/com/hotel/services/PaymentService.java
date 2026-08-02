package com.hotel.services;

import com.hotel.domain.lifecycle.PaymentStatus;
import com.hotel.domain.payment.PaymentCompletionResult;
import com.hotel.dtos.PaymentDTO;
import java.util.List;

public interface PaymentService {
    List<PaymentDTO> getPaymentsByReservation(Long reservationId);
    PaymentCompletionResult handleSuccessfulPayment(Long reservationId, String method, String transactionId);
    PaymentCompletionResult handleSuccessfulPayment(
            Long reservationId,
            String method,
            String transactionId,
            PaymentStatus currentPaymentStatus);
    void refundSuccessfulPayments(Long reservationId);
}
