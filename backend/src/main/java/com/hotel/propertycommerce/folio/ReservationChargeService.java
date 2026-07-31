package com.hotel.propertycommerce.folio;

import com.hotel.entities.HotelService;
import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.repositories.HotelServiceRepository;
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
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class ReservationChargeService {

    private static final String ACTIVE = "ACTIVE";
    private static final String CHECKED_IN = "CHECKED_IN";

    private final ReservationRepository reservationRepository;
    private final HotelServiceRepository hotelServiceRepository;
    private final ReservationChargeLineRepository chargeLineRepository;
    private final PropertyAccessService propertyAccessService;
    private final Clock clock;

    @Autowired
    public ReservationChargeService(
            ReservationRepository reservationRepository,
            HotelServiceRepository hotelServiceRepository,
            ReservationChargeLineRepository chargeLineRepository,
            PropertyAccessService propertyAccessService) {
        this(reservationRepository, hotelServiceRepository, chargeLineRepository,
                propertyAccessService, Clock.systemUTC());
    }

    ReservationChargeService(
            ReservationRepository reservationRepository,
            HotelServiceRepository hotelServiceRepository,
            ReservationChargeLineRepository chargeLineRepository,
            PropertyAccessService propertyAccessService,
            Clock clock) {
        this.reservationRepository = reservationRepository;
        this.hotelServiceRepository = hotelServiceRepository;
        this.chargeLineRepository = chargeLineRepository;
        this.propertyAccessService = propertyAccessService;
        this.clock = clock;
    }

    @Transactional
    public ReservationChargeLine addServiceCharge(AddServiceChargeCommand command) {
        validate(command);
        requireCreatePermission();
        User actor = propertyAccessService.currentUser();
        Reservation reservation = lockReservation(command.reservationId());
        authorize(reservation);
        requireCheckedIn(reservation);

        HotelService catalogService = requireAvailableCatalogService(command.serviceId(), reservation);
        ReservationChargeLine line = createCatalogLine(
                reservation,
                catalogService,
                command.chargeType(),
                command.quantity(),
                command.serviceUsedAt(),
                actor);
        return chargeLineRepository.saveAndFlush(line);
    }

    @Transactional
    public CorrectionResult correctServiceCharge(CorrectServiceChargeCommand command) {
        validate(command);
        requireCreatePermission();
        User actor = propertyAccessService.currentUser();
        Reservation reservation = lockReservation(command.reservationId());
        authorize(reservation);
        requireCheckedIn(reservation);

        ReservationChargeLine original = chargeLineRepository.findByIdForUpdate(
                        command.chargeLineId(), reservation.getHotel().getId(), reservation.getId())
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        if (original.getChargeType() != ReservationChargeLine.ChargeType.SERVICE
                && original.getChargeType() != ReservationChargeLine.ChargeType.MINIBAR) {
            throw invalidState("Only service or minibar lines can be corrected by this workflow.", original.getChargeType().name());
        }
        if (original.getReversesLine() != null || chargeLineRepository.existsByReversesLineId(original.getId())) {
            throw invalidState("The charge line has already been corrected.", original.getChargeType().name());
        }

        ReservationChargeLine reversal = ReservationChargeLine.create(
                reservation.getHotel(),
                reservation,
                ReservationChargeLine.ChargeType.ADJUSTMENT,
                original.getSourceId(),
                original.getSourceVersion(),
                correctionCode(original.getCode()),
                bounded("Correction - " + original.getName(), 255),
                bounded(command.reason().trim(), 1000),
                original.getUnitPrice(),
                original.getQuantity(),
                original.getTaxAmount(),
                original.getDiscountAmount(),
                original.getTotalAmount(),
                null,
                actor,
                original);
        reversal = chargeLineRepository.saveAndFlush(reversal);

        ReservationChargeLine replacement = null;
        if (command.replacementQuantity() != null) {
            HotelService catalogService = requireAvailableCatalogService(original.getSourceId(), reservation);
            replacement = createCatalogLine(
                    reservation,
                    catalogService,
                    original.getChargeType(),
                    command.replacementQuantity(),
                    command.replacementUsedAt(),
                    actor);
            replacement = chargeLineRepository.saveAndFlush(replacement);
        }
        return new CorrectionResult(reversal, replacement);
    }

    private ReservationChargeLine createCatalogLine(
            Reservation reservation,
            HotelService catalogService,
            ReservationChargeLine.ChargeType chargeType,
            BigDecimal requestedQuantity,
            LocalDateTime usedAt,
            User actor) {
        BigDecimal quantity = requireQuantity(requestedQuantity);
        requireUsageTime(usedAt);
        VndMoney unitPrice = requireVnd(catalogService.getPrice(), "Catalog service price");
        VndMoney total = requireVnd(unitPrice.amount().multiply(quantity), "Calculated service total");
        return ReservationChargeLine.create(
                reservation.getHotel(),
                reservation,
                chargeType,
                catalogService.getId(),
                catalogVersion(catalogService),
                bounded(catalogService.getCode(), 80),
                bilingual(catalogService.getNameVi(), catalogService.getNameEn(), 255),
                bilingual(catalogService.getDescriptionVi(), catalogService.getDescriptionEn(), 1000),
                unitPrice.amount(),
                quantity,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                total.amount(),
                usedAt,
                actor,
                null);
    }

    private Reservation lockReservation(Long reservationId) {
        return reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
    }

    private void authorize(Reservation reservation) {
        Long hotelId = reservation.getHotel() == null ? null : reservation.getHotel().getId();
        if (hotelId == null || (!propertyAccessService.isSystemAdministrator()
                && !propertyAccessService.accessibleHotelIds().contains(hotelId))) {
            throw new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private HotelService requireAvailableCatalogService(Long serviceId, Reservation reservation) {
        if (serviceId == null) {
            throw new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND);
        }
        HotelService service = hotelServiceRepository.findById(serviceId)
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        boolean systemService = Boolean.TRUE.equals(service.getSystemService());
        boolean sameHotel = service.getHotel() != null
                && service.getHotel().getId() != null
                && service.getHotel().getId().equals(reservation.getHotel().getId());
        if (!systemService && !sameHotel) {
            throw new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!ACTIVE.equals(service.getStatus())) {
            throw invalidState("The selected catalog service is inactive.", service.getStatus());
        }
        return service;
    }

    private void requireCheckedIn(Reservation reservation) {
        if (!CHECKED_IN.equals(reservation.getStatus())) {
            throw invalidState("Service charges can only be added to a checked-in reservation.", reservation.getStatus());
        }
    }

    private void requireCreatePermission() {
        if (propertyAccessService.isSystemAdministrator()) {
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new FinancialException(FinancialErrorCode.TENANT_ACCESS_DENIED);
        }
        Integer mask = userDetails.getPermissionMasks() == null
                ? null
                : userDetails.getPermissionMasks().get(FunctionCode.RESERVATION_SERVICE);
        if (mask == null || (mask & ActionCode.CREATE) != ActionCode.CREATE) {
            throw new FinancialException(FinancialErrorCode.TENANT_ACCESS_DENIED);
        }
    }

    private void validate(AddServiceChargeCommand command) {
        if (command == null || command.reservationId() == null || command.serviceId() == null
                || command.chargeType() == null || command.quantity() == null || command.serviceUsedAt() == null) {
            throw new IllegalArgumentException("Reservation, catalog service, charge type, quantity and usage time are required.");
        }
        if (command.chargeType() != ReservationChargeLine.ChargeType.SERVICE
                && command.chargeType() != ReservationChargeLine.ChargeType.MINIBAR) {
            throw new IllegalArgumentException("Charge type must be SERVICE or MINIBAR.");
        }
        requireQuantity(command.quantity());
        requireUsageTime(command.serviceUsedAt());
    }

    private void validate(CorrectServiceChargeCommand command) {
        if (command == null || command.reservationId() == null || command.chargeLineId() == null
                || command.reason() == null || command.reason().isBlank()) {
            throw new IllegalArgumentException("Reservation, charge line and correction reason are required.");
        }
        if (command.reason().trim().length() > 1000) {
            throw new IllegalArgumentException("Correction reason is too long.");
        }
        if (command.replacementQuantity() == null && command.replacementUsedAt() != null) {
            throw new IllegalArgumentException("Replacement usage time requires a replacement quantity.");
        }
        if (command.replacementQuantity() != null) {
            requireQuantity(command.replacementQuantity());
            requireUsageTime(command.replacementUsedAt());
        }
    }

    private BigDecimal requireQuantity(BigDecimal value) {
        try {
            BigDecimal normalized = value.setScale(3, RoundingMode.UNNECESSARY);
            if (normalized.signum() <= 0 || normalized.precision() > 19) {
                throw new ArithmeticException("quantity outside supported range");
            }
            return normalized;
        } catch (ArithmeticException exception) {
            throw new FinancialException(FinancialErrorCode.INVALID_AMOUNT,
                    "Quantity must be positive and use at most three decimal places.");
        }
    }

    private VndMoney requireVnd(BigDecimal amount, String field) {
        try {
            VndMoney money = VndMoney.of(amount);
            if (money.amount().precision() > 19) {
                throw new ArithmeticException("amount outside supported range");
            }
            return money;
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw new FinancialException(FinancialErrorCode.INVALID_AMOUNT,
                    field + " must be a non-negative integer VND amount.");
        }
    }

    private void requireUsageTime(LocalDateTime usedAt) {
        if (usedAt == null) {
            throw new IllegalArgumentException("Service usage time is required.");
        }
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (usedAt.isAfter(now)) {
            throw new IllegalArgumentException("Service usage time cannot be in the future.");
        }
    }

    private String catalogVersion(HotelService service) {
        LocalDateTime versionTime = service.getUpdatedAt() != null ? service.getUpdatedAt() : service.getCreatedAt();
        return versionTime == null ? "CATALOG-CURRENT" : versionTime.toString();
    }

    private String bilingual(String vi, String en, int maxLength) {
        String first = normalize(vi);
        String second = normalize(en);
        String value;
        if (first == null) value = second;
        else if (second == null || first.equals(second)) value = first;
        else value = first + " / " + second;
        return bounded(value, maxLength);
    }

    private String correctionCode(String originalCode) {
        String code = normalize(originalCode);
        return bounded(code == null ? "CORRECTION" : "REV-" + code, 80);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String bounded(String value, int maxLength) {
        String normalized = normalize(value);
        if (normalized == null || normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }

    private FinancialException invalidState(String message, String currentState) {
        return new FinancialException(
                FinancialErrorCode.INVALID_STATE_TRANSITION,
                message,
                null,
                currentState,
                null);
    }

    public record AddServiceChargeCommand(
            Long reservationId,
            Long serviceId,
            ReservationChargeLine.ChargeType chargeType,
            BigDecimal quantity,
            LocalDateTime serviceUsedAt) {
    }

    public record CorrectServiceChargeCommand(
            Long reservationId,
            Long chargeLineId,
            String reason,
            BigDecimal replacementQuantity,
            LocalDateTime replacementUsedAt) {
    }

    public record CorrectionResult(
            ReservationChargeLine reversal,
            ReservationChargeLine replacement) {
    }
}
