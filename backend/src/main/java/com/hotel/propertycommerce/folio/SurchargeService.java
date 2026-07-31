package com.hotel.propertycommerce.folio;

import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.paymentprovider.domain.VndMoney;
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
import java.util.Map;

@Service
public class SurchargeService {

    private static final String CHECKED_IN = "CHECKED_IN";

    private final ReservationRepository reservationRepository;
    private final ReservationChargeLineRepository chargeLineRepository;
    private final PropertyAccessService propertyAccessService;
    private final FinancialAuditService auditService;

    public SurchargeService(
            ReservationRepository reservationRepository,
            ReservationChargeLineRepository chargeLineRepository,
            PropertyAccessService propertyAccessService,
            FinancialAuditService auditService) {
        this.reservationRepository = reservationRepository;
        this.chargeLineRepository = chargeLineRepository;
        this.propertyAccessService = propertyAccessService;
        this.auditService = auditService;
    }

    @Transactional
    public ReservationChargeLine addSurcharge(AddSurchargeCommand command) {
        validate(command);
        requirePermissions(false);
        User actor = propertyAccessService.currentUser();
        Reservation reservation = lockAuthorizedCheckedInReservation(command.reservationId());
        VndMoney amount = requirePositiveVnd(command.amount());

        ReservationChargeLine line = ReservationChargeLine.create(
                reservation.getHotel(),
                reservation,
                ReservationChargeLine.ChargeType.SURCHARGE,
                null,
                "SURCHARGE-V1",
                bounded("SURCHARGE:" + command.type().name(), 80),
                bounded("Surcharge - " + display(command.type().name()), 255),
                command.description().trim(),
                amount.amount(),
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                amount.amount(),
                null,
                actor,
                null);
        line = chargeLineRepository.saveAndFlush(line);
        audit(line, actor, "SURCHARGE_CREATED", command.type().name(), command.description(),
                amount, command.correlationId());
        return line;
    }

    @Transactional
    public ReservationChargeLine addNegativeAdjustment(AddNegativeAdjustmentCommand command) {
        validate(command);
        requirePermissions(true);
        User actor = propertyAccessService.currentUser();
        Reservation reservation = lockAuthorizedCheckedInReservation(command.reservationId());
        VndMoney amount = requirePositiveVnd(command.amount());

        ReservationChargeLine line = ReservationChargeLine.create(
                reservation.getHotel(),
                reservation,
                ReservationChargeLine.ChargeType.DISCOUNT,
                null,
                "NEGATIVE-ADJUSTMENT-V1",
                bounded("ADJUSTMENT:" + command.type().name(), 80),
                bounded("Adjustment - " + display(command.type().name()), 255),
                command.description().trim(),
                BigDecimal.ZERO,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                amount.amount(),
                amount.amount(),
                null,
                actor,
                null);
        line = chargeLineRepository.saveAndFlush(line);
        audit(line, actor, "NEGATIVE_ADJUSTMENT_CREATED", command.type().name(), command.description(),
                amount, command.correlationId());
        return line;
    }

    private Reservation lockAuthorizedCheckedInReservation(Long reservationId) {
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        Long hotelId = reservation.getHotel() == null ? null : reservation.getHotel().getId();
        if (hotelId == null || (!propertyAccessService.isSystemAdministrator()
                && !propertyAccessService.accessibleHotelIds().contains(hotelId))) {
            throw new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!CHECKED_IN.equals(reservation.getStatus())) {
            throw new FinancialException(
                    FinancialErrorCode.INVALID_STATE_TRANSITION,
                    "Surcharges and adjustments can only be added to a checked-in reservation.",
                    null,
                    reservation.getStatus(),
                    null);
        }
        return reservation;
    }

    private void requirePermissions(boolean negativeAdjustment) {
        if (propertyAccessService.isSystemAdministrator()) {
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication != null && authentication.getPrincipal() instanceof CustomUserDetails details)) {
            throw new FinancialException(FinancialErrorCode.TENANT_ACCESS_DENIED);
        }
        requireMask(details, FunctionCode.RESERVATION_SURCHARGE, ActionCode.CREATE);
        if (negativeAdjustment) {
            requireMask(details, FunctionCode.INVOICE_ADJUST, ActionCode.APPROVE);
        }
    }

    private void requireMask(CustomUserDetails details, FunctionCode function, int action) {
        Integer mask = details.getPermissionMasks() == null ? null : details.getPermissionMasks().get(function);
        if (mask == null || (mask & action) != action) {
            throw new FinancialException(FinancialErrorCode.TENANT_ACCESS_DENIED);
        }
    }

    private void audit(
            ReservationChargeLine line,
            User actor,
            String source,
            String type,
            String reason,
            VndMoney amount,
            String correlationId) {
        auditService.append(new FinancialAuditService.AuditCommand(
                "PROPERTY_COMMERCE",
                line.getHotel().getId(),
                "RESERVATION_CHARGE_LINE",
                line.getId() == null ? "RESERVATION:" + line.getReservation().getId() : line.getId().toString(),
                "USER",
                actor.getId(),
                source,
                null,
                "CREATED",
                reason.trim(),
                null,
                null,
                correlationId,
                Map.of(
                        "reservationId", line.getReservation().getId(),
                        "chargeType", line.getChargeType().name(),
                        "typedReason", type,
                        "amount", amount.amount())));
    }

    private VndMoney requirePositiveVnd(BigDecimal value) {
        try {
            VndMoney amount = VndMoney.of(value);
            if (amount.amount().signum() <= 0 || amount.amount().precision() > 19) {
                throw new ArithmeticException("amount outside supported range");
            }
            return amount;
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw new FinancialException(FinancialErrorCode.INVALID_AMOUNT,
                    "Surcharge or adjustment amount must be a positive integer VND value.");
        }
    }

    private void validate(AddSurchargeCommand command) {
        if (command == null || command.reservationId() == null || command.type() == null
                || command.description() == null || command.description().isBlank() || command.amount() == null) {
            throw new IllegalArgumentException("Reservation, surcharge type, description and amount are required.");
        }
        requireDescriptionLength(command.description());
    }

    private void validate(AddNegativeAdjustmentCommand command) {
        if (command == null || command.reservationId() == null || command.type() == null
                || command.description() == null || command.description().isBlank() || command.amount() == null) {
            throw new IllegalArgumentException("Reservation, adjustment type, description and amount are required.");
        }
        requireDescriptionLength(command.description());
    }

    private void requireDescriptionLength(String description) {
        if (description.trim().length() > 1000) {
            throw new IllegalArgumentException("Surcharge or adjustment description is too long.");
        }
    }

    private String display(String enumValue) {
        String value = enumValue.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private String bounded(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public enum SurchargeType {
        EARLY_CHECK_IN,
        LATE_CHECK_OUT,
        EXTRA_GUEST,
        DAMAGE,
        CLEANING,
        LOST_KEY,
        OTHER
    }

    public enum NegativeAdjustmentType {
        SERVICE_RECOVERY,
        GOODWILL,
        PRICE_CORRECTION,
        MANUAL_DISCOUNT,
        OTHER
    }

    public record AddSurchargeCommand(
            Long reservationId,
            SurchargeType type,
            String description,
            BigDecimal amount,
            String correlationId) {
    }

    public record AddNegativeAdjustmentCommand(
            Long reservationId,
            NegativeAdjustmentType type,
            String description,
            BigDecimal amount,
            String correlationId) {
    }
}
