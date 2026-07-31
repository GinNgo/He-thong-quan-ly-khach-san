package com.hotel.propertycommerce.invoice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.propertycommerce.checkout.CheckoutOverride;
import com.hotel.propertycommerce.checkout.CheckoutOverrideRepository;
import com.hotel.propertycommerce.checkout.CheckoutPreviewService;
import com.hotel.propertycommerce.checkout.FolioCalculationService;
import com.hotel.propertycommerce.folio.ReservationChargeLine;
import com.hotel.propertycommerce.folio.ReservationChargeLineRepository;
import com.hotel.propertycommerce.payment.PropertyFinancialTransaction;
import com.hotel.propertycommerce.payment.PropertyFinancialTransactionRepository;
import com.hotel.repositories.ReservationRepository;
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
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InvoiceFinalizationService {

    private static final String CHECKED_IN = "CHECKED_IN";

    private final ReservationRepository reservationRepository;
    private final FolioCalculationService folioCalculationService;
    private final CheckoutOverrideRepository checkoutOverrideRepository;
    private final ReservationChargeLineRepository chargeLineRepository;
    private final PropertyFinancialTransactionRepository transactionRepository;
    private final PropertyInvoiceRepository invoiceRepository;
    private final PropertyInvoiceLineRepository invoiceLineRepository;
    private final PropertyInvoicePaymentAllocationRepository allocationRepository;
    private final PropertyAccessService propertyAccessService;
    private final FinancialAuditService auditService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public InvoiceFinalizationService(
            ReservationRepository reservationRepository,
            FolioCalculationService folioCalculationService,
            CheckoutOverrideRepository checkoutOverrideRepository,
            ReservationChargeLineRepository chargeLineRepository,
            PropertyFinancialTransactionRepository transactionRepository,
            PropertyInvoiceRepository invoiceRepository,
            PropertyInvoiceLineRepository invoiceLineRepository,
            PropertyInvoicePaymentAllocationRepository allocationRepository,
            PropertyAccessService propertyAccessService,
            FinancialAuditService auditService,
            ObjectMapper objectMapper) {
        this(reservationRepository, folioCalculationService, checkoutOverrideRepository, chargeLineRepository,
                transactionRepository, invoiceRepository, invoiceLineRepository, allocationRepository,
                propertyAccessService, auditService, objectMapper, Clock.systemUTC());
    }

    InvoiceFinalizationService(
            ReservationRepository reservationRepository,
            FolioCalculationService folioCalculationService,
            CheckoutOverrideRepository checkoutOverrideRepository,
            ReservationChargeLineRepository chargeLineRepository,
            PropertyFinancialTransactionRepository transactionRepository,
            PropertyInvoiceRepository invoiceRepository,
            PropertyInvoiceLineRepository invoiceLineRepository,
            PropertyInvoicePaymentAllocationRepository allocationRepository,
            PropertyAccessService propertyAccessService,
            FinancialAuditService auditService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.reservationRepository = reservationRepository;
        this.folioCalculationService = folioCalculationService;
        this.checkoutOverrideRepository = checkoutOverrideRepository;
        this.chargeLineRepository = chargeLineRepository;
        this.transactionRepository = transactionRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceLineRepository = invoiceLineRepository;
        this.allocationRepository = allocationRepository;
        this.propertyAccessService = propertyAccessService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public FinalizedInvoice finalizeInvoice(FinalizeInvoiceCommand command) {
        if (command == null || command.reservationId() == null) {
            throw new IllegalArgumentException("reservationId is required.");
        }
        requireInvoicePermission();
        Reservation reservation = reservationRepository.findByIdForUpdate(command.reservationId())
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        authorize(reservation);

        PropertyInvoice existing = invoiceRepository.findByReservationIdAndStatus(
                reservation.getId(), PropertyInvoice.Status.FINALIZED).orElse(null);
        if (existing != null) {
            return existingResult(existing);
        }
        if (!CHECKED_IN.equals(reservation.getStatus())) {
            throw new FinancialException(
                    FinancialErrorCode.INVALID_STATE_TRANSITION,
                    "Invoice finalization is available only for a checked-in reservation.",
                    null,
                    reservation.getStatus(),
                    null);
        }

        User finalizer = propertyAccessService.currentUser();
        FolioCalculationService.Folio folio = folioCalculationService.calculate(reservation.getId());
        validateFolioOwner(reservation, folio);
        CheckoutOverride debtOverride = requireSettlementEvidence(command, reservation, folio);

        List<ReservationChargeLine> chargeLines =
                chargeLineRepository.findByReservationIdOrderByCreatedAtAscIdAsc(reservation.getId());
        Map<Long, ReservationChargeLine> chargeById = chargeLines.stream()
                .filter(Objects::nonNull)
                .filter(line -> line.getId() != null)
                .collect(Collectors.toMap(ReservationChargeLine::getId, Function.identity()));
        List<PropertyFinancialTransaction> transactions =
                transactionRepository.findByReservationIdOrderByOccurredAtAsc(reservation.getId());

        LocalDateTime finalizedAt = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        VndMoney subtotal = VndMoney.of(folio.roomCharges().amount()
                .add(folio.serviceCharges().amount())
                .add(folio.surchargeCharges().amount()));
        VndMoney refundAndCredits = VndMoney.of(
                folio.successfulRefunds().amount().add(folio.otherCredits().amount()));
        PropertyInvoice invoice = PropertyInvoice.finalized(
                reservation.getHotel(),
                reservation,
                invoiceNumber(reservation),
                snapshotCustomer(reservation.getUser()),
                snapshotProperty(reservation.getHotel()),
                subtotal,
                folio.taxCharges(),
                folio.feeCharges(),
                folio.discounts(),
                folio.grossCharges(),
                folio.successfulPayments(),
                refundAndCredits,
                VndMoney.of(folio.balance()),
                finalizer,
                finalizedAt);
        invoice = invoiceRepository.saveAndFlush(invoice);

        List<PropertyInvoiceLine> lines = buildLines(invoice, folio, chargeById);
        lines = invoiceLineRepository.saveAllAndFlush(lines);
        List<PropertyInvoicePaymentAllocation> allocations = buildAllocations(invoice, folio, transactions);
        allocations = allocationRepository.saveAllAndFlush(allocations);
        audit(invoice, folio, lines.size(), allocations.size(), debtOverride);
        return new FinalizedInvoice(invoice, List.copyOf(lines), List.copyOf(allocations));
    }

    private FinalizedInvoice existingResult(PropertyInvoice invoice) {
        return new FinalizedInvoice(
                invoice,
                List.copyOf(invoiceLineRepository.findByInvoiceIdOrderByIdAsc(invoice.getId())),
                List.copyOf(allocationRepository.findByInvoiceIdOrderByIdAsc(invoice.getId())));
    }

    private CheckoutOverride requireSettlementEvidence(
            FinalizeInvoiceCommand command,
            Reservation reservation,
            FolioCalculationService.Folio folio) {
        BigDecimal balance = folio.balance();
        if (balance.scale() > 0) {
            throw new IllegalStateException("Invoice balance must be an exact integer VND value.");
        }
        if (balance.signum() < 0) {
            throw new FinancialException(
                    FinancialErrorCode.OVERPAYMENT_REQUIRES_RESOLUTION,
                    "Overpayment must be resolved before invoice finalization.",
                    null,
                    CheckoutPreviewService.SettlementState.OVERPAID.name(),
                    null);
        }
        if (balance.signum() == 0) {
            return null;
        }
        if (command.checkoutOverrideId() == null) {
            throw new FinancialException(
                    FinancialErrorCode.OUTSTANDING_BALANCE,
                    FinancialErrorCode.OUTSTANDING_BALANCE.defaultMessage(),
                    null,
                    CheckoutPreviewService.SettlementState.OUTSTANDING.name(),
                    null);
        }
        CheckoutOverride override = checkoutOverrideRepository.findByIdAndHotelIdAndReservationId(
                        command.checkoutOverrideId(), reservation.getHotel().getId(), reservation.getId())
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        if (override.getOverrideType() != CheckoutOverride.OverrideType.DEBT
                || override.getOutstandingAmount().compareTo(balance) != 0) {
            throw new FinancialException(
                    FinancialErrorCode.CONCURRENT_MODIFICATION,
                    "Debt override no longer matches the locked checkout balance.",
                    null,
                    CheckoutPreviewService.SettlementState.OUTSTANDING.name(),
                    null);
        }
        return override;
    }

    private List<PropertyInvoiceLine> buildLines(
            PropertyInvoice invoice,
            FolioCalculationService.Folio folio,
            Map<Long, ReservationChargeLine> chargeById) {
        List<PropertyInvoiceLine> lines = new ArrayList<>();
        BigDecimal effectTotal = BigDecimal.ZERO;
        for (FolioCalculationService.FolioLine source : folio.lines()) {
            PropertyInvoiceLine.LineType type;
            try {
                type = PropertyInvoiceLine.LineType.valueOf(source.category());
            } catch (RuntimeException exception) {
                throw new IllegalStateException("Unsupported folio line category: " + source.category(), exception);
            }
            requireDetailedSnapshot(source);
            ReservationChargeLine sourceCharge = "RESERVATION_CHARGE_LINE".equals(source.sourceType())
                    ? chargeById.get(source.sourceId())
                    : null;
            if ("RESERVATION_CHARGE_LINE".equals(source.sourceType()) && sourceCharge == null) {
                throw new IllegalStateException("Authoritative charge-line evidence is missing during finalization.");
            }
            PropertyInvoiceLine line = PropertyInvoiceLine.snapshot(
                    invoice,
                    type,
                    sourceCharge,
                    source.code(),
                    source.name(),
                    source.description(),
                    source.quantity(),
                    VndMoney.of(source.unitPrice()),
                    VndMoney.of(source.taxAmount()),
                    VndMoney.of(source.discountAmount()),
                    VndMoney.of(source.snapshotAmount()),
                    source.usageStartedAt(),
                    source.usageEndedAt());
            if (line.economicEffect().compareTo(source.signedEffect()) != 0) {
                throw new IllegalStateException("Invoice line effect does not match the authoritative folio.");
            }
            effectTotal = effectTotal.add(line.economicEffect());
            lines.add(line);
        }
        if (effectTotal.compareTo(folio.grossCharges().amount()) != 0) {
            throw new IllegalStateException("Invoice lines do not reconcile with the authoritative folio total.");
        }
        return lines;
    }

    private List<PropertyInvoicePaymentAllocation> buildAllocations(
            PropertyInvoice invoice,
            FolioCalculationService.Folio folio,
            List<PropertyFinancialTransaction> transactions) {
        List<PropertyInvoicePaymentAllocation> allocations = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;
        for (PropertyFinancialTransaction transaction : transactions) {
            if (transaction.getDirection() != PropertyFinancialTransaction.Direction.DEBIT
                    || transaction.getTransactionType() == PropertyFinancialTransaction.TransactionType.REFUND) {
                continue;
            }
            PropertyInvoicePaymentAllocation allocation = PropertyInvoicePaymentAllocation.allocate(
                    invoice,
                    transaction,
                    transaction.money());
            allocations.add(allocation);
            allocated = allocated.add(allocation.getAllocatedAmount());
        }
        if (allocated.compareTo(folio.successfulPayments().amount()) != 0) {
            throw new IllegalStateException(
                    "Successful payments require complete immutable ledger allocation before finalization.");
        }
        return allocations;
    }

    private void requireDetailedSnapshot(FolioCalculationService.FolioLine line) {
        if (line == null || line.category() == null || line.name() == null
                || line.quantity() == null || line.unitPrice() == null
                || line.taxAmount() == null || line.discountAmount() == null
                || line.snapshotAmount() == null || line.signedEffect() == null) {
            throw new IllegalStateException("Folio line is missing immutable invoice snapshot data.");
        }
    }

    private void validateFolioOwner(Reservation reservation, FolioCalculationService.Folio folio) {
        if (!reservation.getId().equals(folio.reservationId())
                || reservation.getHotel() == null
                || !reservation.getHotel().getId().equals(folio.hotelId())) {
            throw new IllegalArgumentException("Folio belongs to another property or reservation.");
        }
    }

    private void authorize(Reservation reservation) {
        Long hotelId = reservation.getHotel() == null ? null : reservation.getHotel().getId();
        if (hotelId == null || (!propertyAccessService.isSystemAdministrator()
                && !propertyAccessService.accessibleHotelIds().contains(hotelId))) {
            throw new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private void requireInvoicePermission() {
        if (propertyAccessService.isSystemAdministrator()) {
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication != null && authentication.getPrincipal() instanceof CustomUserDetails details)) {
            throw new FinancialException(FinancialErrorCode.TENANT_ACCESS_DENIED);
        }
        Integer mask = details.getPermissionMasks() == null
                ? null
                : details.getPermissionMasks().get(FunctionCode.INVOICE);
        if (mask == null || (mask & ActionCode.CREATE) != ActionCode.CREATE) {
            throw new FinancialException(FinancialErrorCode.TENANT_ACCESS_DENIED);
        }
    }

    private String snapshotCustomer(User customer) {
        if (customer == null) {
            throw new IllegalStateException("Reservation customer is required for invoice finalization.");
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("userId", customer.getId());
        snapshot.put("username", customer.getUsername());
        snapshot.put("email", customer.getEmail());
        snapshot.put("fullName", customer.getFullName());
        snapshot.put("phone", customer.getPhone());
        return json(snapshot);
    }

    private String snapshotProperty(Hotel hotel) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("hotelId", hotel.getId());
        snapshot.put("code", hotel.getCode());
        snapshot.put("name", hotel.getName());
        snapshot.put("nameVi", hotel.getNameVi());
        snapshot.put("nameEn", hotel.getNameEn());
        snapshot.put("address", hotel.getAddressLine());
        snapshot.put("city", hotel.getCity());
        snapshot.put("country", hotel.getCountry());
        snapshot.put("phone", hotel.getPhone());
        snapshot.put("email", hotel.getEmail());
        return json(snapshot);
    }

    private String json(Map<String, Object> snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to create immutable invoice identity snapshot.", exception);
        }
    }

    private String invoiceNumber(Reservation reservation) {
        return "INV-" + reservation.getHotel().getId() + "-" + reservation.getId();
    }

    private void audit(
            PropertyInvoice invoice,
            FolioCalculationService.Folio folio,
            int lineCount,
            int allocationCount,
            CheckoutOverride debtOverride) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("reservationId", invoice.getReservation().getId());
        metadata.put("invoiceNumber", invoice.getInvoiceNumber());
        metadata.put("totalAmount", invoice.getTotalAmount());
        metadata.put("paidAmount", invoice.getPaidAmount());
        metadata.put("refundAndCreditAmount", invoice.getRefundedAmount());
        metadata.put("balanceAmount", invoice.getBalanceAmount());
        metadata.put("lineCount", lineCount);
        metadata.put("allocationCount", allocationCount);
        metadata.put("folioSourceVersion", folio.sourceVersion());
        if (debtOverride != null) {
            metadata.put("checkoutOverrideId", debtOverride.getId());
        }
        auditService.append(new FinancialAuditService.AuditCommand(
                "PROPERTY_COMMERCE",
                invoice.getHotel().getId(),
                "PROPERTY_INVOICE",
                invoice.getId() == null ? invoice.getInvoiceNumber() : invoice.getId().toString(),
                "USER",
                invoice.getFinalizedBy().getId(),
                "INVOICE_FINALIZED",
                null,
                PropertyInvoice.Status.FINALIZED.name(),
                "Authoritative checkout invoice finalized.",
                null,
                null,
                null,
                metadata));
    }

    public record FinalizeInvoiceCommand(Long reservationId, Long checkoutOverrideId) {
    }

    public record FinalizedInvoice(
            PropertyInvoice invoice,
            List<PropertyInvoiceLine> lines,
            List<PropertyInvoicePaymentAllocation> allocations) {
    }
}
