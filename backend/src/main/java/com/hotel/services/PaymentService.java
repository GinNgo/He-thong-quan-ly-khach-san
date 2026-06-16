package com.hotel.services;

import com.hotel.dtos.PaymentDTO;
import java.util.List;

public interface PaymentService {
    List<PaymentDTO> getPaymentsByReservation(Long reservationId);
    PaymentDTO processPayment(PaymentDTO dto);
}
