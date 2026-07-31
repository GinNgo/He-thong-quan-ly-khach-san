package com.hotel.propertycommerce.invoice;

import com.hotel.entities.User;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.security.ActionCode;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.FunctionCode;
import com.hotel.services.PropertyAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CreditNoteService {

    private static final int MAX_LINES = 100;

    private final PropertyInvoiceRepository invoiceRepository;
    private final PropertyInvoiceLineRepository invoiceLineRepository;
    private final PropertyCreditNoteRepository creditNoteRepository;
    private final PropertyCreditNoteLineRepository creditNoteLineRepository;
    private final PropertyAccessService propertyAccessService;
    private final FinancialAuditService auditService;
    private final Clock clock;

    @Autowired
    public CreditNoteService(
            PropertyInvoiceRepository invoiceRepository,
            PropertyInvoiceLineRepository invoiceLineRepository,
            PropertyCreditNoteRepository creditNoteRepository,
            PropertyCreditNoteLineRepository creditNoteLineRepository,
            PropertyAccessService propertyAccessService,
            FinancialAuditService auditService) {
        this(invoiceRepository, invoiceLineRepository, creditNoteRepository, creditNoteLineRepository,
                propertyAccessService, auditService, Clock.systemUTC());
    }

    CreditNoteService(
            PropertyInvoiceRepository invoiceRepository,
            PropertyInvoiceLineRepository invoiceLineRepository,
            PropertyCreditNoteRepository creditNoteRepository,
            PropertyCreditNoteLineRepository creditNoteLineRepository,
            PropertyAccessService propertyAccessService,
            FinancialAuditService auditService,
            Clock clock) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceLineRepository = invoiceLineRepository;
        this.creditNoteRepository = creditNoteRepository;
        this.creditNoteLineRepository = creditNoteLineRepository;
        this.propertyAccessService = propertyAccessService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public IssuedCreditNote issue(IssueCreditNoteCommand command) {
        validate(command);
        requireApprovePermission();
        User actor = propertyAccessService.currentUser();
        PropertyInvoice invoice = invoiceRepository.findByIdForUpdate(command.invoiceId())
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        authorize(invoice);
        if (invoice.getStatus() != PropertyInvoice.Status.FINALIZED) {
            throw new FinancialException(
                    FinancialErrorCode.INVALID_STATE_TRANSITION,
                    "Credit notes can only correct a finalized invoice.",
                    null,
                    invoice.getStatus().name(),
                    null);
        }

        Long hotelId = invoice.getHotel().getId();
        List<PropertyInvoiceLine> invoiceLines =
                invoiceLineRepository.findByHotelIdAndInvoiceIdOrderByIdAsc(hotelId, invoice.getId());
        Map<Long, PropertyInvoiceLine> invoiceLineById = invoiceLines.stream()
                .filter(line -> line.getId() != null)
                .collect(Collectors.toMap(PropertyInvoiceLine::getId, Function.identity()));
        List<PropertyCreditNote> existingNotes =
                creditNoteRepository.findByHotelIdAndInvoiceIdOrderByIssuedAtAscIdAsc(hotelId, invoice.getId());
        List<PropertyCreditNoteLine> existingLines =
                creditNoteLineRepository.findByHotelIdAndInvoiceIdOrderByIdAsc(hotelId, invoice.getId());

        List<ValidatedLine> validatedLines = validateLines(command.lines(), invoiceLineById);
        BigDecimal requestedAmount = validatedLines.stream()
                .map(line -> line.amount().amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal existingAmount = existingNotes.stream()
                .map(PropertyCreditNote::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (existingAmount.add(requestedAmount).compareTo(invoice.getTotalAmount()) > 0) {
            throw exceedsValue("Cumulative credit notes cannot exceed the finalized invoice total.");
        }
        validateLineCaps(validatedLines, existingLines);

        LocalDateTime issuedAt = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        PropertyCreditNote note = PropertyCreditNote.issue(
                invoice,
                creditNoteNumber(invoice, existingNotes.size() + 1L),
                command.reason(),
                VndMoney.of(requestedAmount),
                actor,
                actor,
                issuedAt);
        note = creditNoteRepository.saveAndFlush(note);

        List<PropertyCreditNoteLine> lines = new ArrayList<>(validatedLines.size());
        for (ValidatedLine validated : validatedLines) {
            lines.add(PropertyCreditNoteLine.snapshot(
                    note,
                    validated.invoiceLine(),
                    validated.description(),
                    validated.amount()));
        }
        lines = creditNoteLineRepository.saveAllAndFlush(lines);
        audit(note, lines, command.correlationId());
        return new IssuedCreditNote(note, List.copyOf(lines));
    }

    private List<ValidatedLine> validateLines(
            List<CreditLineCommand> commands,
            Map<Long, PropertyInvoiceLine> invoiceLineById) {
        List<ValidatedLine> lines = new ArrayList<>(commands.size());
        for (CreditLineCommand command : commands) {
            if (command == null || command.description() == null || command.description().isBlank()
                    || command.description().trim().length() > 1000 || command.amount() == null) {
                throw new IllegalArgumentException("Every credit-note line requires a description and amount.");
            }
            PropertyInvoiceLine invoiceLine = null;
            if (command.invoiceLineId() != null) {
                invoiceLine = invoiceLineById.get(command.invoiceLineId());
                if (invoiceLine == null) {
                    throw new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND);
                }
                if (invoiceLine.economicEffect().signum() <= 0) {
                    throw exceedsValue("Only a positive finalized invoice line can be credited.");
                }
            }
            lines.add(new ValidatedLine(
                    invoiceLine,
                    command.description().trim(),
                    requirePositiveVnd(command.amount())));
        }
        return lines;
    }

    private void validateLineCaps(
            List<ValidatedLine> requested,
            List<PropertyCreditNoteLine> existing) {
        Map<Long, BigDecimal> creditedByLine = new LinkedHashMap<>();
        for (PropertyCreditNoteLine line : existing) {
            if (line.getInvoiceLine() != null && line.getInvoiceLine().getId() != null) {
                creditedByLine.merge(line.getInvoiceLine().getId(), line.getAmount(), BigDecimal::add);
            }
        }
        for (ValidatedLine line : requested) {
            if (line.invoiceLine() == null) {
                continue;
            }
            Long invoiceLineId = line.invoiceLine().getId();
            BigDecimal cumulative = creditedByLine.merge(
                    invoiceLineId,
                    line.amount().amount(),
                    BigDecimal::add);
            if (cumulative.compareTo(line.invoiceLine().economicEffect()) > 0) {
                throw exceedsValue("Cumulative credit cannot exceed the referenced invoice line value.");
            }
        }
    }

    private void authorize(PropertyInvoice invoice) {
        Long hotelId = invoice.getHotel() == null ? null : invoice.getHotel().getId();
        if (hotelId == null || (!propertyAccessService.isSystemAdministrator()
                && !propertyAccessService.accessibleHotelIds().contains(hotelId))) {
            throw new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private void requireApprovePermission() {
        if (propertyAccessService.isSystemAdministrator()) {
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication != null && authentication.getPrincipal() instanceof CustomUserDetails details)) {
            throw new FinancialException(FinancialErrorCode.TENANT_ACCESS_DENIED);
        }
        Integer mask = details.getPermissionMasks() == null
                ? null
                : details.getPermissionMasks().get(FunctionCode.INVOICE_ADJUST);
        if (mask == null || (mask & ActionCode.APPROVE) != ActionCode.APPROVE) {
            throw new FinancialException(FinancialErrorCode.TENANT_ACCESS_DENIED);
        }
    }

    private VndMoney requirePositiveVnd(BigDecimal value) {
        try {
            VndMoney amount = VndMoney.of(value);
            if (amount.amount().signum() <= 0 || amount.amount().precision() > 19) {
                throw new ArithmeticException("amount outside supported range");
            }
            return amount;
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw new FinancialException(
                    FinancialErrorCode.INVALID_AMOUNT,
                    "Credit-note amounts must be positive integer VND values.");
        }
    }

    private FinancialException exceedsValue(String message) {
        return new FinancialException(FinancialErrorCode.INVALID_AMOUNT, message);
    }

    private String creditNoteNumber(PropertyInvoice invoice, long sequence) {
        return "CN-" + invoice.getHotel().getId() + "-" + invoice.getId() + "-" + sequence;
    }

    private void audit(
            PropertyCreditNote note,
            List<PropertyCreditNoteLine> lines,
            String correlationId) {
        long referencedLineCount = lines.stream().filter(line -> line.getInvoiceLine() != null).count();
        auditService.append(new FinancialAuditService.AuditCommand(
                "PROPERTY_COMMERCE",
                note.getHotel().getId(),
                "PROPERTY_CREDIT_NOTE",
                note.getId() == null ? note.getCreditNoteNumber() : note.getId().toString(),
                "USER",
                note.getActor().getId(),
                "CREDIT_NOTE_ISSUED",
                PropertyInvoice.Status.FINALIZED.name(),
                "CREDIT_NOTE_APPENDED",
                note.getReason(),
                null,
                null,
                correlationId,
                Map.of(
                        "invoiceId", note.getInvoice().getId(),
                        "invoiceNumber", note.getInvoice().getInvoiceNumber(),
                        "creditNoteNumber", note.getCreditNoteNumber(),
                        "amount", note.getAmount(),
                        "lineCount", lines.size(),
                        "referencedLineCount", referencedLineCount)));
    }

    private void validate(IssueCreditNoteCommand command) {
        if (command == null || command.invoiceId() == null
                || command.reason() == null || command.reason().isBlank()) {
            throw new IllegalArgumentException("Invoice and credit-note reason are required.");
        }
        if (command.reason().trim().length() > 1000) {
            throw new IllegalArgumentException("Credit-note reason is too long.");
        }
        if (command.lines() == null || command.lines().isEmpty() || command.lines().size() > MAX_LINES) {
            throw new IllegalArgumentException("A credit note requires between 1 and 100 correction lines.");
        }
    }

    private record ValidatedLine(PropertyInvoiceLine invoiceLine, String description, VndMoney amount) {
    }

    public record IssueCreditNoteCommand(
            Long invoiceId,
            String reason,
            List<CreditLineCommand> lines,
            String correlationId) {
    }

    public record CreditLineCommand(Long invoiceLineId, String description, BigDecimal amount) {
    }

    public record IssuedCreditNote(PropertyCreditNote creditNote, List<PropertyCreditNoteLine> lines) {
    }
}
