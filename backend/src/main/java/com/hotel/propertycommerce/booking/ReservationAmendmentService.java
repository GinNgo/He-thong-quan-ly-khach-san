package com.hotel.propertycommerce.booking;

import com.hotel.domain.lifecycle.ReservationStatus;
import com.hotel.entities.Reservation;
import com.hotel.entities.ReservationDetail;
import com.hotel.entities.ReservationHold;
import com.hotel.entities.RoomType;
import com.hotel.entities.User;
import com.hotel.paymentprovider.domain.FinancialStates.PaymentState;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.paymentprovider.error.FinancialErrorCode;
import com.hotel.paymentprovider.error.FinancialException;
import com.hotel.propertycommerce.config.PropertyPaymentConfiguration;
import com.hotel.propertycommerce.config.PropertyPaymentConfigurationMethod;
import com.hotel.propertycommerce.config.PropertyPaymentConfigurationRepository;
import com.hotel.propertycommerce.payment.PropertyFinancialTransaction;
import com.hotel.propertycommerce.payment.PropertyFinancialTransactionRepository;
import com.hotel.propertycommerce.payment.PropertyPaymentAttempt;
import com.hotel.propertycommerce.payment.PropertyPaymentAttemptService;
import com.hotel.propertycommerce.refund.PropertyRefundRequest;
import com.hotel.propertycommerce.refund.PropertyRefundRequestRepository;
import com.hotel.propertycommerce.refund.PropertyRefundService;
import com.hotel.repositories.ReservationDetailRepository;
import com.hotel.repositories.ReservationHoldRepository;
import com.hotel.repositories.ReservationRepository;
import com.hotel.repositories.ReservationRoomRepository;
import com.hotel.repositories.RoomTypeRepository;
import com.hotel.services.OperationalAuditService;
import com.hotel.services.PropertyAccessService;
import com.hotel.services.PublicInventoryEligibilityPolicy;
import com.hotel.services.RoomAvailabilityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class ReservationAmendmentService {

    public enum AccessMode {
        CUSTOMER,
        STAFF
    }

    private static final List<ReservationAmendment.Status> ACTIVE_STATUSES = List.of(
            ReservationAmendment.Status.QUOTED,
            ReservationAmendment.Status.AWAITING_PAYMENT,
            ReservationAmendment.Status.PAYMENT_PENDING);

    private final ReservationRepository reservationRepository;
    private final ReservationDetailRepository detailRepository;
    private final ReservationRoomRepository reservationRoomRepository;
    private final ReservationHoldRepository holdRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final ReservationAmendmentRepository amendmentRepository;
    private final PropertyFinancialTransactionRepository transactionRepository;
    private final PropertyRefundRequestRepository refundRequestRepository;
    private final PropertyPaymentConfigurationRepository paymentConfigurationRepository;
    private final ReservationAmendmentPolicy policy;
    private final RoomAvailabilityService availabilityService;
    private final PublicInventoryEligibilityPolicy inventoryEligibilityPolicy;
    private final PropertyAccessService propertyAccessService;
    private final PropertyPaymentAttemptService paymentAttemptService;
    private final PropertyRefundService refundService;
    private final BookingFinancialSummaryService financialSummaryService;
    private final OperationalAuditService auditService;

    public ReservationAmendmentService(
            ReservationRepository reservationRepository,
            ReservationDetailRepository detailRepository,
            ReservationRoomRepository reservationRoomRepository,
            ReservationHoldRepository holdRepository,
            RoomTypeRepository roomTypeRepository,
            ReservationAmendmentRepository amendmentRepository,
            PropertyFinancialTransactionRepository transactionRepository,
            PropertyRefundRequestRepository refundRequestRepository,
            PropertyPaymentConfigurationRepository paymentConfigurationRepository,
            ReservationAmendmentPolicy policy,
            RoomAvailabilityService availabilityService,
            PublicInventoryEligibilityPolicy inventoryEligibilityPolicy,
            PropertyAccessService propertyAccessService,
            PropertyPaymentAttemptService paymentAttemptService,
            PropertyRefundService refundService,
            BookingFinancialSummaryService financialSummaryService,
            OperationalAuditService auditService) {
        this.reservationRepository = reservationRepository;
        this.detailRepository = detailRepository;
        this.reservationRoomRepository = reservationRoomRepository;
        this.holdRepository = holdRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.amendmentRepository = amendmentRepository;
        this.transactionRepository = transactionRepository;
        this.refundRequestRepository = refundRequestRepository;
        this.paymentConfigurationRepository = paymentConfigurationRepository;
        this.policy = policy;
        this.availabilityService = availabilityService;
        this.inventoryEligibilityPolicy = inventoryEligibilityPolicy;
        this.propertyAccessService = propertyAccessService;
        this.paymentAttemptService = paymentAttemptService;
        this.refundService = refundService;
        this.financialSummaryService = financialSummaryService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public ContextResponse context(Long reservationId, AccessMode accessMode) {
        Reservation reservation = findReservation(reservationId);
        authorize(reservation, accessMode);
        ReservationDetail detail = requireSingleDetail(reservation.getId());
        String blockedReason = null;
        try {
            policy.requireEditable(
                    reservation.getStatus(), reservation.getCheckInDate(), reservation.getHotel().getCheckinTime());
        } catch (FinancialException exception) {
            blockedReason = exception.getMessage();
        }
        List<RoomTypeOption> roomTypes = roomTypeRepository.findByHotelId(reservation.getHotel().getId()).stream()
                .filter(roomType -> "ACTIVE".equalsIgnoreCase(roomType.getStatus()))
                .sorted(Comparator.comparing(RoomType::getNameVi, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(this::roomTypeOption)
                .toList();
        return new ContextResponse(
                reservation.getId(),
                blockedReason == null,
                blockedReason,
                utc(policy.cutoffAt(
                        reservation.getCheckInDate(), reservation.getHotel().getCheckinTime())),
                ReservationAmendmentPolicy.VERSION,
                snapshot(reservation, detail, reservation.getTotalAmount(), depositRequired(reservation)),
                roomTypes,
                configuredPaymentMethods(reservation.getHotel().getId()));
    }

    @Transactional
    public QuoteResponse quote(
            Long reservationId,
            QuoteRequest request,
            String idempotencyKey,
            String correlationId,
            AccessMode accessMode) {
        validateQuoteRequest(request);
        String normalizedKey = normalizeKey(idempotencyKey);
        Reservation reservation = lockedReservation(reservationId);
        User actor = authorize(reservation, accessMode);
        policy.requireEditable(
                reservation.getStatus(), reservation.getCheckInDate(), reservation.getHotel().getCheckinTime());
        ReservationDetail detail = requireSingleDetail(reservation.getId());
        String requestHash = hash(quoteIdentity(reservation.getId(), request));
        ReservationAmendment replay = amendmentRepository.findByHotelIdAndIdempotencyKey(
                        reservation.getHotel().getId(), normalizedKey)
                .orElse(null);
        if (replay != null) {
            requireQuoteReservation(replay, reservation.getId());
            verifyHash(replay.getRequestHash(), requestHash);
            replay.markExpired(policy.now());
            return response(replay, true);
        }

        List<ReservationAmendment> activeQuotes = amendmentRepository.findActiveByReservationIdForUpdate(
                reservation.getId(), ACTIVE_STATUSES);
        activeQuotes.forEach(existing -> {
            existing.markExpired(policy.now());
            if (existing.isActive()) {
                existing.markCancelled();
            }
        });

        RoomType currentType = Objects.requireNonNull(detail.getRoomType(), "Reservation room type is required.");
        Map<Long, RoomType> lockedTypes = lockRoomTypes(currentType.getId(), request.proposedRoomTypeId());
        RoomType proposedType = lockedTypes.get(request.proposedRoomTypeId());
        if (proposedType == null
                || proposedType.getHotel() == null
                || !Objects.equals(proposedType.getHotel().getId(), reservation.getHotel().getId())) {
            throw new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND);
        }
        inventoryEligibilityPolicy.requireSellableRoomTypeForBooking(proposedType);
        availabilityService.validateCapacity(
                proposedType, request.proposedQuantity(), request.proposedAdults(), request.proposedChildren());
        long proposedNights = availabilityService.getNights(
                request.proposedCheckInDate(), request.proposedCheckOutDate());
        boolean structuralChange = structuralChange(reservation, detail, request);
        if (structuralChange && !reservationRoomRepository.findAssignedByReservationIdForUpdate(
                reservation.getId()).isEmpty()) {
            throw new FinancialException(
                    FinancialErrorCode.CONCURRENT_MODIFICATION,
                    "Assigned physical rooms must be released or reassigned before changing stay structure.");
        }
        long available = availabilityService.countAvailableRoomsExcludingReservation(
                proposedType.getId(),
                request.proposedCheckInDate(),
                request.proposedCheckOutDate(),
                reservation.getId(),
                null,
                policy.now());
        if (available < request.proposedQuantity()) {
            throw new FinancialException(
                    FinancialErrorCode.CONCURRENT_MODIFICATION,
                    "The requested room inventory is no longer available.");
        }

        long originalNights = availabilityService.getNights(
                reservation.getCheckInDate(), reservation.getCheckOutDate());
        BigDecimal originalGross = availabilityService.calculateTotal(
                currentType.getBasePrice(), originalNights, quantity(detail));
        BigDecimal originalTotal = money(reservation.getTotalAmount(), "reservation total");
        BigDecimal preservedDiscount = originalGross.subtract(originalTotal).max(BigDecimal.ZERO);
        BigDecimal proposedGross = availabilityService.calculateTotal(
                proposedType.getBasePrice(), proposedNights, request.proposedQuantity());
        BigDecimal appliedDiscount = preservedDiscount.min(proposedGross);
        BigDecimal proposedTotal = VndMoney.of(proposedGross.subtract(appliedDiscount)).amount();
        DepositPolicySnapshot originalDeposit = requireDepositSnapshot(reservation);
        DepositPolicySnapshot proposedDeposit = DepositPolicySnapshot.reprice(originalDeposit, proposedTotal);
        int holdQuantity = sameInventoryWindow(reservation, detail, request)
                ? Math.max(request.proposedQuantity() - quantity(detail), 0)
                : request.proposedQuantity();
        ReservationAmendment amendment = ReservationAmendment.quote(
                new ReservationAmendment.QuoteSnapshot(
                        UUID.randomUUID().toString(),
                        reservation,
                        actor,
                        accessMode.name(),
                        currentType,
                        proposedType,
                        reservation.getCheckInDate(),
                        reservation.getCheckOutDate(),
                        request.proposedCheckInDate(),
                        request.proposedCheckOutDate(),
                        quantity(detail),
                        request.proposedQuantity(),
                        adults(detail),
                        request.proposedAdults(),
                        children(detail),
                        request.proposedChildren(),
                        originalTotal,
                        proposedTotal,
                        proposedTotal.subtract(originalTotal),
                        originalDeposit.requiredDeposit().amount(),
                        proposedDeposit.requiredDeposit().amount(),
                        appliedDiscount,
                        holdQuantity,
                        normalizedKey,
                        requestHash,
                        policy.quoteExpiresAt()));
        amendment = amendmentRepository.saveAndFlush(amendment);
        appendAudit(
                amendment,
                "RESERVATION_AMENDMENT_QUOTED",
                "A server-owned reservation amendment quote was created.",
                originalState(amendment),
                proposedState(amendment),
                correlationId);
        return response(amendment, false);
    }

    @Transactional
    public QuoteResponse get(
            Long reservationId,
            String quotePublicId,
            AccessMode accessMode) {
        Reservation reservation = findReservation(reservationId);
        authorize(reservation, accessMode);
        ReservationAmendment amendment = amendmentRepository.findByPublicId(requireText(quotePublicId, "quoteId"))
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        requireQuoteReservation(amendment, reservationId);
        amendment.markExpired(policy.now());
        return response(amendment, false);
    }

    @Transactional
    public QuoteResponse createPaymentAttempt(
            Long reservationId,
            String quotePublicId,
            String method,
            String idempotencyKey,
            String correlationId,
            AccessMode accessMode) {
        Reservation reservation = lockedReservation(reservationId);
        authorize(reservation, accessMode);
        ReservationAmendment amendment = lockedQuote(reservationId, quotePublicId);
        paymentAttemptService.createAmendmentDelta(
                new PropertyPaymentAttemptService.CreateAmendmentCommand(
                        amendment,
                        requireText(method, "payment method"),
                        normalizeKey(idempotencyKey),
                        correlationId));
        amendmentRepository.saveAndFlush(amendment);
        appendAudit(
                amendment,
                "RESERVATION_AMENDMENT_PAYMENT_STARTED",
                "A payment attempt was created for the exact amendment price increase.",
                Map.of("status", ReservationAmendment.Status.AWAITING_PAYMENT.name()),
                Map.of("status", amendment.getStatus().name(),
                        "paymentAttemptId", amendment.getPaymentAttempt().getPublicId()),
                correlationId);
        return response(amendment, false);
    }

    @Transactional
    public QuoteResponse apply(
            Long reservationId,
            String quotePublicId,
            String idempotencyKey,
            String correlationId,
            AccessMode accessMode) {
        String applyKey = normalizeKey(idempotencyKey);
        Reservation reservation = lockedReservation(reservationId);
        authorize(reservation, accessMode);
        ReservationAmendment amendment = lockedQuote(reservationId, quotePublicId);
        if (amendment.getStatus() == ReservationAmendment.Status.APPLIED) {
            if (!Objects.equals(amendment.getApplyIdempotencyKey(), applyKey)) {
                throw new FinancialException(FinancialErrorCode.IDEMPOTENCY_KEY_REUSED);
            }
            return response(amendment, true);
        }
        amendment.markExpired(policy.now());
        if (!amendment.isActive()) {
            throw new FinancialException(FinancialErrorCode.INVALID_STATE_TRANSITION,
                    "The amendment quote is no longer active.");
        }
        policy.requireEditable(
                reservation.getStatus(), reservation.getCheckInDate(), reservation.getHotel().getCheckinTime());
        ReservationDetail detail = requireSingleDetail(reservation.getId());
        requireUnchangedSnapshot(reservation, detail, amendment);
        lockRoomTypes(amendment.getOriginalRoomType().getId(), amendment.getProposedRoomType().getId());
        if (amendment.structuralChange()
                && !reservationRoomRepository.findAssignedByReservationIdForUpdate(reservation.getId()).isEmpty()) {
            throw new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION,
                    "Assigned physical rooms must be released or reassigned before applying this quote.");
        }
        long available = availabilityService.countAvailableRoomsExcludingReservation(
                amendment.getProposedRoomType().getId(),
                amendment.getProposedCheckIn(),
                amendment.getProposedCheckOut(),
                reservation.getId(),
                amendment.getId(),
                policy.now());
        if (available < amendment.getProposedQuantity()) {
            throw new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION,
                    "Inventory changed while the amendment quote was active.");
        }

        verifyPositiveDeltaSettlement(amendment);
        PropertyRefundRequest refundRequest = requestDecreaseRefund(amendment, reservation, correlationId);
        Map<String, Object> beforeState = currentReservationState(reservation, detail);
        applyReservationSnapshot(reservation, detail, amendment);
        updatePendingReservationHold(reservation, amendment);
        reservationRepository.save(reservation);
        detailRepository.save(detail);
        financialSummaryService.refresh(reservation.getId());
        amendment.markApplied(applyKey, refundRequest, policy.now());
        amendmentRepository.saveAndFlush(amendment);
        appendAudit(
                amendment,
                "RESERVATION_AMENDMENT_APPLIED",
                "The reservation amendment was applied after inventory and settlement revalidation.",
                beforeState,
                currentReservationState(reservation, detail),
                correlationId);
        return response(amendment, false);
    }

    private PropertyRefundRequest requestDecreaseRefund(
            ReservationAmendment amendment,
            Reservation reservation,
            String correlationId) {
        if (amendment.getPriceDelta().signum() >= 0) {
            return null;
        }
        BookingFinancialSummaryService.Summary summary = financialSummaryService.calculate(reservation.getId());
        BigDecimal netPaid = summary.successfulPayments().amount().subtract(summary.successfulRefunds().amount());
        BigDecimal excessAfterNewDeposit = netPaid.subtract(amendment.getProposedDeposit()).max(BigDecimal.ZERO);
        BigDecimal refundAmount = amendment.getPriceDelta().abs().min(excessAfterNewDeposit);
        if (refundAmount.signum() == 0) {
            return null;
        }
        PropertyRefundService.RefundResult result = refundService.requestSingleSource(
                new PropertyRefundService.SingleSourceCommand(
                        reservation.getId(),
                        refundAmount,
                        "RESERVATION_AMENDMENT_PRICE_DECREASE:" + amendment.getPublicId(),
                        "AMENDMENT-REFUND:" + amendment.getPublicId(),
                        correlationId));
        return refundRequestRepository.findByPublicId(result.publicId())
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION));
    }

    private void verifyPositiveDeltaSettlement(ReservationAmendment amendment) {
        if (amendment.getPriceDelta().signum() <= 0) {
            return;
        }
        PropertyPaymentAttempt attempt = amendment.getPaymentAttempt();
        if (attempt == null || attempt.getStatus() != PaymentState.SUCCESS) {
            throw new FinancialException(FinancialErrorCode.INVALID_STATE_TRANSITION,
                    "The amendment price increase has not been paid successfully.");
        }
        List<PropertyFinancialTransaction> transactions = transactionRepository
                .findByAttemptIdOrderByOccurredAtAsc(attempt.getId());
        if (transactions.size() != 1 || !matchesExactDelta(transactions.get(0), amendment, attempt)) {
            throw new FinancialException(
                    FinancialErrorCode.CONCURRENT_MODIFICATION,
                    "The successful amendment payment has incomplete or mismatched ledger evidence.");
        }
    }

    private boolean matchesExactDelta(
            PropertyFinancialTransaction transaction,
            ReservationAmendment amendment,
            PropertyPaymentAttempt attempt) {
        return transaction.getDirection() == PropertyFinancialTransaction.Direction.DEBIT
                && transaction.getTransactionType() == PropertyFinancialTransaction.TransactionType.ROOM_PAYMENT
                && transaction.getAmount().compareTo(amendment.getPriceDelta()) == 0
                && "VND".equals(transaction.getCurrency())
                && !transaction.isLegacyReconciliationRequired()
                && transaction.getOriginalTransaction() == null
                && transaction.getAttempt() != null
                && Objects.equals(transaction.getAttempt().getId(), attempt.getId())
                && transaction.getReservation() != null
                && Objects.equals(transaction.getReservation().getId(), amendment.getReservation().getId())
                && Objects.equals(transaction.getHotel().getId(), amendment.getHotel().getId());
    }

    private void applyReservationSnapshot(
            Reservation reservation,
            ReservationDetail detail,
            ReservationAmendment amendment) {
        reservation.setCheckInDate(amendment.getProposedCheckIn());
        reservation.setCheckOutDate(amendment.getProposedCheckOut());
        reservation.setGuests(amendment.getProposedAdults() + amendment.getProposedChildren());
        reservation.setTotalAmount(amendment.getProposedTotal());
        reservation.applyAmendedDepositPolicy(DepositPolicySnapshot.reprice(
                requireDepositSnapshot(reservation), amendment.getProposedTotal()));
        detail.setRoomType(amendment.getProposedRoomType());
        detail.setQuantity(amendment.getProposedQuantity());
        detail.setAdults(amendment.getProposedAdults());
        detail.setChildren(amendment.getProposedChildren());
        detail.setUnitPrice(amendment.getProposedRoomType().getBasePrice());
        detail.setPrice(amendment.getProposedRoomType().getBasePrice());
        long nights = availabilityService.getNights(
                amendment.getProposedCheckIn(), amendment.getProposedCheckOut());
        detail.setSubtotal(amendment.getProposedRoomType().getBasePrice()
                .multiply(BigDecimal.valueOf(nights))
                .multiply(BigDecimal.valueOf(amendment.getProposedQuantity())));
    }

    private void updatePendingReservationHold(
            Reservation reservation,
            ReservationAmendment amendment) {
        if (ReservationStatus.fromStorage(reservation.getStatus()) != ReservationStatus.PENDING_PAYMENT) {
            return;
        }
        ReservationHold hold = holdRepository.findActiveByReservationIdForUpdate(reservation.getId()).orElse(null);
        if (hold != null) {
            hold.setRoomType(amendment.getProposedRoomType());
            hold.setQuantity(amendment.getProposedQuantity());
            holdRepository.save(hold);
        }
    }

    private void requireUnchangedSnapshot(
            Reservation reservation,
            ReservationDetail detail,
            ReservationAmendment amendment) {
        boolean unchanged = Objects.equals(reservation.getCheckInDate(), amendment.getOriginalCheckIn())
                && Objects.equals(reservation.getCheckOutDate(), amendment.getOriginalCheckOut())
                && money(reservation.getTotalAmount(), "reservation total").compareTo(amendment.getOriginalTotal()) == 0
                && detail.getRoomType() != null
                && Objects.equals(detail.getRoomType().getId(), amendment.getOriginalRoomType().getId())
                && quantity(detail) == amendment.getOriginalQuantity()
                && adults(detail) == amendment.getOriginalAdults()
                && children(detail) == amendment.getOriginalChildren()
                && depositRequired(reservation).compareTo(amendment.getOriginalDeposit()) == 0;
        if (!unchanged) {
            throw new FinancialException(FinancialErrorCode.CONCURRENT_MODIFICATION,
                    "The reservation changed after this amendment quote was created.");
        }
    }

    private QuoteResponse response(ReservationAmendment amendment, boolean replayed) {
        PropertyPaymentAttempt attempt = amendment.getPaymentAttempt();
        String settlementType = settlementType(amendment, attempt);
        BigDecimal settlementAmount = amendment.getPriceDelta().signum() > 0
                ? amendment.getPriceDelta()
                : amendment.getRefundRequest() == null
                    ? BigDecimal.ZERO
                    : amendment.getRefundRequest().getRequestedAmount();
        return new QuoteResponse(
                amendment.getPublicId(),
                amendment.getReservation().getId(),
                amendment.getStatus(),
                amendment.getPolicyVersion(),
                snapshot(amendment, false),
                snapshot(amendment, true),
                signedAmount(amendment.getPriceDelta()),
                amount(amendment.getPreservedDiscount()),
                utc(amendment.getExpiresAt()),
                utc(policy.cutoffAt(
                        amendment.getReservation().getCheckInDate(),
                        amendment.getHotel().getCheckinTime())),
                new SettlementResponse(
                        settlementType,
                        amount(settlementAmount),
                        attempt == null ? null : paymentAttempt(attempt),
                        amendment.getRefundRequest() == null
                                ? null
                                : amendment.getRefundRequest().getPublicId()),
                replayed);
    }

    private String settlementType(ReservationAmendment amendment, PropertyPaymentAttempt attempt) {
        if (amendment.getRefundRequest() != null) {
            return "REFUND_PENDING";
        }
        if (amendment.getPriceDelta().signum() <= 0) {
            return "NONE";
        }
        if (attempt == null
                || attempt.getStatus() == PaymentState.FAILED
                || attempt.getStatus() == PaymentState.CANCELLED
                || attempt.getStatus() == PaymentState.EXPIRED) {
            return "PAYMENT_REQUIRED";
        }
        return "PAYMENT_PENDING";
    }

    private PaymentAttemptResponse paymentAttempt(PropertyPaymentAttempt attempt) {
        return new PaymentAttemptResponse(
                attempt.getPublicId(),
                attempt.getPurpose(),
                attempt.getStatus(),
                amount(attempt.getExpectedAmount()),
                utc(attempt.getExpiresAt()),
                attempt.getMethod(),
                attempt.getProvider(),
                attempt.getUniqueTransferContent());
    }

    private StaySnapshot snapshot(ReservationAmendment amendment, boolean proposed) {
        return new StaySnapshot(
                proposed ? amendment.getProposedRoomType().getId() : amendment.getOriginalRoomType().getId(),
                proposed ? roomTypeName(amendment.getProposedRoomType()) : roomTypeName(amendment.getOriginalRoomType()),
                proposed ? amendment.getProposedCheckIn() : amendment.getOriginalCheckIn(),
                proposed ? amendment.getProposedCheckOut() : amendment.getOriginalCheckOut(),
                proposed ? amendment.getProposedQuantity() : amendment.getOriginalQuantity(),
                proposed ? amendment.getProposedAdults() : amendment.getOriginalAdults(),
                proposed ? amendment.getProposedChildren() : amendment.getOriginalChildren(),
                amount(proposed ? amendment.getProposedTotal() : amendment.getOriginalTotal()),
                amount(proposed ? amendment.getProposedDeposit() : amendment.getOriginalDeposit()));
    }

    private StaySnapshot snapshot(
            Reservation reservation,
            ReservationDetail detail,
            BigDecimal total,
            BigDecimal deposit) {
        return new StaySnapshot(
                detail.getRoomType().getId(),
                roomTypeName(detail.getRoomType()),
                reservation.getCheckInDate(),
                reservation.getCheckOutDate(),
                quantity(detail),
                adults(detail),
                children(detail),
                amount(total),
                amount(deposit));
    }

    private RoomTypeOption roomTypeOption(RoomType roomType) {
        int maxGuests = firstPositive(roomType.getMaxGuests(), roomType.getMaxGuest());
        int maxAdults = firstPositive(roomType.getMaxAdults(), roomType.getMaxGuests(), roomType.getMaxGuest());
        int maxChildren = firstNonNegative(roomType.getMaxChildren(), roomType.getMaxGuests(), roomType.getMaxGuest());
        return new RoomTypeOption(
                roomType.getId(), roomTypeName(roomType), maxAdults, maxChildren, maxGuests);
    }

    private List<String> configuredPaymentMethods(Long hotelId) {
        PropertyPaymentConfiguration configuration = paymentConfigurationRepository.findByHotelId(hotelId).orElse(null);
        if (configuration == null || !configuration.isEnabled()) {
            return List.of();
        }
        return configuration.getMethods().stream()
                .filter(PropertyPaymentConfigurationMethod::isEnabled)
                .map(PropertyPaymentConfigurationMethod::getMethod)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
    }

    private User authorize(Reservation reservation, AccessMode accessMode) {
        User actor = propertyAccessService.currentUser();
        boolean owner = reservation.getUser() != null
                && Objects.equals(reservation.getUser().getId(), actor.getId());
        if (accessMode == AccessMode.CUSTOMER) {
            if (!owner) {
                throw new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND);
            }
            return actor;
        }
        if (!propertyAccessService.isSystemAdministrator()
                && !propertyAccessService.accessibleHotelIds().contains(reservation.getHotel().getId())) {
            throw new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND);
        }
        return actor;
    }

    private Reservation lockedReservation(Long reservationId) {
        if (reservationId == null) {
            throw new IllegalArgumentException("reservationId is required.");
        }
        return reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
    }

    private Reservation findReservation(Long reservationId) {
        if (reservationId == null) {
            throw new IllegalArgumentException("reservationId is required.");
        }
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
    }

    private ReservationAmendment lockedQuote(Long reservationId, String quotePublicId) {
        ReservationAmendment amendment = amendmentRepository.findByPublicIdForUpdate(
                        requireText(quotePublicId, "quoteId"))
                .orElseThrow(() -> new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND));
        requireQuoteReservation(amendment, reservationId);
        return amendment;
    }

    private void requireQuoteReservation(ReservationAmendment amendment, Long reservationId) {
        if (amendment.getReservation() == null
                || !Objects.equals(amendment.getReservation().getId(), reservationId)) {
            throw new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private ReservationDetail requireSingleDetail(Long reservationId) {
        List<ReservationDetail> details = detailRepository.findByReservationId(reservationId);
        if (details.size() != 1 || details.get(0).getRoomType() == null) {
            throw new FinancialException(
                    FinancialErrorCode.POLICY_NOT_CONFIGURED,
                    "Reservation amendment currently requires one authoritative room-type detail.");
        }
        return details.get(0);
    }

    private Map<Long, RoomType> lockRoomTypes(Long firstId, Long secondId) {
        Set<Long> ids = new java.util.TreeSet<>();
        ids.add(Objects.requireNonNull(firstId, "original room type is required"));
        ids.add(Objects.requireNonNull(secondId, "proposed room type is required"));
        Map<Long, RoomType> locked = new LinkedHashMap<>();
        roomTypeRepository.findAllByIdForUpdate(ids).forEach(item -> locked.put(item.getId(), item));
        if (locked.size() != ids.size()) {
            throw new FinancialException(FinancialErrorCode.RESOURCE_NOT_FOUND);
        }
        return locked;
    }

    private void validateQuoteRequest(QuoteRequest request) {
        if (request == null
                || request.proposedRoomTypeId() == null
                || request.proposedCheckInDate() == null
                || request.proposedCheckOutDate() == null) {
            throw new IllegalArgumentException("Proposed room type and stay dates are required.");
        }
        if (request.proposedQuantity() < 1
                || request.proposedAdults() < 1
                || request.proposedChildren() < 0) {
            throw new IllegalArgumentException("Proposed room quantity and guest counts are invalid.");
        }
    }

    private boolean structuralChange(
            Reservation reservation,
            ReservationDetail detail,
            QuoteRequest request) {
        return !Objects.equals(detail.getRoomType().getId(), request.proposedRoomTypeId())
                || !Objects.equals(reservation.getCheckInDate(), request.proposedCheckInDate())
                || !Objects.equals(reservation.getCheckOutDate(), request.proposedCheckOutDate())
                || quantity(detail) != request.proposedQuantity();
    }

    private boolean sameInventoryWindow(
            Reservation reservation,
            ReservationDetail detail,
            QuoteRequest request) {
        return Objects.equals(detail.getRoomType().getId(), request.proposedRoomTypeId())
                && Objects.equals(reservation.getCheckInDate(), request.proposedCheckInDate())
                && Objects.equals(reservation.getCheckOutDate(), request.proposedCheckOutDate());
    }

    private DepositPolicySnapshot requireDepositSnapshot(Reservation reservation) {
        DepositPolicySnapshot snapshot = reservation.getDepositPolicySnapshot();
        if (snapshot == null) {
            throw new FinancialException(
                    FinancialErrorCode.POLICY_NOT_CONFIGURED,
                    "The reservation has no snapshotted deposit policy.");
        }
        return snapshot;
    }

    private BigDecimal depositRequired(Reservation reservation) {
        return reservation.getDepositRequired() == null
                ? BigDecimal.ZERO
                : VndMoney.of(reservation.getDepositRequired()).amount();
    }

    private int quantity(ReservationDetail detail) {
        return detail.getQuantity() == null ? 1 : detail.getQuantity();
    }

    private int adults(ReservationDetail detail) {
        return detail.getAdults() == null
                ? Math.max(1, detail.getReservation().getGuests() == null ? 1 : detail.getReservation().getGuests())
                : detail.getAdults();
    }

    private int children(ReservationDetail detail) {
        return detail.getChildren() == null ? 0 : detail.getChildren();
    }

    private BigDecimal money(BigDecimal value, String field) {
        if (value == null) {
            throw new FinancialException(FinancialErrorCode.INVALID_AMOUNT, field + " is required.");
        }
        return VndMoney.of(value).amount();
    }

    private MoneyResponse amount(BigDecimal value) {
        return new MoneyResponse(VndMoney.of(value).amount(), "VND");
    }

    private MoneyResponse signedAmount(BigDecimal value) {
        if (value == null) {
            throw new FinancialException(FinancialErrorCode.INVALID_AMOUNT);
        }
        BigDecimal normalized = VndMoney.of(value.abs()).amount();
        return new MoneyResponse(value.signum() < 0 ? normalized.negate() : normalized, "VND");
    }

    private String utc(LocalDateTime value) {
        return value.toInstant(ZoneOffset.UTC).toString();
    }

    private String roomTypeName(RoomType roomType) {
        return roomType.getNameVi() == null || roomType.getNameVi().isBlank()
                ? roomType.getNameEn()
                : roomType.getNameVi();
    }

    private int firstPositive(Integer... values) {
        for (Integer value : values) {
            if (value != null && value > 0) {
                return value;
            }
        }
        return Integer.MAX_VALUE;
    }

    private int firstNonNegative(Integer... values) {
        for (Integer value : values) {
            if (value != null && value >= 0) {
                return value;
            }
        }
        return Integer.MAX_VALUE;
    }

    private String normalizeKey(String value) {
        String normalized = requireText(value, "Idempotency-Key");
        if (normalized.length() > 160) {
            throw new IllegalArgumentException("Idempotency-Key is too long.");
        }
        return normalized;
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return value.trim();
    }

    private String quoteIdentity(Long reservationId, QuoteRequest request) {
        return String.join("|",
                String.valueOf(reservationId),
                String.valueOf(request.proposedRoomTypeId()),
                request.proposedCheckInDate().toString(),
                request.proposedCheckOutDate().toString(),
                String.valueOf(request.proposedQuantity()),
                String.valueOf(request.proposedAdults()),
                String.valueOf(request.proposedChildren()));
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private void verifyHash(String existing, String candidate) {
        if (existing == null || !MessageDigest.isEqual(
                existing.getBytes(StandardCharsets.UTF_8), candidate.getBytes(StandardCharsets.UTF_8))) {
            throw new FinancialException(FinancialErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
    }

    private void appendAudit(
            ReservationAmendment amendment,
            String eventType,
            String reason,
            Object beforeState,
            Object afterState,
            String correlationId) {
        auditService.append(new OperationalAuditService.AuditCommand(
                "TENANT",
                amendment.getHotel().getId(),
                "STAY",
                eventType,
                "RESERVATION",
                amendment.getReservation().getId().toString(),
                "USER",
                amendment.getActor() == null ? null : amendment.getActor().getId(),
                reason,
                beforeState,
                afterState,
                correlationId));
    }

    private Map<String, Object> originalState(ReservationAmendment amendment) {
        return state(
                amendment.getOriginalRoomType().getId(),
                amendment.getOriginalCheckIn(),
                amendment.getOriginalCheckOut(),
                amendment.getOriginalQuantity(),
                amendment.getOriginalAdults(),
                amendment.getOriginalChildren(),
                amendment.getOriginalTotal(),
                amendment.getOriginalDeposit());
    }

    private Map<String, Object> proposedState(ReservationAmendment amendment) {
        return state(
                amendment.getProposedRoomType().getId(),
                amendment.getProposedCheckIn(),
                amendment.getProposedCheckOut(),
                amendment.getProposedQuantity(),
                amendment.getProposedAdults(),
                amendment.getProposedChildren(),
                amendment.getProposedTotal(),
                amendment.getProposedDeposit());
    }

    private Map<String, Object> currentReservationState(Reservation reservation, ReservationDetail detail) {
        return state(
                detail.getRoomType().getId(),
                reservation.getCheckInDate(),
                reservation.getCheckOutDate(),
                quantity(detail),
                adults(detail),
                children(detail),
                reservation.getTotalAmount(),
                depositRequired(reservation));
    }

    private Map<String, Object> state(
            Long roomTypeId,
            LocalDate checkIn,
            LocalDate checkOut,
            int quantity,
            int adults,
            int children,
            BigDecimal total,
            BigDecimal deposit) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("roomTypeId", roomTypeId);
        state.put("checkInDate", checkIn);
        state.put("checkOutDate", checkOut);
        state.put("quantity", quantity);
        state.put("adults", adults);
        state.put("children", children);
        state.put("totalAmount", total);
        state.put("depositRequired", deposit);
        return state;
    }

    public record QuoteRequest(
            Long proposedRoomTypeId,
            LocalDate proposedCheckInDate,
            LocalDate proposedCheckOutDate,
            int proposedQuantity,
            int proposedAdults,
            int proposedChildren) {
    }

    public record ContextResponse(
            Long reservationId,
            boolean allowed,
            String blockedReason,
            String cutoffAt,
            String policyVersion,
            StaySnapshot current,
            List<RoomTypeOption> roomTypeOptions,
            List<String> paymentMethods) {
    }

    public record QuoteResponse(
            String publicId,
            Long reservationId,
            ReservationAmendment.Status status,
            String policyVersion,
            StaySnapshot original,
            StaySnapshot proposed,
            MoneyResponse priceDelta,
            MoneyResponse preservedDiscount,
            String expiresAt,
            String cutoffAt,
            SettlementResponse settlement,
            boolean replayed) {
    }

    public record StaySnapshot(
            Long roomTypeId,
            String roomTypeName,
            LocalDate checkInDate,
            LocalDate checkOutDate,
            int quantity,
            int adults,
            int children,
            MoneyResponse totalAmount,
            MoneyResponse depositRequired) {
    }

    public record RoomTypeOption(
            Long id,
            String name,
            int maxAdults,
            int maxChildren,
            int maxGuests) {
    }

    public record MoneyResponse(BigDecimal amount, String currency) {
    }

    public record SettlementResponse(
            String type,
            MoneyResponse amount,
            PaymentAttemptResponse paymentAttempt,
            String refundRequestPublicId) {
    }

    public record PaymentAttemptResponse(
            String attemptId,
            PropertyPaymentAttempt.Purpose purpose,
            PaymentState status,
            MoneyResponse expectedAmount,
            String expiresAt,
            String method,
            String provider,
            String uniqueTransferContent) {
    }
}
