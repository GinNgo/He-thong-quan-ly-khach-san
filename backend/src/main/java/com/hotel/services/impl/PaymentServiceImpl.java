package com.hotel.services.impl;

import com.hotel.dtos.PaymentDTO;
import com.hotel.services.PaymentService;
import org.springframework.stereotype.Service;
import java.util.Collections;
import java.util.List;

@Service
public class PaymentServiceImpl implements PaymentService {
    @Override
    public List<PaymentDTO> getPaymentsByReservation(Long reservationId) {
        return Collections.emptyList();
    }

    @Override
    public PaymentDTO processPayment(PaymentDTO dto) {
        return dto;
    }
}
