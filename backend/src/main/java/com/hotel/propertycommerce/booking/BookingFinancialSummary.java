package com.hotel.propertycommerce.booking;

import com.hotel.entities.Hotel;
import com.hotel.entities.Reservation;
import com.hotel.paymentprovider.domain.FinancialStates.BookingFinancialState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "booking_financial_summaries")
@FilterDef(name = "bookingFinancialSummaryTenantFilter", parameters = @ParamDef(name = "hotelId", type = Long.class))
@Filter(name = "bookingFinancialSummaryTenantFilter", condition = "hotel_id = :hotelId")
public class BookingFinancialSummary implements Persistable<Long> {

    @Id
    @Column(name = "reservation_id", nullable = false, updatable = false)
    private Long reservationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, insertable = false, updatable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id", nullable = false, updatable = false)
    private Hotel hotel;

    @Column(name = "gross_charges", nullable = false, precision = 19, scale = 0)
    private BigDecimal grossCharges;

    @Column(name = "deposit_required", nullable = false, precision = 19, scale = 0)
    private BigDecimal depositRequired;

    @Column(name = "successful_payments", nullable = false, precision = 19, scale = 0)
    private BigDecimal successfulPayments;

    @Column(name = "successful_refunds", nullable = false, precision = 19, scale = 0)
    private BigDecimal successfulRefunds;

    @Column(name = "remaining_balance", nullable = false, precision = 19, scale = 0)
    private BigDecimal remainingBalance;

    @Enumerated(EnumType.STRING)
    @Column(name = "financial_state", nullable = false, length = 30)
    private BookingFinancialState financialState;

    @Column(name = "source_version", nullable = false)
    private long sourceVersion;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    @Transient
    private boolean newRecord = true;

    protected BookingFinancialSummary() {
    }

    BookingFinancialSummary(Reservation reservation, Hotel hotel) {
        this.reservation = reservation;
        this.reservationId = reservation.getId();
        this.hotel = hotel;
    }

    void replaceWith(BookingFinancialSummaryService.Summary summary) {
        if (!reservationId.equals(summary.reservationId()) || !hotel.getId().equals(summary.hotelId())) {
            throw new IllegalArgumentException("Financial summary ownership cannot be changed.");
        }
        grossCharges = summary.grossCharges().amount();
        depositRequired = summary.depositRequired().amount();
        successfulPayments = summary.successfulPayments().amount();
        successfulRefunds = summary.successfulRefunds().amount();
        remainingBalance = summary.remainingBalance();
        financialState = summary.financialState();
        sourceVersion = summary.sourceVersion();
        calculatedAt = summary.calculatedAt();
    }

    @Override
    public Long getId() {
        return reservationId;
    }

    @Override
    public boolean isNew() {
        return newRecord;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        newRecord = false;
    }
}
