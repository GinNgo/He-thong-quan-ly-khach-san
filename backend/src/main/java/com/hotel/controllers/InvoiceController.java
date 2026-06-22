package com.hotel.controllers;

import com.hotel.dtos.InvoiceDTO;
import com.hotel.entities.Invoice;
import com.hotel.repositories.InvoiceRepository;
import com.hotel.services.InvoiceService;
import org.springframework.http.ResponseEntity;
import com.hotel.security.Permission;
import com.hotel.security.FunctionCode;
import com.hotel.security.ActionCode;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/invoices")
@CrossOrigin(origins = "*", maxAge = 3600)
public class InvoiceController {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceRepository invoiceRepository, InvoiceService invoiceService) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceService = invoiceService;
    }

    @GetMapping
    @Permission(function = FunctionCode.INVOICE, action = ActionCode.VIEW)
    public ResponseEntity<List<Invoice>> getAllInvoices() {
        return ResponseEntity.ok(invoiceRepository.findAll());
    }

    @GetMapping("/{id}")
    @Permission(function = FunctionCode.INVOICE, action = ActionCode.VIEW)
    public ResponseEntity<Invoice> getInvoiceById(@PathVariable Long id) {
        return invoiceRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/reservation/{reservationId}")
    @Permission(function = FunctionCode.INVOICE, action = ActionCode.CREATE)
    public ResponseEntity<InvoiceDTO> generateInvoice(@PathVariable Long reservationId) {
        return ResponseEntity.ok(invoiceService.generateInvoice(reservationId));
    }

    @GetMapping("/reservation/{reservationId}")
    @Permission(function = FunctionCode.INVOICE, action = ActionCode.VIEW)
    public ResponseEntity<InvoiceDTO> getInvoiceByReservation(@PathVariable Long reservationId) {
        return ResponseEntity.ok(invoiceService.getInvoiceByReservation(reservationId));
    }
}
