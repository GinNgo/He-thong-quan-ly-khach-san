package com.hotel.propertycommerce.checkout;

import com.hotel.dtos.CheckoutRequest;
import com.hotel.dtos.CheckoutResultDTO;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.propertycommerce.booking.BookingFinancialSummaryService;
import com.hotel.propertycommerce.folio.ReservationChargeLine;
import com.hotel.propertycommerce.folio.ReservationChargeService;
import com.hotel.propertycommerce.folio.SurchargeService;
import com.hotel.security.ActionCode;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import com.hotel.services.ReservationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/management/reservations/{reservationId}")
public class PropertyCheckoutController {

    private final ReservationChargeService chargeService;
    private final SurchargeService surchargeService;
    private final CheckoutPreviewService previewService;
    private final CheckoutOverrideService overrideService;
    private final ReservationService reservationService;
    private final BookingFinancialSummaryService financialSummaryService;

    public PropertyCheckoutController(
            ReservationChargeService chargeService,
            SurchargeService surchargeService,
            CheckoutPreviewService previewService,
            CheckoutOverrideService overrideService,
            ReservationService reservationService,
            BookingFinancialSummaryService financialSummaryService) {
        this.chargeService = chargeService;
        this.surchargeService = surchargeService;
        this.previewService = previewService;
        this.overrideService = overrideService;
        this.reservationService = reservationService;
        this.financialSummaryService = financialSummaryService;
    }

    @PostMapping("/charges/services")
    @Permission(function = FunctionCode.RESERVATION_SERVICE, action = ActionCode.CREATE)
    public ResponseEntity<ChargeResponse> addService(
            @PathVariable Long reservationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestBody ServiceChargeRequest request) {
        ReservationChargeLine.ChargeType chargeType = enumValue(
                request == null ? null : request.chargeType(),
                ReservationChargeLine.ChargeType.class);
        ReservationChargeService.AddServiceChargeResult result = chargeService.addServiceCharge(
                new ReservationChargeService.AddServiceChargeCommand(
                        reservationId,
                        request == null ? null : request.serviceId(),
                        chargeType,
                        request == null ? null : request.quantity(),
                        request == null ? null : request.serviceUsedAt(),
                        idempotencyKey,
                        correlationId));
        return ResponseEntity.status(201).body(ChargeResponse.from(result, correlationId));
    }

    @PostMapping("/charges/surcharges")
    @Permission(function = FunctionCode.RESERVATION_SURCHARGE, action = ActionCode.CREATE)
    public ResponseEntity<ChargeResponse> addSurcharge(
            @PathVariable Long reservationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestBody SurchargeRequest request) {
        if (request != null && Boolean.TRUE.equals(request.negativeAdjustment())) {
            SurchargeService.NegativeAdjustmentType type = SurchargeService.parseNegativeAdjustmentType(
                    request.negativeType() == null ? request.type() : request.negativeType());
            SurchargeService.AddSurchargeResult result = surchargeService.addNegativeAdjustment(
                    new SurchargeService.AddNegativeAdjustmentCommand(
                            reservationId,
                            type,
                            request.description(),
                            request.amount(),
                            idempotencyKey,
                            request.correlationId() == null ? correlationId : request.correlationId()));
            return ResponseEntity.status(201).body(ChargeResponse.from(result, correlationId));
        }

        SurchargeService.SurchargeType type = SurchargeService.parseSurchargeType(
                request == null ? null : request.type());
        SurchargeService.AddSurchargeResult result = surchargeService.addSurcharge(
                new SurchargeService.AddSurchargeCommand(
                        reservationId,
                        type,
                        request == null ? null : request.description(),
                        request == null ? null : request.amount(),
                        idempotencyKey,
                        request == null || request.correlationId() == null
                                ? correlationId
                                : request.correlationId()));
        return ResponseEntity.status(201).body(ChargeResponse.from(result, correlationId));
    }

    @GetMapping("/charges/adjustments")
    @Permission(function = FunctionCode.RESERVATION_SURCHARGE, action = ActionCode.VIEW)
    public List<SurchargeService.AdjustmentHistoryEntry> adjustmentHistory(@PathVariable Long reservationId) {
        return surchargeService.adjustmentHistory(reservationId);
    }

    @PostMapping("/checkout-preview")
    @Permission(function = FunctionCode.CHECKOUT, action = ActionCode.VIEW)
    public CheckoutPreviewResponse preview(
            @PathVariable Long reservationId,
            @RequestBody(required = false) Map<String, Object> ignored) {
        return CheckoutPreviewResponse.from(previewService.preview(reservationId));
    }

    @PostMapping("/checkout-override")
    @Permission(function = FunctionCode.RESERVATION_DEBT_OVERRIDE, action = ActionCode.APPROVE)
    public CheckoutOverrideResponse authorizeOverride(
            @PathVariable Long reservationId,
            @RequestBody DebtOverrideRequest request) {
        CheckoutOverrideService.SettlementAuthorization authorization = overrideService.authorizeCheckout(
                reservationId,
                new CheckoutOverrideService.DebtOverrideCommand(
                        request == null ? null : request.reason(),
                        request == null ? null : request.correlationId()));
        return CheckoutOverrideResponse.from(authorization);
    }

    @PostMapping("/checkout")
    @Permission(function = FunctionCode.CHECKOUT, action = ActionCode.CREATE)
    public CheckoutResponse checkout(
            @PathVariable Long reservationId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestBody(required = false) CheckoutCommand request) {
        if (request != null && (request.paymentAmount() != null
                || hasText(request.paymentMethod())
                || hasText(request.transactionId()))) {
            throw new FinancialException(
                    com.hotel.paymentprovider.error.FinancialErrorCode.INVALID_AMOUNT,
                    "Checkout accepts only a server-issued debt override; totals and payment references are not client authoritative.");
        }

        Long checkoutOverrideId = request == null ? null : request.checkoutOverrideId();
        if (checkoutOverrideId == null && request != null && hasText(request.debtOverrideReason())) {
            CheckoutOverrideService.SettlementAuthorization authorization = overrideService.authorizeCheckout(
                    reservationId,
                    new CheckoutOverrideService.DebtOverrideCommand(
                            request.debtOverrideReason(),
                            request.correlationId() == null ? correlationId : request.correlationId()));
            checkoutOverrideId = authorization.override() == null ? null : authorization.override().getId();
        }

        CheckoutRequest serviceRequest = new CheckoutRequest();
        serviceRequest.setCheckoutOverrideId(checkoutOverrideId);
        CheckoutResultDTO result = reservationService.checkout(reservationId, serviceRequest);
        return CheckoutResponse.from(result, financialSummaryService.calculate(reservationId));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private <T extends Enum<T>> T enumValue(String value, Class<T> type) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported " + type.getSimpleName() + ": " + value + ".");
        }
    }

    public record ServiceChargeRequest(
            Long serviceId,
            String chargeType,
            BigDecimal quantity,
            LocalDateTime serviceUsedAt) {
    }

    public record SurchargeRequest(
            String type,
            String description,
            BigDecimal amount,
            String correlationId,
            Boolean negativeAdjustment,
            String negativeType) {
    }

    public record DebtOverrideRequest(String reason, String correlationId) {
    }

    public record CheckoutCommand(
            Long checkoutOverrideId,
            String debtOverrideReason,
            String paymentMethod,
            BigDecimal paymentAmount,
            String transactionId,
            String correlationId) {
    }

    public record ChargeResponse(
            Long id,
            Long reservationId,
            String chargeType,
            String code,
            String name,
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal taxAmount,
            BigDecimal discountAmount,
            BigDecimal totalAmount,
            LocalDateTime serviceUsedAt,
            String correlationId,
            boolean replayed) {

        static ChargeResponse from(ReservationChargeService.AddServiceChargeResult result, String correlationId) {
            ReservationChargeLine line = result.line();
            return new ChargeResponse(
                    line.getId(),
                    line.getReservation().getId(),
                    line.getChargeType().name(),
                    line.getCode(),
                    line.getName(),
                    line.getDescription(),
                    line.getQuantity(),
                    line.getUnitPrice(),
                    line.getTaxAmount(),
                    line.getDiscountAmount(),
                    line.getTotalAmount(),
                    line.getServiceUsedAt(),
                    correlationId,
                    result.replayed());
        }

        static ChargeResponse from(SurchargeService.AddSurchargeResult result, String correlationId) {
            ReservationChargeLine line = result.line();
            return new ChargeResponse(
                    line.getId(),
                    line.getReservation().getId(),
                    line.getChargeType().name(),
                    line.getCode(),
                    line.getName(),
                    line.getDescription(),
                    line.getQuantity(),
                    line.getUnitPrice(),
                    line.getTaxAmount(),
                    line.getDiscountAmount(),
                    line.getTotalAmount(),
                    line.getServiceUsedAt(),
                    correlationId,
                    result.replayed());
        }

        static ChargeResponse from(ReservationChargeLine line, String correlationId) {
            return from(new ReservationChargeService.AddServiceChargeResult(line, false), correlationId);
        }
    }

    public record CheckoutPreviewResponse(
            Long reservationId,
            Long hotelId,
            String settlementState,
            boolean checkoutAllowed,
            String blockingError,
            long sourceVersion,
            LocalDateTime calculatedAt,
            FolioResponse folio) {

        static CheckoutPreviewResponse from(CheckoutPreviewService.CheckoutPreview preview) {
            return new CheckoutPreviewResponse(
                    preview.folio().reservationId(),
                    preview.folio().hotelId(),
                    preview.settlementState().name(),
                    preview.checkoutAllowed(),
                    preview.blockingError() == null ? null : preview.blockingError().name(),
                    preview.sourceVersion(),
                    preview.folio().calculatedAt(),
                    FolioResponse.from(preview.folio()));
        }
    }

    public record FolioResponse(
            BigDecimal roomCharges,
            BigDecimal serviceCharges,
            BigDecimal surchargeCharges,
            BigDecimal taxCharges,
            BigDecimal feeCharges,
            BigDecimal discounts,
            BigDecimal grossCharges,
            BigDecimal depositRequired,
            BigDecimal successfulPayments,
            BigDecimal successfulRefunds,
            BigDecimal otherCredits,
            BigDecimal netSettled,
            BigDecimal balance,
            List<FolioCalculationService.FolioLine> lines,
            long sourceVersion,
            LocalDateTime calculatedAt) {

        static FolioResponse from(FolioCalculationService.Folio folio) {
            return new FolioResponse(
                    folio.roomCharges().amount(),
                    folio.serviceCharges().amount(),
                    folio.surchargeCharges().amount(),
                    folio.taxCharges().amount(),
                    folio.feeCharges().amount(),
                    folio.discounts().amount(),
                    folio.grossCharges().amount(),
                    folio.depositRequired().amount(),
                    folio.successfulPayments().amount(),
                    folio.successfulRefunds().amount(),
                    folio.otherCredits().amount(),
                    folio.netSettled().amount(),
                    folio.balance(),
                    folio.lines(),
                    folio.sourceVersion(),
                    folio.calculatedAt());
        }
    }

    public record CheckoutOverrideResponse(
            Long overrideId,
            boolean debtOverrideApplied,
            CheckoutPreviewResponse preview) {

        static CheckoutOverrideResponse from(CheckoutOverrideService.SettlementAuthorization authorization) {
            return new CheckoutOverrideResponse(
                    authorization.override() == null ? null : authorization.override().getId(),
                    authorization.debtOverrideApplied(),
                    CheckoutPreviewResponse.from(authorization.preview()));
        }
    }

    public record CheckoutResponse(
            Long reservationId,
            String reservationStatus,
            Long invoiceId,
            String invoiceNumber,
            String invoiceStatus,
            BigDecimal totalAmount,
            List<Long> dirtyRoomIds,
            FinancialSummaryResponse financialSummary) {

        static CheckoutResponse from(
                CheckoutResultDTO result,
                BookingFinancialSummaryService.Summary summary) {
            return new CheckoutResponse(
                    result.getReservationId(),
                    result.getReservationStatus(),
                    result.getInvoiceId(),
                    result.getInvoiceCode(),
                    result.getInvoiceStatus(),
                    result.getTotalAmount(),
                    result.getDirtyRoomIds(),
                    FinancialSummaryResponse.from(summary));
        }
    }

    public record FinancialSummaryResponse(
            BigDecimal grossCharges,
            BigDecimal depositRequired,
            BigDecimal successfulPayments,
            BigDecimal successfulRefunds,
            BigDecimal remainingBalance,
            String financialState,
            long sourceVersion,
            LocalDateTime calculatedAt) {

        static FinancialSummaryResponse from(BookingFinancialSummaryService.Summary summary) {
            return new FinancialSummaryResponse(
                    summary.grossCharges().amount(),
                    summary.depositRequired().amount(),
                    summary.successfulPayments().amount(),
                    summary.successfulRefunds().amount(),
                    summary.remainingBalance(),
                    summary.financialState().name(),
                    summary.sourceVersion(),
                    summary.calculatedAt());
        }
    }
}
