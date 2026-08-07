package com.hotel.propertycommerce.folio;

import com.hotel.entities.Reservation;
import com.hotel.entities.User;
import com.hotel.paymentprovider.audit.FinancialAuditService;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.paymentprovider.idempotency.FinancialIdempotencyService;
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
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SurchargeService {

    private static final String CHECKED_IN = "CHECKED_IN";

    private final ReservationRepository reservationRepository;
    private final ReservationChargeLineRepository chargeLineRepository;
    private final PropertyAccessService propertyAccessService;
    private final FinancialAuditService auditService;
    private final FinancialIdempotencyService idempotencyService;

    @org.springframework.beans.factory.annotation.Autowired
    public SurchargeService(
            ReservationRepository reservationRepository,
            ReservationChargeLineRepository chargeLineRepository,
            PropertyAccessService propertyAccessService,
            FinancialAuditService auditService,
            FinancialIdempotencyService idempotencyService) {
        this.reservationRepository = reservationRepository;
        this.chargeLineRepository = chargeLineRepository;
        this.propertyAccessService = propertyAccessService;
        this.auditService = auditService;
        this.idempotencyService = idempotencyService;
    }

    /** Compatibility constructor for focused unit tests that do not exercise persistence idempotency. */
    SurchargeService(
            ReservationRepository reservationRepository,
            ReservationChargeLineRepository chargeLineRepository,
            PropertyAccessService propertyAccessService,
            FinancialAuditService auditService) {
        this(reservationRepository, chargeLineRepository, propertyAccessService, auditService, null);
    }

    @Transactional
    public AddSurchargeResult addSurcharge(AddSurchargeCommand command) {
        validate(command);
        requirePermissions(false);
        User actor = propertyAccessService.currentUser();
        Reservation reservation = lockAuthorizedCheckedInReservation(command.reservationId());
        VndMoney amount = requirePositiveVnd(command.amount());
        FinancialIdempotencyService.BeginResult begin = begin(
                "ADD_RESERVATION_SURCHARGE", reservation, actor, command.idempotencyKey(),
                new SurchargeIdentity(reservation.getId(), command.type(), command.description().trim(), amount.amount()),
                command.correlationId());
        if (begin == null) {
            return new AddSurchargeResult(saveSurcharge(reservation, actor, command, amount), false);
        }
        if (begin instanceof FinancialIdempotencyService.Replay replay) {
            return new AddSurchargeResult(findReplay(replay.responseBody(), reservation), true);
        }
        if (begin instanceof FinancialIdempotencyService.InProgress
                || begin instanceof FinancialIdempotencyService.RetryableFailure) {
            throw new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION);
        }
        FinancialIdempotencyService.Acquired acquired = (FinancialIdempotencyService.Acquired) begin;
        ReservationChargeLine line = saveSurcharge(reservation, actor, command, amount);
        idempotencyService.complete(acquired.recordId(), 201, line.getId().toString());
        return new AddSurchargeResult(line, false);
    }

    private ReservationChargeLine saveSurcharge(
            Reservation reservation,
            User actor,
            AddSurchargeCommand command,
            VndMoney amount) {

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
                amount, command.correlationId(), command.idempotencyKey());
        return line;
    }

    @Transactional
    public AddSurchargeResult addNegativeAdjustment(AddNegativeAdjustmentCommand command) {
        validate(command);
        requirePermissions(true);
        User actor = propertyAccessService.currentUser();
        Reservation reservation = lockAuthorizedCheckedInReservation(command.reservationId());
        VndMoney amount = requirePositiveVnd(command.amount());
        FinancialIdempotencyService.BeginResult begin = begin(
                "ADD_RESERVATION_NEGATIVE_ADJUSTMENT", reservation, actor, command.idempotencyKey(),
                new AdjustmentIdentity(reservation.getId(), command.type(), command.description().trim(), amount.amount()),
                command.correlationId());
        if (begin == null) {
            return new AddSurchargeResult(saveNegativeAdjustment(reservation, actor, command, amount), false);
        }
        if (begin instanceof FinancialIdempotencyService.Replay replay) {
            return new AddSurchargeResult(findReplay(replay.responseBody(), reservation), true);
        }
        if (begin instanceof FinancialIdempotencyService.InProgress
                || begin instanceof FinancialIdempotencyService.RetryableFailure) {
            throw new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION);
        }
        FinancialIdempotencyService.Acquired acquired = (FinancialIdempotencyService.Acquired) begin;
        ReservationChargeLine line = saveNegativeAdjustment(reservation, actor, command, amount);
        idempotencyService.complete(acquired.recordId(), 201, line.getId().toString());
        return new AddSurchargeResult(line, false);
    }

    private ReservationChargeLine saveNegativeAdjustment(
            Reservation reservation,
            User actor,
            AddNegativeAdjustmentCommand command,
            VndMoney amount) {

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
                amount, command.correlationId(), command.idempotencyKey());
        return line;
    }

    @Transactional(readOnly = true)
    public List<AdjustmentHistoryEntry> adjustmentHistory(Long reservationId) {
        if (reservationId == null) {
            throw new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND);
        }
        Reservation reservation = lockAuthorizedReservation(reservationId);
        return chargeLineRepository.findByHotelIdAndReservationIdOrderByCreatedAtAscIdAsc(
                        reservation.getHotel().getId(), reservation.getId()).stream()
                .filter(line -> line.getChargeType() == ReservationChargeLine.ChargeType.SURCHARGE
                        || line.getChargeType() == ReservationChargeLine.ChargeType.DISCOUNT
                        || line.getChargeType() == ReservationChargeLine.ChargeType.ADJUSTMENT)
                .map(line -> new AdjustmentHistoryEntry(
                        line.getId(), line.getReservation().getId(), line.getChargeType().name(),
                        reasonType(line.getCode()), line.getName(), line.getDescription(),
                        line.getTotalAmount(), line.getCreatedAt(),
                        line.getActor() == null ? null : line.getActor().getId(),
                        line.getChargeType() == ReservationChargeLine.ChargeType.DISCOUNT))
                .toList();
    }

    private FinancialIdempotencyService.BeginResult begin(
            String operation,
            Reservation reservation,
            User actor,
            String key,
            Object payload,
            String correlationId) {
        if (idempotencyService == null) {
            return null;
        }
        FinancialIdempotencyService.BeginResult result = idempotencyService.begin(
                new FinancialIdempotencyService.BeginCommand(
                        "PROPERTY_COMMERCE", operation, "RESERVATION:" + reservation.getId(),
                        key, payload, reservation.getHotel().getId(), actor.getId(), correlationId));
        return result;
    }

    private ReservationChargeLine findReplay(String responseBody, Reservation reservation) {
        try {
            Long lineId = Long.valueOf(responseBody);
            return chargeLineRepository.findByIdAndHotelIdAndReservationId(
                            lineId, reservation.getHotel().getId(), reservation.getId())
                    .orElseThrow(() -> new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION));
        } catch (RuntimeException exception) {
            if (exception instanceof FinancialException financialException) throw financialException;
            throw new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION);
        }
    }

    private String reasonType(String code) {
        if (code == null || code.isBlank()) return "OTHER";
        int separator = code.indexOf(':');
        return separator < 0 ? code : code.substring(separator + 1);
    }

    private Reservation lockAuthorizedCheckedInReservation(Long reservationId) {
        Reservation reservation = lockAuthorizedReservation(reservationId);
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

    private Reservation lockAuthorizedReservation(Long reservationId) {
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        Long hotelId = reservation.getHotel() == null ? null : reservation.getHotel().getId();
        if (hotelId == null || (!propertyAccessService.isSystemAdministrator()
                && !propertyAccessService.accessibleHotelIds().contains(hotelId))) {
            throw new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND);
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
            String correlationId,
            String idempotencyKey) {
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
                idempotencyKey,
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
                || command.description() == null || command.description().isBlank() || command.amount() == null
                || (idempotencyService != null && (command.idempotencyKey() == null || command.idempotencyKey().isBlank()))) {
            throw new IllegalArgumentException("Reservation, surcharge type, description and amount are required.");
        }
        requireDescriptionLength(command.description());
    }

    private void validate(AddNegativeAdjustmentCommand command) {
        if (command == null || command.reservationId() == null || command.type() == null
                || command.description() == null || command.description().isBlank() || command.amount() == null
                || (idempotencyService != null && (command.idempotencyKey() == null || command.idempotencyKey().isBlank()))) {
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

    public static SurchargeType parseSurchargeType(String value) {
        return parseType(value, SurchargeType.class);
    }

    public static NegativeAdjustmentType parseNegativeAdjustmentType(String value) {
        return parseType(value, NegativeAdjustmentType.class);
    }

    private static <T extends Enum<T>> T parseType(String value, Class<T> type) {
        String normalized = normalizeType(value);
        if (normalized == null) return null;
        for (T candidate : type.getEnumConstants()) {
            if (candidate.name().equals(normalized)) return candidate;
        }
        if (type == SurchargeType.class) {
            return type.cast(surchargeAlias(normalized));
        }
        return type.cast(negativeAlias(normalized));
    }

    private static SurchargeType surchargeAlias(String value) {
        return switch (value) {
            case "NHAN_PHONG_SOM", "CHECKIN_SOM", "EARLY_CHECKIN" -> SurchargeType.EARLY_CHECK_IN;
            case "TRA_PHONG_MUON", "CHECKOUT_MUON", "LATE_CHECKOUT" -> SurchargeType.LATE_CHECK_OUT;
            case "THEM_KHACH" -> SurchargeType.EXTRA_GUEST;
            case "HU_HONG" -> SurchargeType.DAMAGE;
            case "VE_SINH", "VE_SINH_DAC_BIET" -> SurchargeType.CLEANING;
            case "MAT_CHIA_KHOA" -> SurchargeType.LOST_KEY;
            case "KHAC" -> SurchargeType.OTHER;
            default -> throw new IllegalArgumentException("Unsupported surcharge type: " + value + ".");
        };
    }

    private static NegativeAdjustmentType negativeAlias(String value) {
        return switch (value) {
            case "BOI_HOAN_DICH_VU" -> NegativeAdjustmentType.SERVICE_RECOVERY;
            case "THIEN_CHI", "HO_TRO_THIEN_CHI" -> NegativeAdjustmentType.GOODWILL;
            case "DIEU_CHINH_GIA" -> NegativeAdjustmentType.PRICE_CORRECTION;
            case "GIAM_GIA_THU_CONG" -> NegativeAdjustmentType.MANUAL_DISCOUNT;
            case "KHAC" -> NegativeAdjustmentType.OTHER;
            default -> throw new IllegalArgumentException("Unsupported negative adjustment type: " + value + ".");
        };
    }

    private static String normalizeType(String value) {
        if (value == null || value.isBlank()) return null;
        String withoutMarks = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return withoutMarks.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
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
            String idempotencyKey,
            String correlationId) {
        public AddSurchargeCommand(Long reservationId, SurchargeType type, String description,
                                    BigDecimal amount, String correlationId) {
            this(reservationId, type, description, amount, null, correlationId);
        }
    }

    public record AddNegativeAdjustmentCommand(
            Long reservationId,
            NegativeAdjustmentType type,
            String description,
            BigDecimal amount,
            String idempotencyKey,
            String correlationId) {
        public AddNegativeAdjustmentCommand(Long reservationId, NegativeAdjustmentType type, String description,
                                             BigDecimal amount, String correlationId) {
            this(reservationId, type, description, amount, null, correlationId);
        }
    }

    public record AddSurchargeResult(ReservationChargeLine line, boolean replayed) {
    }

    public record AdjustmentHistoryEntry(
            Long id,
            Long reservationId,
            String chargeType,
            String reasonType,
            String name,
            String description,
            BigDecimal amount,
            LocalDateTime createdAt,
            Long actorId,
            boolean approved) {
    }

    private record SurchargeIdentity(Long reservationId, SurchargeType type, String description, BigDecimal amount) {
    }

    private record AdjustmentIdentity(Long reservationId, NegativeAdjustmentType type, String description, BigDecimal amount) {
    }

}
