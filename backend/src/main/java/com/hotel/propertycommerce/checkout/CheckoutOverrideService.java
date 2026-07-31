package com.hotel.propertycommerce.checkout;

import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.repositories.ReservationRepository;
import com.hotel.security.ActionCode;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.FunctionCode;
import com.hotel.services.PropertyAccessService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class CheckoutOverrideService {

    private static final String CHECKED_IN = "CHECKED_IN";

    private final CheckoutPreviewService previewService;
    private final ReservationRepository reservationRepository;
    private final CheckoutOverrideRepository overrideRepository;
    private final PropertyAccessService propertyAccessService;
    private final FinancialAuditService auditService;

    public CheckoutOverrideService(
            CheckoutPreviewService previewService,
            ReservationRepository reservationRepository,
            CheckoutOverrideRepository overrideRepository,
            PropertyAccessService propertyAccessService,
            FinancialAuditService auditService) {
        this.previewService = previewService;
        this.reservationRepository = reservationRepository;
        this.overrideRepository = overrideRepository;
        this.propertyAccessService = propertyAccessService;
        this.auditService = auditService;
    }

    @Transactional
    public SettlementAuthorization authorizeCheckout(Long reservationId, DebtOverrideCommand command) {
        CheckoutPreviewService.CheckoutPreview initialPreview = previewService.preview(reservationId);
        return switch (initialPreview.settlementState()) {
            case SETTLED -> SettlementAuthorization.settled(initialPreview);
            case OVERPAID -> throw overpaymentBlocked();
            case OUTSTANDING -> authorizeDebt(initialPreview, command);
        };
    }

    private SettlementAuthorization authorizeDebt(
            CheckoutPreviewService.CheckoutPreview initialPreview,
            DebtOverrideCommand command) {
        if (command == null) {
            throw outstandingBlocked();
        }
        String reason = requireReason(command.reason());
        requireDebtOverridePermission();
        User actor = propertyAccessService.currentUser();

        Reservation reservation = reservationRepository.findByIdForUpdate(initialPreview.folio().reservationId())
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        authorizeLockedReservation(reservation);

        CheckoutPreviewService.CheckoutPreview lockedPreview = previewService.preview(reservation.getId());
        if (lockedPreview.settlementState() == CheckoutPreviewService.SettlementState.SETTLED) {
            return SettlementAuthorization.settled(lockedPreview);
        }
        if (lockedPreview.settlementState() == CheckoutPreviewService.SettlementState.OVERPAID) {
            throw overpaymentBlocked();
        }

        CheckoutOverride override = CheckoutOverride.approveDebt(
                reservation.getHotel(),
                reservation,
                lockedPreview.folio().balance(),
                reason,
                actor);
        override = overrideRepository.saveAndFlush(override);
        audit(override, lockedPreview, command.correlationId());
        return new SettlementAuthorization(lockedPreview, true, override);
    }

    private void authorizeLockedReservation(Reservation reservation) {
        Long hotelId = reservation.getHotel() == null ? null : reservation.getHotel().getId();
        if (hotelId == null || (!propertyAccessService.isSystemAdministrator()
                && !propertyAccessService.accessibleHotelIds().contains(hotelId))) {
            throw new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!CHECKED_IN.equals(reservation.getStatus())) {
            throw new FinancialException(
                    FinancialErrorCode.INVALID_STATE_TRANSITION,
                    "Debt override is available only for a checked-in reservation.",
                    null,
                    reservation.getStatus(),
                    null);
        }
    }

    private void requireDebtOverridePermission() {
        if (propertyAccessService.isSystemAdministrator()) {
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication != null && authentication.getPrincipal() instanceof CustomUserDetails details)) {
            throw new FinancialException(FinancialErrorCode.TENANT_ACCESS_DENIED);
        }
        Integer mask = details.getPermissionMasks() == null
                ? null
                : details.getPermissionMasks().get(FunctionCode.RESERVATION_DEBT_OVERRIDE);
        if (mask == null || (mask & ActionCode.APPROVE) != ActionCode.APPROVE) {
            throw new FinancialException(FinancialErrorCode.TENANT_ACCESS_DENIED);
        }
    }

    private void audit(
            CheckoutOverride override,
            CheckoutPreviewService.CheckoutPreview preview,
            String correlationId) {
        auditService.append(new FinancialAuditService.AuditCommand(
                "PROPERTY_COMMERCE",
                override.getHotel().getId(),
                "CHECKOUT_OVERRIDE",
                override.getId() == null
                        ? "RESERVATION:" + override.getReservation().getId()
                        : override.getId().toString(),
                "USER",
                override.getActor().getId(),
                "DEBT_OVERRIDE_APPROVED",
                CheckoutPreviewService.SettlementState.OUTSTANDING.name(),
                "AUTHORIZED_WITH_DEBT",
                override.getReason(),
                null,
                null,
                correlationId,
                Map.of(
                        "reservationId", override.getReservation().getId(),
                        "overrideType", override.getOverrideType().name(),
                        "outstandingAmount", override.getOutstandingAmount(),
                        "folioSourceVersion", preview.sourceVersion())));
    }

    private String requireReason(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Debt override reason is required.");
        }
        String normalized = value.trim();
        if (normalized.length() > 1000) {
            throw new IllegalArgumentException("Debt override reason is too long.");
        }
        return normalized;
    }

    private FinancialException outstandingBlocked() {
        return new FinancialException(
                FinancialErrorCode.OUTSTANDING_BALANCE,
                FinancialErrorCode.OUTSTANDING_BALANCE.defaultMessage(),
                null,
                CheckoutPreviewService.SettlementState.OUTSTANDING.name(),
                null);
    }

    private FinancialException overpaymentBlocked() {
        return new FinancialException(
                FinancialErrorCode.OVERPAYMENT_REQUIRES_RESOLUTION,
                "Overpayment checkout remains blocked until an approved resolution policy is configured.",
                null,
                CheckoutPreviewService.SettlementState.OVERPAID.name(),
                null);
    }

    public record DebtOverrideCommand(String reason, String correlationId) {
    }

    public record SettlementAuthorization(
            CheckoutPreviewService.CheckoutPreview preview,
            boolean debtOverrideApplied,
            CheckoutOverride override) {

        static SettlementAuthorization settled(CheckoutPreviewService.CheckoutPreview preview) {
            return new SettlementAuthorization(preview, false, null);
        }
    }
}
