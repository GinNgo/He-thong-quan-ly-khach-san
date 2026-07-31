package com.hotel.propertycommerce.invoice;

import com.hotel.entities.User;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.propertycommerce.payment.PropertyFinancialTransaction;
import com.hotel.services.EmailService;
import com.hotel.services.PropertyAccessService;
import com.hotel.security.ActionCode;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
public class PropertyInvoiceController {

    private final PropertyInvoiceRepository invoiceRepository;
    private final PropertyInvoiceLineRepository lineRepository;
    private final PropertyInvoicePaymentAllocationRepository allocationRepository;
    private final PropertyCreditNoteRepository creditNoteRepository;
    private final PropertyCreditNoteLineRepository creditNoteLineRepository;
    private final CreditNoteService creditNoteService;
    private final PropertyInvoiceDocumentService documentService;
    private final PropertyAccessService propertyAccessService;
    private final EmailService emailService;

    public PropertyInvoiceController(
            PropertyInvoiceRepository invoiceRepository,
            PropertyInvoiceLineRepository lineRepository,
            PropertyInvoicePaymentAllocationRepository allocationRepository,
            PropertyCreditNoteRepository creditNoteRepository,
            PropertyCreditNoteLineRepository creditNoteLineRepository,
            CreditNoteService creditNoteService,
            PropertyInvoiceDocumentService documentService,
            PropertyAccessService propertyAccessService,
            EmailService emailService) {
        this.invoiceRepository = invoiceRepository;
        this.lineRepository = lineRepository;
        this.allocationRepository = allocationRepository;
        this.creditNoteRepository = creditNoteRepository;
        this.creditNoteLineRepository = creditNoteLineRepository;
        this.creditNoteService = creditNoteService;
        this.documentService = documentService;
        this.propertyAccessService = propertyAccessService;
        this.emailService = emailService;
    }

    @GetMapping("/api/invoices/{invoiceId}")
    public InvoiceResponse get(@PathVariable Long invoiceId) {
        PropertyInvoice invoice = findAuthorized(invoiceId);
        return InvoiceResponse.from(
                invoice,
                lineRepository.findByInvoiceIdOrderByIdAsc(invoiceId),
                allocationRepository.findByInvoiceIdOrderByIdAsc(invoiceId),
                creditNoteRepository.findByInvoiceIdOrderByIssuedAtAscIdAsc(invoiceId),
                creditNoteLineRepository.findByHotelIdAndInvoiceIdOrderByIdAsc(
                        invoice.getHotel().getId(), invoiceId));
    }

    @GetMapping(value = "/api/invoices/{invoiceId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<ByteArrayResource> pdf(@PathVariable Long invoiceId) {
        PropertyInvoice invoice = findAuthorized(invoiceId);
        byte[] content = documentService.renderPdf(
                invoice,
                lineRepository.findByInvoiceIdOrderByIdAsc(invoiceId),
                creditNoteRepository.findByInvoiceIdOrderByIssuedAtAscIdAsc(invoiceId));
        String filename = invoice.getInvoiceNumber().replaceAll("[^A-Za-z0-9._-]", "_") + ".pdf";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(content.length);
        return ResponseEntity.ok().headers(headers).body(new ByteArrayResource(content));
    }

    @PostMapping("/api/invoices/{invoiceId}/email")
    public InvoiceEmailResponse email(
            @PathVariable Long invoiceId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestBody(required = false) InvoiceEmailRequest request) {
        PropertyInvoice invoice = findAuthorized(invoiceId);
        String ownerEmail = invoice.getReservation().getUser() == null
                ? null
                : invoice.getReservation().getUser().getEmail();
        String recipient = request == null || request.recipient() == null || request.recipient().isBlank()
                ? ownerEmail
                : request.recipient().trim();
        authorizeRecipient(ownerEmail, recipient);

        byte[] content = documentService.renderPdf(
                invoice,
                lineRepository.findByInvoiceIdOrderByIdAsc(invoiceId),
                creditNoteRepository.findByInvoiceIdOrderByIssuedAtAscIdAsc(invoiceId));
        boolean sent = emailService.sendInvoiceEmail(recipient, invoice.getInvoiceNumber(), content);
        return new InvoiceEmailResponse(invoice.getId(), invoice.getInvoiceNumber(), recipient, sent, correlationId);
    }

    @PostMapping("/api/management/invoices/{invoiceId}/credit-notes")
    @Permission(function = FunctionCode.INVOICE_ADJUST, action = ActionCode.APPROVE)
    public CreditNoteResponse creditNote(
            @PathVariable Long invoiceId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestBody CreditNoteRequest request) {
        CreditNoteService.IssueCreditNoteCommand command = new CreditNoteService.IssueCreditNoteCommand(
                invoiceId,
                request == null ? null : request.reason(),
                request == null || request.lines() == null
                        ? null
                        : request.lines().stream()
                                .map(line -> new CreditNoteService.CreditLineCommand(
                                        line.invoiceLineId(), line.description(), line.amount()))
                                .toList(),
                request == null || request.correlationId() == null
                        ? correlationId
                        : request.correlationId());
        CreditNoteService.IssuedCreditNote issued = creditNoteService.issue(command);
        return CreditNoteResponse.from(issued);
    }

    private PropertyInvoice findAuthorized(Long invoiceId) {
        PropertyInvoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hóa đơn."));
        if (invoice.getStatus() != PropertyInvoice.Status.FINALIZED) {
            throw new ResourceNotFoundException("Không tìm thấy hóa đơn đã chốt.");
        }
        authorizeInvoice(invoice);
        return invoice;
    }

    private void authorizeInvoice(PropertyInvoice invoice) {
        if (propertyAccessService.isSystemAdministrator()) {
            return;
        }
        Long hotelId = invoice.getHotel() == null ? null : invoice.getHotel().getId();
        if (hotelId != null && propertyAccessService.accessibleHotelIds().contains(hotelId)) {
            return;
        }
        User current = propertyAccessService.currentUser();
        User owner = invoice.getReservation() == null ? null : invoice.getReservation().getUser();
        if (current.getId() != null && owner != null && current.getId().equals(owner.getId())) {
            return;
        }
        throw new ResourceNotFoundException("Không tìm thấy hóa đơn.");
    }

    private void authorizeRecipient(String ownerEmail, String recipient) {
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("Invoice recipient is required.");
        }
        if (ownerEmail != null && ownerEmail.equalsIgnoreCase(recipient)) {
            return;
        }
        if (propertyAccessService.isSystemAdministrator()) {
            return;
        }
        User current = propertyAccessService.currentUser();
        if (current.getEmail() != null && current.getEmail().equalsIgnoreCase(recipient)
                && propertyAccessService.accessibleHotelIds().size() > 0) {
            return;
        }
        throw new SecurityException("Invoice email recipient is not verified for this account.");
    }

    public record InvoiceEmailRequest(String recipient) {
    }

    public record InvoiceEmailResponse(
            Long invoiceId,
            String invoiceNumber,
            String recipient,
            boolean sent,
            String correlationId) {
    }

    public record CreditNoteRequest(
            String reason,
            List<CreditNoteLineRequest> lines,
            String correlationId) {
    }

    public record CreditNoteLineRequest(Long invoiceLineId, String description, BigDecimal amount) {
    }

    public record InvoiceResponse(
            Long id,
            Long reservationId,
            String invoiceNumber,
            String status,
            String currency,
            BigDecimal subtotal,
            BigDecimal taxAmount,
            BigDecimal feeAmount,
            BigDecimal discountAmount,
            BigDecimal totalAmount,
            BigDecimal paidAmount,
            BigDecimal refundedAmount,
            BigDecimal balanceAmount,
            String customerSnapshotJson,
            String propertySnapshotJson,
            LocalDateTime finalizedAt,
            List<InvoiceLineResponse> lines,
            List<AllocationResponse> allocations,
            List<CreditNoteResponse> creditNotes) {

        static InvoiceResponse from(
                PropertyInvoice invoice,
                List<PropertyInvoiceLine> lines,
                List<PropertyInvoicePaymentAllocation> allocations,
                List<PropertyCreditNote> creditNotes,
                List<PropertyCreditNoteLine> creditNoteLines) {
            return new InvoiceResponse(
                    invoice.getId(),
                    invoice.getReservation().getId(),
                    invoice.getInvoiceNumber(),
                    invoice.getStatus().name(),
                    invoice.getCurrency(),
                    invoice.getSubtotal(),
                    invoice.getTaxAmount(),
                    invoice.getFeeAmount(),
                    invoice.getDiscountAmount(),
                    invoice.getTotalAmount(),
                    invoice.getPaidAmount(),
                    invoice.getRefundedAmount(),
                    invoice.getBalanceAmount(),
                    invoice.getCustomerSnapshotJson(),
                    invoice.getPropertySnapshotJson(),
                    invoice.getFinalizedAt(),
                    lines == null ? List.of() : lines.stream().map(InvoiceLineResponse::from).toList(),
                    allocations == null ? List.of() : allocations.stream().map(AllocationResponse::from).toList(),
                    creditNotes == null ? List.of() : creditNotes.stream()
                            .map(note -> CreditNoteResponse.fromExisting(note, creditNoteLines))
                            .toList());
        }
    }

    public record InvoiceLineResponse(
            Long id,
            String lineType,
            String code,
            String name,
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal taxAmount,
            BigDecimal discountAmount,
            BigDecimal totalAmount,
            LocalDateTime usageStartedAt,
            LocalDateTime usageEndedAt) {

        static InvoiceLineResponse from(PropertyInvoiceLine line) {
            return new InvoiceLineResponse(
                    line.getId(),
                    line.getLineType().name(),
                    line.getCode(),
                    line.getName(),
                    line.getDescription(),
                    line.getQuantity(),
                    line.getUnitPrice(),
                    line.getTaxAmount(),
                    line.getDiscountAmount(),
                    line.getTotalAmount(),
                    line.getUsageStartedAt(),
                    line.getUsageEndedAt());
        }
    }

    public record AllocationResponse(
            Long id,
            Long transactionId,
            String transactionPublicId,
            BigDecimal allocatedAmount,
            String method,
            String provider,
            LocalDateTime occurredAt) {

        static AllocationResponse from(PropertyInvoicePaymentAllocation allocation) {
            PropertyFinancialTransaction transaction = allocation.getFinancialTransaction();
            return new AllocationResponse(
                    allocation.getId(),
                    transaction.getId(),
                    transaction.getPublicId(),
                    allocation.getAllocatedAmount(),
                    transaction.getMethod(),
                    transaction.getProvider(),
                    transaction.getOccurredAt());
        }
    }

    public record CreditNoteResponse(
            Long id,
            String creditNoteNumber,
            String reason,
            BigDecimal amount,
            LocalDateTime issuedAt,
            List<CreditNoteLineResponse> lines) {

        static CreditNoteResponse from(CreditNoteService.IssuedCreditNote issued) {
            return fromIssued(issued.creditNote(), issued.lines());
        }

        static CreditNoteResponse fromExisting(PropertyCreditNote note, List<PropertyCreditNoteLine> allLines) {
            List<CreditNoteLineResponse> lines = allLines == null ? List.of() : allLines.stream()
                    .filter(line -> line.getCreditNote().getId() != null
                            && line.getCreditNote().getId().equals(note.getId()))
                    .map(CreditNoteLineResponse::from)
                    .toList();
            return new CreditNoteResponse(
                    note.getId(),
                    note.getCreditNoteNumber(),
                    note.getReason(),
                    note.getAmount(),
                    note.getIssuedAt(),
                    lines);
        }

        private static CreditNoteResponse fromIssued(PropertyCreditNote note, List<PropertyCreditNoteLine> lines) {
            return new CreditNoteResponse(
                    note.getId(),
                    note.getCreditNoteNumber(),
                    note.getReason(),
                    note.getAmount(),
                    note.getIssuedAt(),
                    lines == null ? List.of() : lines.stream().map(CreditNoteLineResponse::from).toList());
        }
    }

    public record CreditNoteLineResponse(
            Long id,
            Long invoiceLineId,
            String description,
            BigDecimal amount) {

        static CreditNoteLineResponse from(PropertyCreditNoteLine line) {
            return new CreditNoteLineResponse(
                    line.getId(),
                    line.getInvoiceLine() == null ? null : line.getInvoiceLine().getId(),
                    line.getDescription(),
                    line.getAmount());
        }
    }
}
