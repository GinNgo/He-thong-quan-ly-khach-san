package com.hotel.services.impl;

import com.hotel.dtos.InvoiceDTO;
import com.hotel.services.InvoiceService;
import org.springframework.stereotype.Service;

@Service
public class InvoiceServiceImpl implements InvoiceService {
    @Override
    public InvoiceDTO getInvoiceByReservation(Long reservationId) {
        return new InvoiceDTO();
    }

    @Override
    public InvoiceDTO generateInvoice(Long reservationId) {
        return new InvoiceDTO();
    }
}
