package com.hotel.propertycommerce.checkout;

import com.hotel.entities.Reservation;
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

import java.math.BigDecimal;

@Service
public class CheckoutPreviewService {

    private static final String CHECKED_IN = "CHECKED_IN";

    private final ReservationRepository reservationRepository;
    private final FolioCalculationService folioCalculationService;
    private final PropertyAccessService propertyAccessService;

    public CheckoutPreviewService(
            ReservationRepository reservationRepository,
            FolioCalculationService folioCalculationService,
            PropertyAccessService propertyAccessService) {
        this.reservationRepository = reservationRepository;
        this.folioCalculationService = folioCalculationService;
        this.propertyAccessService = propertyAccessService;
    }

    @Transactional(readOnly = true)
    public CheckoutPreview preview(Long reservationId) {
        if (reservationId == null) {
            throw new IllegalArgumentException("reservationId is required.");
        }
        requireCheckoutPermission();
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        authorize(reservation);
        if (!CHECKED_IN.equals(reservation.getStatus())) {
            throw new FinancialException(
                    FinancialErrorCode.INVALID_STATE_TRANSITION,
                    "Checkout preview is available only for a checked-in reservation.",
                    null,
                    reservation.getStatus(),
                    null);
        }

        FolioCalculationService.Folio folio = folioCalculationService.calculate(reservationId);
        SettlementState state = state(folio.balance());
        FinancialErrorCode blockingError = switch (state) {
            case SETTLED -> null;
            case OUTSTANDING -> FinancialErrorCode.OUTSTANDING_BALANCE;
            case OVERPAID -> FinancialErrorCode.OVERPAYMENT_REQUIRES_RESOLUTION;
        };
        return new CheckoutPreview(
                folio,
                state,
                state == SettlementState.SETTLED,
                blockingError,
                folio.sourceVersion());
    }

    @Transactional(readOnly = true)
    public FolioCalculationService.Folio requireSettled(Long reservationId) {
        CheckoutPreview preview = preview(reservationId);
        if (preview.blockingError() != null) {
            throw new FinancialException(
                    preview.blockingError(),
                    preview.blockingError().defaultMessage(),
                    null,
                    preview.settlementState().name(),
                    null);
        }
        return preview.folio();
    }

    private SettlementState state(BigDecimal balance) {
        if (balance == null || balance.scale() > 0) {
            throw new IllegalStateException("Checkout balance must be an exact integer VND value.");
        }
        return balance.signum() > 0
                ? SettlementState.OUTSTANDING
                : balance.signum() < 0 ? SettlementState.OVERPAID : SettlementState.SETTLED;
    }

    private void authorize(Reservation reservation) {
        Long hotelId = reservation.getHotel() == null ? null : reservation.getHotel().getId();
        if (hotelId == null || (!propertyAccessService.isSystemAdministrator()
                && !propertyAccessService.accessibleHotelIds().contains(hotelId))) {
            throw new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private void requireCheckoutPermission() {
        if (propertyAccessService.isSystemAdministrator()) {
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication != null && authentication.getPrincipal() instanceof CustomUserDetails details)) {
            throw new FinancialException(FinancialErrorCode.TENANT_ACCESS_DENIED);
        }
        Integer mask = details.getPermissionMasks() == null
                ? null
                : details.getPermissionMasks().get(FunctionCode.CHECKOUT);
        if (mask == null || (mask & ActionCode.VIEW) != ActionCode.VIEW) {
            throw new FinancialException(FinancialErrorCode.TENANT_ACCESS_DENIED);
        }
    }

    public enum SettlementState {
        SETTLED,
        OUTSTANDING,
        OVERPAID
    }

    public record CheckoutPreview(
            FolioCalculationService.Folio folio,
            SettlementState settlementState,
            boolean checkoutAllowed,
            FinancialErrorCode blockingError,
            long sourceVersion) {
    }
}
