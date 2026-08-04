package com.hotel.entities;

import com.hotel.propertycommerce.booking.DepositPolicySnapshot;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "reservations")
@org.hibernate.annotations.FilterDef(name = "reservationTenantFilter", parameters = @org.hibernate.annotations.ParamDef(name = "hotelId", type = Long.class))
@org.hibernate.annotations.Filter(name = "reservationTenantFilter", condition = "hotel_id = :hotelId")
public class Reservation extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @Column(name = "check_out_date", nullable = false)
    private LocalDate checkOutDate;

    private Integer guests;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private String status; // Canonical values are defined by ReservationStatus.

    @Column(name = "payment_method")
    private String paymentMethod; // CREDIT_CARD, PAYPAL, APPLE_PAY

    @Column(name = "special_requests", columnDefinition = "nvarchar(max)")
    private String specialRequests;

    @Column(name = "deposit_configuration_id")
    private Long depositConfigurationId;

    @Column(name = "deposit_configuration_version")
    private Long depositConfigurationVersion;

    @Column(name = "deposit_policy_type", length = 20)
    private String depositPolicyType;

    @Column(name = "deposit_policy_value", precision = 19, scale = 0)
    private BigDecimal depositPolicyValue;

    @Column(name = "deposit_booking_total", precision = 19, scale = 0)
    private BigDecimal depositBookingTotal;

    @Column(name = "deposit_required", precision = 19, scale = 0)
    private BigDecimal depositRequired;

    @Column(name = "deposit_currency", length = 3)
    private String depositCurrency;

    /**
     * Booking-level identity lets a retry recover a reservation if the
     * response was lost after the business transaction committed.
     */
    @Column(name = "booking_idempotency_scope", length = 160)
    private String bookingIdempotencyScope;

    @Column(name = "booking_idempotency_key", length = 160)
    private String bookingIdempotencyKey;



    // Getters and Setters omitted for brevity

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Room getRoom() {
        return room;
    }

    public void setRoom(Room room) {
        this.room = room;
    }

    public Hotel getHotel() {
        return hotel;
    }

    public void setHotel(Hotel hotel) {
        this.hotel = hotel;
    }

    public LocalDate getCheckInDate() {
        return checkInDate;
    }

    public void setCheckInDate(LocalDate checkInDate) {
        this.checkInDate = checkInDate;
    }

    public LocalDate getCheckOutDate() {
        return checkOutDate;
    }

    public void setCheckOutDate(LocalDate checkOutDate) {
        this.checkOutDate = checkOutDate;
    }

    public Integer getGuests() {
        return guests;
    }

    public void setGuests(Integer guests) {
        this.guests = guests;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getSpecialRequests() {
        return specialRequests;
    }

    public void setSpecialRequests(String specialRequests) {
        this.specialRequests = specialRequests;
    }

    public void captureDepositPolicy(DepositPolicySnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Deposit policy snapshot is required.");
        }
        if (hotel == null || hotel.getId() == null || !hotel.getId().equals(snapshot.propertyId())) {
            throw new IllegalArgumentException("Deposit policy must belong to the reservation property.");
        }
        if (depositPolicyType != null) {
            throw new IllegalStateException("Deposit policy snapshot is immutable once captured.");
        }
        depositConfigurationId = snapshot.configurationId();
        depositConfigurationVersion = snapshot.configurationVersion();
        depositPolicyType = snapshot.policyType().name();
        depositPolicyValue = snapshot.policyValue();
        depositBookingTotal = snapshot.bookingTotal().amount();
        depositRequired = snapshot.requiredDeposit().amount();
        depositCurrency = snapshot.currency();
    }

    public DepositPolicySnapshot getDepositPolicySnapshot() {
        if (depositPolicyType == null) {
            return null;
        }
        return new DepositPolicySnapshot(
                hotel.getId(),
                depositConfigurationId,
                depositConfigurationVersion,
                DepositPolicySnapshot.PolicyType.from(depositPolicyType),
                depositPolicyValue,
                com.hotel.paymentprovider.domain.VndMoney.of(depositBookingTotal),
                com.hotel.paymentprovider.domain.VndMoney.of(depositRequired));
    }

    public void applyAmendedDepositPolicy(DepositPolicySnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Deposit policy snapshot is required.");
        }
        if (depositPolicyType == null) {
            throw new IllegalStateException("The reservation has no deposit policy to amend.");
        }
        boolean samePolicy = hotel != null
                && hotel.getId() != null
                && hotel.getId().equals(snapshot.propertyId())
                && java.util.Objects.equals(depositConfigurationId, snapshot.configurationId())
                && java.util.Objects.equals(depositConfigurationVersion, snapshot.configurationVersion())
                && depositPolicyType.equals(snapshot.policyType().name())
                && java.util.Objects.equals(depositPolicyValue, snapshot.policyValue());
        if (!samePolicy) {
            throw new IllegalArgumentException("Reservation amendments must preserve the original deposit policy identity.");
        }
        depositBookingTotal = snapshot.bookingTotal().amount();
        depositRequired = snapshot.requiredDeposit().amount();
        depositCurrency = snapshot.currency();
    }

    public Long getDepositConfigurationId() { return depositConfigurationId; }
    public Long getDepositConfigurationVersion() { return depositConfigurationVersion; }
    public String getDepositPolicyType() { return depositPolicyType; }
    public BigDecimal getDepositPolicyValue() { return depositPolicyValue; }
    public BigDecimal getDepositBookingTotal() { return depositBookingTotal; }
    public BigDecimal getDepositRequired() { return depositRequired; }
    public String getDepositCurrency() { return depositCurrency; }

    public String getBookingIdempotencyScope() { return bookingIdempotencyScope; }
    public void setBookingIdempotencyScope(String bookingIdempotencyScope) {
        this.bookingIdempotencyScope = bookingIdempotencyScope;
    }

    public String getBookingIdempotencyKey() { return bookingIdempotencyKey; }
    public void setBookingIdempotencyKey(String bookingIdempotencyKey) {
        this.bookingIdempotencyKey = bookingIdempotencyKey;
    }
}
