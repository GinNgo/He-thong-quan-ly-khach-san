package com.hotel.controllers;

import com.hotel.dtos.InvoiceDTO;
import com.hotel.entities.Invoice;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.propertycommerce.invoice.PropertyInvoice;
import com.hotel.propertycommerce.invoice.PropertyInvoiceRepository;
import com.hotel.repositories.InvoiceRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.security.ActionCode;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import com.hotel.services.PropertyAccessService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * Compatibility facade for the retired mutable invoice model.
 * Active list/reservation paths now expose finalized Property Commerce invoices only.
 */
@RestController
@CrossOrigin(origins = "*", maxAge = 3600)
public class InvoiceController {

    private final InvoiceRepository legacyInvoiceRepository;
    private final PropertyInvoiceRepository invoiceRepository;
    private final ReservationRepository reservationRepository;
    private final PropertyAccessService propertyAccessService;

    public InvoiceController(
            InvoiceRepository legacyInvoiceRepository,
            PropertyInvoiceRepository invoiceRepository,
            ReservationRepository reservationRepository,
            PropertyAccessService propertyAccessService) {
        this.legacyInvoiceRepository = legacyInvoiceRepository;
        this.invoiceRepository = invoiceRepository;
        this.reservationRepository = reservationRepository;
        this.propertyAccessService = propertyAccessService;
    }

    @GetMapping("/api/invoices")
    @Permission(function = FunctionCode.INVOICE, action = ActionCode.VIEW)
    public ResponseEntity<List<InvoiceDTO>> getAllInvoices() {
        List<PropertyInvoice> invoices;
        if (propertyAccessService.isSystemAdministrator()) {
            invoices = invoiceRepository.findByStatusOrderByFinalizedAtDesc(PropertyInvoice.Status.FINALIZED);
        } else {
            Set<Long> hotelIds = propertyAccessService.accessibleHotelIds();
            invoices = hotelIds == null || hotelIds.isEmpty()
                    ? List.of()
                    : invoiceRepository.findByHotelIdInAndStatusOrderByFinalizedAtDesc(
                            hotelIds.stream().sorted().toList(), PropertyInvoice.Status.FINALIZED);
        }
        return ResponseEntity.ok(invoices.stream().map(this::toCompatibilityDto).toList());
    }

    /** Read-only legacy lookup retained outside the canonical /api/invoices/{id} namespace. */
    @Deprecated(forRemoval = false)
    @GetMapping("/api/legacy/invoices/{id}")
    @Permission(function = FunctionCode.INVOICE, action = ActionCode.VIEW)
    public ResponseEntity<InvoiceDTO> getLegacyInvoiceById(@PathVariable Long id) {
        return legacyInvoiceRepository.findById(id)
                .filter(this::canAccessLegacyInvoice)
                .map(this::toLegacyDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/api/invoices/reservation/{reservationId}")
    @Permission(function = FunctionCode.INVOICE, action = ActionCode.VIEW)
    public ResponseEntity<InvoiceDTO> getInvoiceByReservation(@PathVariable Long reservationId) {
        return finalizedByReservation(reservationId, false);
    }

    /**
     * Deprecated write-shaped route. It never creates an invoice; equivalent retries return the
     * one finalized checkout snapshot or 409 while checkout/finalization is incomplete.
     */
    @Deprecated(forRemoval = false)
    @PostMapping("/api/invoices/reservation/{reservationId}")
    @Permission(function = FunctionCode.INVOICE, action = ActionCode.CREATE)
    public ResponseEntity<InvoiceDTO> generateInvoice(@PathVariable Long reservationId) {
        return finalizedByReservation(reservationId, true);
    }

    private ResponseEntity<InvoiceDTO> finalizedByReservation(Long reservationId, boolean conflictWhenMissing) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .filter(this::canAccessReservation)
                .orElse(null);
        if (reservation == null) {
            return ResponseEntity.notFound().build();
        }
        PropertyInvoice invoice = invoiceRepository.findByReservationIdAndStatus(
                        reservationId, PropertyInvoice.Status.FINALIZED)
                .orElse(null);
        if (invoice != null) {
            return ResponseEntity.ok(toCompatibilityDto(invoice));
        }
        return conflictWhenMissing
                ? ResponseEntity.status(409).build()
                : ResponseEntity.notFound().build();
    }

    private boolean canAccess(PropertyInvoice invoice) {
        if (propertyAccessService.isSystemAdministrator()) {
            return true;
        }
        Long hotelId = invoice.getHotel() == null ? null : invoice.getHotel().getId();
        Set<Long> hotelIds = propertyAccessService.accessibleHotelIds();
        return hotelId != null && hotelIds != null && hotelIds.contains(hotelId);
    }

    private boolean canAccessReservation(Reservation reservation) {
        if (propertyAccessService.isSystemAdministrator()) {
            return true;
        }
        User current = propertyAccessService.currentUser();
        User owner = reservation.getUser();
        if (current.getId() != null && owner != null && current.getId().equals(owner.getId())) {
            return true;
        }
        Long hotelId = reservation.getHotel() == null ? null : reservation.getHotel().getId();
        Set<Long> hotelIds = propertyAccessService.accessibleHotelIds();
        return hotelId != null && hotelIds != null && hotelIds.contains(hotelId);
    }

    private boolean canAccessLegacyInvoice(Invoice invoice) {
        if (propertyAccessService.isSystemAdministrator()) {
            return true;
        }
        Long hotelId = invoice.getReservation() == null || invoice.getReservation().getHotel() == null
                ? null
                : invoice.getReservation().getHotel().getId();
        Set<Long> hotelIds = propertyAccessService.accessibleHotelIds();
        return hotelId != null && hotelIds != null && hotelIds.contains(hotelId);
    }

    private InvoiceDTO toCompatibilityDto(PropertyInvoice invoice) {
        InvoiceDTO dto = new InvoiceDTO();
        dto.setId(invoice.getId());
        dto.setInvoiceCode(invoice.getInvoiceNumber());
        dto.setReservationId(invoice.getReservation().getId());
        dto.setIssueDate(invoice.getFinalizedAt().toLocalDate());
        dto.setTotalAmount(invoice.getTotalAmount());
        dto.setStatus(invoice.getStatus().name());
        return dto;
    }

    private InvoiceDTO toLegacyDto(Invoice invoice) {
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
