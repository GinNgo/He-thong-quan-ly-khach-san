package com.hotel.propertycommerce.booking;

import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.paymentprovider.domain.FinancialStates.BookingFinancialState;
import com.hotel.paymentprovider.domain.VndMoney;
import com.hotel.propertycommerce.payment.PropertyFinancialTransaction;
import com.hotel.propertycommerce.payment.PropertyFinancialTransaction.Direction;
import com.hotel.propertycommerce.payment.PropertyFinancialTransaction.TransactionType;
import com.hotel.propertycommerce.payment.PropertyFinancialTransactionRepository;
import com.hotel.repositories.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class BookingFinancialSummaryService {

    private final ReservationRepository reservationRepository;
    private final PropertyFinancialTransactionRepository transactionRepository;
    private final BookingFinancialSummaryRepository summaryRepository;

    public BookingFinancialSummaryService(
            ReservationRepository reservationRepository,
            PropertyFinancialTransactionRepository transactionRepository,
            BookingFinancialSummaryRepository summaryRepository) {
        this.reservationRepository = reservationRepository;
        this.transactionRepository = transactionRepository;
        this.summaryRepository = summaryRepository;
    }

    @Transactional(readOnly = true)
    public Summary calculate(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation was not found."));
        return calculate(reservation,
                transactionRepository.findByReservationIdOrderByOccurredAtAsc(reservationId));
    }

    @Transactional
    public Summary refresh(Long reservationId) {
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation was not found."));
        Summary calculated = calculate(reservation,
                transactionRepository.findByReservationIdOrderByOccurredAtAsc(reservationId));
        BookingFinancialSummary projection = summaryRepository.findByReservationIdForUpdate(reservationId)
                .orElseGet(() -> new BookingFinancialSummary(reservation, reservation.getHotel()));
        projection.replaceWith(calculated);
        summaryRepository.save(projection);
        return calculated;
    }

    Summary calculate(Reservation reservation, List<PropertyFinancialTransaction> transactions) {
        Objects.requireNonNull(reservation, "reservation must not be null");
        Objects.requireNonNull(reservation.getHotel(), "reservation hotel must not be null");
        List<PropertyFinancialTransaction> evidence = transactions == null ? List.of() : List.copyOf(transactions);

        VndMoney grossCharges = VndMoney.of(authoritativeBookingTotal(reservation));
        VndMoney depositRequired = VndMoney.of(
                reservation.getDepositRequired() == null ? BigDecimal.ZERO : reservation.getDepositRequired());
        if (depositRequired.amount().compareTo(grossCharges.amount()) > 0) {
            throw new IllegalStateException("Deposit requirement cannot exceed gross booking charges.");
        }

        BigDecimal paymentTotal = BigDecimal.ZERO;
        BigDecimal refundTotal = BigDecimal.ZERO;
        BigDecimal otherCredits = BigDecimal.ZERO;
        long sourceVersion = 0;
        for (PropertyFinancialTransaction transaction : evidence) {
            validateOwnership(reservation, transaction);
            BigDecimal amount = transaction.money().amount();
            if (transaction.getTransactionType() == TransactionType.REFUND) {
                refundTotal = refundTotal.add(amount);
            } else if (transaction.getDirection() == Direction.DEBIT) {
                paymentTotal = paymentTotal.add(amount);
            } else {
                otherCredits = otherCredits.add(amount);
            }
            if (transaction.getId() != null) {
                sourceVersion = Math.max(sourceVersion, transaction.getId());
            }
        }
        if (sourceVersion == 0 && !evidence.isEmpty()) {
            sourceVersion = evidence.size();
        }

        BigDecimal totalCredits = refundTotal.add(otherCredits);
        if (totalCredits.compareTo(paymentTotal) > 0) {
            throw new IllegalStateException("Ledger credits cannot exceed successful property payments.");
        }
        BigDecimal netSettled = paymentTotal.subtract(totalCredits);
        BigDecimal remainingBalance = grossCharges.amount().subtract(netSettled);
        BookingFinancialState state = state(
                grossCharges.amount(),
                depositRequired.amount(),
                paymentTotal,
                refundTotal,
                netSettled);

        return new Summary(
                reservation.getId(),
                reservation.getHotel().getId(),
                grossCharges,
                depositRequired,
                VndMoney.of(paymentTotal),
                VndMoney.of(refundTotal),
                remainingBalance,
                state,
                sourceVersion,
                LocalDateTime.now());
    }

    private BigDecimal authoritativeBookingTotal(Reservation reservation) {
        BigDecimal amount = reservation.getDepositBookingTotal() != null
                ? reservation.getDepositBookingTotal()
                : reservation.getTotalAmount();
        if (amount == null || amount.signum() < 0) {
            throw new IllegalStateException("Reservation has no valid server-owned booking total.");
        }
        return amount;
    }

    private void validateOwnership(Reservation reservation, PropertyFinancialTransaction transaction) {
        if (transaction == null || transaction.getHotel() == null) {
            throw new IllegalArgumentException("Financial transaction ownership is required.");
        }
        if (!sameHotel(reservation.getHotel(), transaction.getHotel())) {
            throw new IllegalArgumentException("Financial transaction belongs to another property.");
        }
        if (transaction.getReservation() != null && !sameReservation(reservation, transaction.getReservation())) {
            throw new IllegalArgumentException("Financial transaction belongs to another reservation.");
        }
    }

    private BookingFinancialState state(
            BigDecimal grossCharges,
            BigDecimal depositRequired,
            BigDecimal payments,
            BigDecimal refunds,
            BigDecimal netSettled) {
        if (refunds.signum() > 0) {
            return refunds.compareTo(payments) == 0
                    ? BookingFinancialState.REFUNDED
                    : BookingFinancialState.PARTIALLY_REFUNDED;
        }
        if (netSettled.signum() == 0) {
            return BookingFinancialState.UNPAID;
        }
        int grossComparison = netSettled.compareTo(grossCharges);
        if (grossComparison > 0) {
            return BookingFinancialState.OVERPAID;
        }
        if (grossComparison == 0) {
            return BookingFinancialState.PAID;
        }
        if (depositRequired.signum() > 0 && netSettled.compareTo(depositRequired) >= 0) {
            return BookingFinancialState.DEPOSIT_PAID;
        }
        return BookingFinancialState.PARTIALLY_PAID;
    }

    private boolean sameHotel(Hotel left, Hotel right) {
        if (left.getId() != null && right.getId() != null) {
            return left.getId().equals(right.getId());
        }
        return left == right;
    }

    private boolean sameReservation(Reservation left, Reservation right) {
        if (left.getId() != null && right.getId() != null) {
            return left.getId().equals(right.getId());
        }
        return left == right;
    }

    public record Summary(
            Long reservationId,
            Long hotelId,
            VndMoney grossCharges,
            VndMoney depositRequired,
            VndMoney successfulPayments,
            VndMoney successfulRefunds,
            BigDecimal remainingBalance,
            BookingFinancialState financialState,
            long sourceVersion,
            LocalDateTime calculatedAt) {
    }
}
