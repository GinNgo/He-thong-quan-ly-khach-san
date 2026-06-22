package com.hotel.services.impl;

import com.hotel.dtos.InvoiceDTO;
import com.hotel.services.InvoiceService;
import com.hotel.entities.Invoice;
import com.hotel.entities.Reservation;
import com.hotel.repositories.InvoiceRepository;
import com.hotel.repositories.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class InvoiceServiceImpl implements InvoiceService {
    private final InvoiceRepository invoiceRepository;
    private final ReservationRepository reservationRepository;

    public InvoiceServiceImpl(InvoiceRepository invoiceRepository, ReservationRepository reservationRepository) {
        this.invoiceRepository = invoiceRepository;
        this.reservationRepository = reservationRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceDTO getInvoiceByReservation(Long reservationId) {
        return invoiceRepository.findAll().stream()
                .filter(inv -> inv.getReservation().getId().equals(reservationId))
                .map(this::mapToDTO)
                .findFirst()
                .orElse(null);
    }

    @Override
    @Transactional
    public InvoiceDTO generateInvoice(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));
        
        Invoice invoice = new Invoice();
        invoice.setReservation(reservation);
        invoice.setInvoiceCode("INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        invoice.setIssueDate(LocalDate.now());
        invoice.setTotalAmount(reservation.getTotalAmount());
        invoice.setStatus("PAID");
        
        Invoice saved = invoiceRepository.save(invoice);
        return mapToDTO(saved);
    }

    private InvoiceDTO mapToDTO(Invoice invoice) {
        InvoiceDTO dto = new InvoiceDTO();
        dto.setId(invoice.getId());
        dto.setInvoiceCode(invoice.getInvoiceCode());
        dto.setReservationId(invoice.getReservation().getId());
        dto.setIssueDate(invoice.getIssueDate());
        dto.setTotalAmount(invoice.getTotalAmount());
        dto.setStatus(invoice.getStatus());
        return dto;
    }
}
