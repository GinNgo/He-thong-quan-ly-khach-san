package com.hotel.dtos;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class ReservationRequest {

    @NotNull(message = "Room type is required.")
    @Positive(message = "Room type must be a positive identifier.")
    private Long roomTypeId;
    @NotNull(message = "Check-in date is required.")
    private LocalDate checkInDate;
    @NotNull(message = "Check-out date is required.")
    private LocalDate checkOutDate;
    @Positive(message = "Guests must be greater than zero.")
    private Integer guests;
    @Positive(message = "Quantity must be greater than zero.")
    private Integer quantity = 1;
    @Positive(message = "Adults must be greater than zero.")
    private Integer adults;
    @PositiveOrZero(message = "Children must not be negative.")
    private Integer children;
    @Size(max = 2000, message = "Special requests must not exceed 2000 characters.")
    private String specialRequests;
    @Size(max = 50, message = "Payment method must not exceed 50 characters.")
    private String paymentMethod;
    @Size(max = 100, message = "First name must not exceed 100 characters.")
    private String firstName;
    @Size(max = 100, message = "Last name must not exceed 100 characters.")
    private String lastName;
    @Size(max = 30, message = "Phone must not exceed 30 characters.")
    private String phone;
<<<<<<< HEAD
    private Long operationalPolicyVersion;

    @AssertTrue(message = "Check-out date must be after check-in date.")
    public boolean isStayRangeValid() {
        return checkInDate == null || checkOutDate == null || checkOutDate.isAfter(checkInDate);
    }
=======
    private String couponCode;
>>>>>>> codex/ui-functional-audit-polish

    // Getters and Setters omitted for brevity
    public Long getRoomTypeId() { return roomTypeId; }
    public void setRoomTypeId(Long roomTypeId) { this.roomTypeId = roomTypeId; }

    public LocalDate getCheckInDate() { return checkInDate; }
    public void setCheckInDate(LocalDate checkInDate) { this.checkInDate = checkInDate; }

    public LocalDate getCheckOutDate() { return checkOutDate; }
    public void setCheckOutDate(LocalDate checkOutDate) { this.checkOutDate = checkOutDate; }

    public Integer getGuests() { return guests; }
    public void setGuests(Integer guests) { this.guests = guests; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Integer getAdults() { return adults; }
    public void setAdults(Integer adults) { this.adults = adults; }

    public Integer getChildren() { return children; }
    public void setChildren(Integer children) { this.children = children; }

    public String getSpecialRequests() { return specialRequests; }
    public void setSpecialRequests(String specialRequests) { this.specialRequests = specialRequests; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

<<<<<<< HEAD
    public Long getOperationalPolicyVersion() { return operationalPolicyVersion; }
    public void setOperationalPolicyVersion(Long operationalPolicyVersion) { this.operationalPolicyVersion = operationalPolicyVersion; }
=======
    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }
>>>>>>> codex/ui-functional-audit-polish
}
