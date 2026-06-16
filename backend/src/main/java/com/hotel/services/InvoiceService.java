package com.hotel.services;

import com.hotel.dtos.InvoiceDTO;

public interface InvoiceService {
    InvoiceDTO getInvoiceByReservation(Long reservationId);
    InvoiceDTO generateInvoice(Long reservationId);
}
