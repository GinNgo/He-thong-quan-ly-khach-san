package com.hotel.propertycommerce.checkout;

import com.hotel.entities.Hotel;
import com.hotel.entities.Payment;
import com.hotel.entities.Reservation;
import com.hotel.entities.ReservationDetail;
import com.hotel.entities.ReservationServiceItem;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.propertycommerce.folio.ReservationChargeLine;
import com.hotel.propertycommerce.folio.ReservationChargeLineRepository;
import com.hotel.propertycommerce.payment.PropertyFinancialTransaction;
import com.hotel.propertycommerce.payment.PropertyFinancialTransaction.Direction;
import com.hotel.propertycommerce.payment.PropertyFinancialTransaction.TransactionType;
import com.hotel.propertycommerce.payment.PropertyFinancialTransactionRepository;
import com.hotel.repositories.PaymentRepository;
import com.hotel.repositories.ReservationDetailRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.ReservationServiceItemRepository;
import com.hotel.services.PropertyAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class FolioCalculationService {

    private final ReservationRepository reservationRepository;
    private final ReservationDetailRepository reservationDetailRepository;
    private final ReservationServiceItemRepository reservationServiceItemRepository;
    private final ReservationChargeLineRepository chargeLineRepository;
    private final PropertyFinancialTransactionRepository transactionRepository;
    private final PaymentRepository legacyPaymentRepository;
    private final PropertyAccessService propertyAccessService;
    private final Clock clock;

    @Autowired
    public FolioCalculationService(
            ReservationRepository reservationRepository,
            ReservationDetailRepository reservationDetailRepository,
            ReservationServiceItemRepository reservationServiceItemRepository,
            ReservationChargeLineRepository chargeLineRepository,
            PropertyFinancialTransactionRepository transactionRepository,
            PaymentRepository legacyPaymentRepository,
            PropertyAccessService propertyAccessService) {
        this(reservationRepository, reservationDetailRepository, reservationServiceItemRepository,
                chargeLineRepository, transactionRepository, legacyPaymentRepository,
                propertyAccessService, Clock.systemUTC());
    }

    FolioCalculationService(
            ReservationRepository reservationRepository,
            ReservationDetailRepository reservationDetailRepository,
            ReservationServiceItemRepository reservationServiceItemRepository,
            ReservationChargeLineRepository chargeLineRepository,
            PropertyFinancialTransactionRepository transactionRepository,
            PaymentRepository legacyPaymentRepository,
            PropertyAccessService propertyAccessService,
            Clock clock) {
        this.reservationRepository = reservationRepository;
        this.reservationDetailRepository = reservationDetailRepository;
        this.reservationServiceItemRepository = reservationServiceItemRepository;
        this.chargeLineRepository = chargeLineRepository;
        this.transactionRepository = transactionRepository;
        this.legacyPaymentRepository = legacyPaymentRepository;
        this.propertyAccessService = propertyAccessService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Folio calculate(Long reservationId) {
        if (reservationId == null) {
            throw new IllegalArgumentException("reservationId is required.");
        }
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        authorize(reservation);
        return calculateFromEvidence(
                reservation,
                reservationDetailRepository.findByReservationId(reservationId),
                reservationServiceItemRepository.findByReservationId(reservationId),
                chargeLineRepository.findByReservationIdOrderByCreatedAtAscIdAsc(reservationId),
                transactionRepository.findByReservationIdOrderByOccurredAtAsc(reservationId),
                legacyPaymentRepository.findByReservationId(reservationId));
    }

    public Folio calculateFromEvidence(
            Reservation reservation,
            List<ReservationDetail> details,
            List<ReservationServiceItem> legacyServices,
            List<ReservationChargeLine> chargeLines,
            List<PropertyFinancialTransaction> transactions,
            List<Payment> legacyPayments) {
        Objects.requireNonNull(reservation, "reservation must not be null");
        Objects.requireNonNull(reservation.getHotel(), "reservation hotel must not be null");
        Components totals = new Components();
        List<FolioLine> lines = new ArrayList<>();
        List<ReservationChargeLine> authoritativeLines = safeList(chargeLines);
        boolean hasRoomChargeLines = authoritativeLines.stream()
                .anyMatch(line -> line != null && line.getChargeType() == ReservationChargeLine.ChargeType.ROOM);

        if (!hasRoomChargeLines) {
            addAuthoritativeRoomCharges(reservation, safeList(details), totals, lines);
        }
        Set<Long> reconciledLegacyServiceIds = new HashSet<>();
        for (ReservationChargeLine line : authoritativeLines) {
            if (line != null && line.getLegacyServiceItemId() != null) {
                reconciledLegacyServiceIds.add(line.getLegacyServiceItemId());
            }
        }
        addLegacyServiceCharges(
                reservation,
                safeList(legacyServices),
                reconciledLegacyServiceIds,
                totals,
                lines);
        addChargeLines(reservation, authoritativeLines, totals, lines);
        totals.requireNonNegative();

        Settlement settlement = settlement(
                reservation,
                safeList(transactions),
                safeList(legacyPayments));
        BigDecimal gross = totals.gross();
        if (gross.signum() < 0) {
            throw new IllegalStateException("Folio gross charges cannot be negative.");
        }
        BigDecimal balance = gross.subtract(settlement.netSettled());
        BigDecimal depositRequired = moneyOrZero(reservation.getDepositRequired(), "depositRequired");
        long sourceVersion = sourceVersion(details, legacyServices, chargeLines, transactions, legacyPayments);

        return new Folio(
                reservation.getId(),
                reservation.getHotel().getId(),
                VndMoney.of(totals.room),
                VndMoney.of(totals.service),
                VndMoney.of(totals.surcharge),
                VndMoney.of(totals.tax),
                VndMoney.of(totals.fee),
                VndMoney.of(totals.discount),
                VndMoney.of(gross),
                VndMoney.of(depositRequired),
                VndMoney.of(settlement.payments()),
                VndMoney.of(settlement.refunds()),
                VndMoney.of(settlement.otherCredits()),
                VndMoney.of(settlement.netSettled()),
                balance,
                List.copyOf(lines),
                sourceVersion,
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    }

    private void addAuthoritativeRoomCharges(
            Reservation reservation,
            List<ReservationDetail> details,
            Components totals,
            List<FolioLine> lines) {
        if (details.isEmpty()) {
            BigDecimal fallback = reservation.getDepositBookingTotal() != null
                    ? reservation.getDepositBookingTotal()
                    : reservation.getTotalAmount();
            BigDecimal room = money(fallback, "reservation room total");
            totals.room = totals.room.add(room);
            lines.add(new FolioLine("RESERVATION", reservation.getId(), "ROOM", "ROOM-STAY",
                    "Room stay", null, BigDecimal.ONE, room, BigDecimal.ZERO, BigDecimal.ZERO,
                    room, room, usageStart(reservation), usageEnd(reservation)));
            return;
        }

        BigDecimal detailTotal = BigDecimal.ZERO;
        for (ReservationDetail detail : details) {
            validateReservationOwner(reservation, detail == null ? null : detail.getReservation(), "Room detail");
            BigDecimal unitPrice = money(
                    detail.getUnitPrice() != null ? detail.getUnitPrice() : detail.getPrice(),
                    "room detail unit price");
            int roomQuantity = detail.getQuantity() == null ? 1 : detail.getQuantity();
            long nights = reservation.getCheckInDate() == null || reservation.getCheckOutDate() == null
                    ? 1
                    : ChronoUnit.DAYS.between(reservation.getCheckInDate(), reservation.getCheckOutDate());
            if (roomQuantity <= 0 || nights <= 0) {
                throw new IllegalStateException("Room detail quantity and stay duration must be positive.");
            }
            BigDecimal invoiceQuantity = BigDecimal.valueOf(roomQuantity).multiply(BigDecimal.valueOf(nights));
            BigDecimal calculatedSubtotal = unitPrice.multiply(invoiceQuantity);
            BigDecimal subtotal = detail.getSubtotal() == null
                    ? calculatedSubtotal
                    : money(detail.getSubtotal(), "room detail subtotal");
            if (calculatedSubtotal.compareTo(subtotal) != 0) {
                throw new IllegalStateException("Room detail subtotal does not match room-night pricing.");
            }
            detailTotal = detailTotal.add(subtotal);
            lines.add(new FolioLine(
                    "RESERVATION_DETAIL",
                    detail.getId(),
                    "ROOM",
                    detail.getRoomType() == null || detail.getRoomType().getId() == null
                            ? "ROOM-STAY" : "ROOM-TYPE:" + detail.getRoomType().getId(),
                    detail.getRoomType() == null || detail.getRoomType().getNameVi() == null
                            ? "Room stay" : detail.getRoomType().getNameVi(),
                    null,
                    invoiceQuantity,
                    unitPrice,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    subtotal,
                    subtotal,
                    usageStart(reservation),
                    usageEnd(reservation)));
        }
        if (reservation.getDepositBookingTotal() != null
                && money(reservation.getDepositBookingTotal(), "booking room snapshot").compareTo(detailTotal) != 0) {
            throw new IllegalStateException("Room detail totals do not match the immutable booking snapshot.");
        }
        totals.room = totals.room.add(detailTotal);
    }

    private void addLegacyServiceCharges(
            Reservation reservation,
            List<ReservationServiceItem> legacyServices,
            Set<Long> reconciledLegacyServiceIds,
            Components totals,
            List<FolioLine> lines) {
        for (ReservationServiceItem item : legacyServices) {
            if (item == null || !"ACTIVE".equals(item.getStatus())) {
                continue;
            }
            if (item.getId() != null && reconciledLegacyServiceIds.contains(item.getId())) {
                continue;
            }
            validateReservationOwner(reservation, item.getReservation(), "Legacy service line");
            int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
            if (quantity <= 0) {
                throw new IllegalStateException("Legacy service quantity must be positive.");
            }
            BigDecimal price = money(item.getPrice(), "legacy service price");
            BigDecimal total = money(item.getTotalAmount(), "legacy service total");
            if (price.multiply(BigDecimal.valueOf(quantity)).compareTo(total) != 0) {
                throw new IllegalStateException("Legacy service total does not match its server price snapshot.");
            }
            totals.service = totals.service.add(total);
            lines.add(new FolioLine(
                    "LEGACY_RESERVATION_SERVICE",
                    item.getId(),
                    "SERVICE",
                    item.getHotelService() == null ? "LEGACY-SERVICE" : item.getHotelService().getCode(),
                    item.getHotelService() == null ? "Legacy service" : item.getHotelService().getNameVi(),
                    null,
                    BigDecimal.valueOf(quantity),
                    price,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    total,
                    total,
                    item.getUsedAt(),
                    item.getUsedAt()));
        }
    }

    private void addChargeLines(
            Reservation reservation,
            List<ReservationChargeLine> chargeLines,
            Components totals,
            List<FolioLine> lines) {
        Set<Long> availableIds = new HashSet<>();
        for (ReservationChargeLine line : chargeLines) {
            validateChargeOwnership(reservation, line);
            if (line.getId() != null) {
                availableIds.add(line.getId());
            }
        }

        Set<String> reversedIdentities = new HashSet<>();
        for (ReservationChargeLine line : chargeLines) {
            if (line.getChargeType() == ReservationChargeLine.ChargeType.ADJUSTMENT) {
                ReservationChargeLine original = line.getReversesLine();
                if (original == null) {
                    throw new IllegalStateException("An adjustment line requires an explicit reversed charge.");
                }
                validateChargeOwnership(reservation, original);
                if (original.getId() != null && !availableIds.contains(original.getId())) {
                    throw new IllegalStateException("The reversed charge is missing from the authoritative folio.");
                }
                String identity = original.getId() == null
                        ? Integer.toHexString(System.identityHashCode(original))
                        : original.getId().toString();
                if (!reversedIdentities.add(identity)) {
                    throw new IllegalStateException("A charge line cannot be reversed more than once.");
                }
                requireEquivalentReversal(line, original);
                Components reversal = components(original).negated();
                totals.add(reversal);
                BigDecimal effect = reversal.gross();
                lines.add(folioLine(line, effect));
                continue;
            }
            if (line.getReversesLine() != null) {
                throw new IllegalStateException("Only adjustment lines may reference a reversed charge.");
            }

            Components lineComponents = components(line);
            totals.add(lineComponents);
            lines.add(folioLine(line, lineComponents.gross()));
        }
    }

    private Components components(ReservationChargeLine line) {
        BigDecimal unitPrice = money(line.getUnitPrice(), "charge unit price");
        BigDecimal quantity = quantity(line.getQuantity());
        BigDecimal tax = money(line.getTaxAmount(), "charge tax");
        BigDecimal discount = money(line.getDiscountAmount(), "charge discount");
        BigDecimal total = money(line.getTotalAmount(), "charge total");
        BigDecimal base = money(unitPrice.multiply(quantity), "charge base");
        Components components = new Components();

        switch (line.getChargeType()) {
            case ROOM -> components.room = base;
            case SERVICE, MINIBAR -> components.service = base;
            case SURCHARGE -> components.surcharge = base;
            case FEE -> components.fee = base;
            case TAX -> {
                if (discount.signum() != 0) {
                    throw new IllegalStateException("Tax lines cannot contain a discount.");
                }
                components.tax = total;
                return components;
            }
            case DISCOUNT -> {
                if (base.signum() != 0 || tax.signum() != 0
                        || discount.signum() != 0 && discount.compareTo(total) != 0) {
                    throw new IllegalStateException("Discount lines must contain one unambiguous discount magnitude.");
                }
                components.discount = total;
                return components;
            }
            case ADJUSTMENT -> throw new IllegalStateException("Adjustment direction requires reversal context.");
        }
        components.tax = tax;
        components.discount = discount;
        BigDecimal expected = base.add(tax).subtract(discount);
        if (expected.signum() < 0 || expected.compareTo(total) != 0) {
            throw new IllegalStateException("Charge total does not reconcile with price, quantity, tax and discount.");
        }
        return components;
    }

    private void requireEquivalentReversal(ReservationChargeLine reversal, ReservationChargeLine original) {
        if (reversal.getReversesLine() != original
                && (reversal.getReversesLine().getId() == null || original.getId() == null
                || !reversal.getReversesLine().getId().equals(original.getId()))) {
            throw new IllegalStateException("Adjustment reversal identity is inconsistent.");
        }
        if (reversal.getUnitPrice().compareTo(original.getUnitPrice()) != 0
                || reversal.getQuantity().compareTo(original.getQuantity()) != 0
                || reversal.getTaxAmount().compareTo(original.getTaxAmount()) != 0
                || reversal.getDiscountAmount().compareTo(original.getDiscountAmount()) != 0
                || reversal.getTotalAmount().compareTo(original.getTotalAmount()) != 0) {
            throw new IllegalStateException("Adjustment reversal must preserve the original monetary snapshot.");
        }
    }

    private Settlement settlement(
            Reservation reservation,
            List<PropertyFinancialTransaction> transactions,
            List<Payment> legacyPayments) {
        BigDecimal payments = BigDecimal.ZERO;
        BigDecimal refunds = BigDecimal.ZERO;
        BigDecimal otherCredits = BigDecimal.ZERO;

        if (!transactions.isEmpty()) {
            for (PropertyFinancialTransaction transaction : transactions) {
                validateTransactionOwnership(reservation, transaction);
                if (transaction.isLegacyReconciliationRequired()) {
                    continue;
                }
                BigDecimal amount = money(transaction.getAmount(), "transaction amount");
                if (transaction.getTransactionType() == TransactionType.REFUND) {
                    refunds = refunds.add(amount);
                } else if (transaction.getDirection() == Direction.DEBIT) {
                    payments = payments.add(amount);
                } else {
                    otherCredits = otherCredits.add(amount);
                }
            }
        } else {
            for (Payment payment : legacyPayments) {
                if (payment == null) {
                    continue;
                }
                validateReservationOwner(reservation, payment.getReservation(), "Legacy payment");
                String legacyStatus = normalizeLegacyStatus(payment.getStatus());
                if ("REFUNDED".equals(legacyStatus)) {
                    throw new IllegalStateException("Legacy REFUNDED payment rows require reconciliation evidence.");
                }
                if (!Set.of("SUCCESS", "SUCCEEDED", "PAID", "COMPLETED").contains(legacyStatus)) {
                    if (!Set.of("CREATED", "PENDING", "PENDING_PAYMENT", "PROCESSING", "FAILED",
                            "CANCELLED", "CANCELED", "EXPIRED").contains(legacyStatus)) {
                        throw new IllegalStateException("Unsupported legacy payment status: " + legacyStatus);
                    }
                    continue;
                }
                BigDecimal raw = Objects.requireNonNull(payment.getAmount(), "Legacy payment amount is required.");
                BigDecimal amount = money(raw.abs(), "legacy payment amount");
                if (amount.signum() == 0) {
                    continue;
                }
                if (raw.signum() < 0) {
                    refunds = refunds.add(amount);
                } else {
                    payments = payments.add(amount);
                }
            }
        }

        BigDecimal credits = refunds.add(otherCredits);
        if (credits.compareTo(payments) > 0) {
            throw new IllegalStateException("Refunds and credits cannot exceed successful payments.");
        }
        return new Settlement(payments, refunds, otherCredits, payments.subtract(credits));
    }

    private void authorize(Reservation reservation) {
        Long hotelId = reservation.getHotel() == null ? null : reservation.getHotel().getId();
        if (hotelId == null || (!propertyAccessService.isSystemAdministrator()
                && !propertyAccessService.accessibleHotelIds().contains(hotelId))) {
            throw new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private void validateChargeOwnership(Reservation reservation, ReservationChargeLine line) {
        if (line == null || !sameHotel(reservation.getHotel(), line.getHotel())
                || !sameReservation(reservation, line.getReservation())) {
            throw new IllegalArgumentException("Charge line belongs to another property or reservation.");
        }
    }

    private void validateTransactionOwnership(
            Reservation reservation,
            PropertyFinancialTransaction transaction) {
        if (transaction == null || !sameHotel(reservation.getHotel(), transaction.getHotel())
                || transaction.getReservation() != null
                && !sameReservation(reservation, transaction.getReservation())) {
            throw new IllegalArgumentException("Financial transaction belongs to another property or reservation.");
        }
    }

    private void validateReservationOwner(Reservation expected, Reservation actual, String source) {
        if (actual == null || !sameReservation(expected, actual)
                || !sameHotel(expected.getHotel(), actual.getHotel())) {
            throw new IllegalArgumentException(source + " belongs to another property or reservation.");
        }
    }

    private FolioLine folioLine(ReservationChargeLine line, BigDecimal signedEffect) {
        return new FolioLine(
                "RESERVATION_CHARGE_LINE",
                line.getId(),
                line.getChargeType().name(),
                line.getCode(),
                line.getName(),
                line.getDescription(),
                line.getQuantity(),
                line.getUnitPrice(),
                line.getTaxAmount(),
                line.getDiscountAmount(),
                line.getTotalAmount(),
                signedEffect,
                line.getServiceUsedAt(),
                line.getServiceUsedAt());
    }

    private LocalDateTime usageStart(Reservation reservation) {
        return reservation.getCheckInDate() == null ? null : reservation.getCheckInDate().atStartOfDay();
    }

    private LocalDateTime usageEnd(Reservation reservation) {
        return reservation.getCheckOutDate() == null ? null : reservation.getCheckOutDate().atStartOfDay();
    }

    private BigDecimal quantity(BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.scale() > 3) {
            throw new IllegalStateException("Charge quantity is invalid.");
        }
        return value;
    }

    private BigDecimal money(BigDecimal value, String field) {
        try {
            BigDecimal amount = VndMoney.of(value).amount();
            if (amount.precision() > 19) {
                throw new ArithmeticException("amount outside supported range");
            }
            return amount;
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw new IllegalStateException(field + " is not an exact non-negative VND amount.", exception);
        }
    }

    private BigDecimal moneyOrZero(BigDecimal value, String field) {
        return value == null ? BigDecimal.ZERO : money(value, field);
    }

    private String normalizeLegacyStatus(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Legacy payment status is required.");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private long sourceVersion(
            List<ReservationDetail> details,
            List<ReservationServiceItem> legacyServices,
            List<ReservationChargeLine> chargeLines,
            List<PropertyFinancialTransaction> transactions,
            List<Payment> legacyPayments) {
        long maximum = 0;
        maximum = max(maximum, safeList(details).stream().map(ReservationDetail::getId).toList());
        maximum = max(maximum, safeList(legacyServices).stream().map(ReservationServiceItem::getId).toList());
        maximum = max(maximum, safeList(chargeLines).stream().map(ReservationChargeLine::getId).toList());
        maximum = max(maximum, safeList(transactions).stream().map(PropertyFinancialTransaction::getId).toList());
        maximum = max(maximum, safeList(legacyPayments).stream().map(Payment::getId).toList());
        return maximum;
    }

    private long max(long current, List<Long> ids) {
        long maximum = current;
        for (Long id : ids) {
            if (id != null) maximum = Math.max(maximum, id);
        }
        return maximum;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private boolean sameHotel(Hotel left, Hotel right) {
        if (left == null || right == null) return false;
        if (left.getId() != null && right.getId() != null) return left.getId().equals(right.getId());
        return left == right;
    }

    private boolean sameReservation(Reservation left, Reservation right) {
        if (left == null || right == null) return false;
        if (left.getId() != null && right.getId() != null) return left.getId().equals(right.getId());
        return left == right;
    }

    private static final class Components {
        private BigDecimal room = BigDecimal.ZERO;
        private BigDecimal service = BigDecimal.ZERO;
        private BigDecimal surcharge = BigDecimal.ZERO;
        private BigDecimal tax = BigDecimal.ZERO;
        private BigDecimal fee = BigDecimal.ZERO;
        private BigDecimal discount = BigDecimal.ZERO;

        private void add(Components other) {
            room = room.add(other.room);
            service = service.add(other.service);
            surcharge = surcharge.add(other.surcharge);
            tax = tax.add(other.tax);
            fee = fee.add(other.fee);
            discount = discount.add(other.discount);
        }

        private Components negated() {
            Components negated = new Components();
            negated.room = room.negate();
            negated.service = service.negate();
            negated.surcharge = surcharge.negate();
            negated.tax = tax.negate();
            negated.fee = fee.negate();
            negated.discount = discount.negate();
            return negated;
        }

        private BigDecimal gross() {
            return room.add(service).add(surcharge).add(tax).add(fee).subtract(discount);
        }

        private void requireNonNegative() {
            if (room.signum() < 0 || service.signum() < 0 || surcharge.signum() < 0
                    || tax.signum() < 0 || fee.signum() < 0 || discount.signum() < 0) {
                throw new IllegalStateException("Folio component totals cannot be negative.");
            }
        }
    }

    private record Settlement(
            BigDecimal payments,
            BigDecimal refunds,
            BigDecimal otherCredits,
            BigDecimal netSettled) {
    }

    public record FolioLine(
            String sourceType,
            Long sourceId,
            String category,
            String code,
            String name,
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal taxAmount,
            BigDecimal discountAmount,
            BigDecimal snapshotAmount,
            BigDecimal signedEffect,
            LocalDateTime usageStartedAt,
            LocalDateTime usageEndedAt) {
    }

    public record Folio(
            Long reservationId,
            Long hotelId,
            VndMoney roomCharges,
            VndMoney serviceCharges,
            VndMoney surchargeCharges,
            VndMoney taxCharges,
            VndMoney feeCharges,
            VndMoney discounts,
            VndMoney grossCharges,
            VndMoney depositRequired,
            VndMoney successfulPayments,
            VndMoney successfulRefunds,
            VndMoney otherCredits,
            VndMoney netSettled,
            BigDecimal balance,
            List<FolioLine> lines,
            long sourceVersion,
            LocalDateTime calculatedAt) {
    }
}
