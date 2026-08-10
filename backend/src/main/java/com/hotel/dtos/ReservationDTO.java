package com.hotel.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class ReservationDTO {
    private Long id;
    private Long userId;
    private String username;
    private String userFullName;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private Integer guests;
    private BigDecimal totalAmount;
    private String status;
    private String paymentMethod;
    private String specialRequests;
    private String cancellationReasonCode;
    private String cancellationReason;
    private LocalDateTime cancelledAt;
    private List<ReservationDetailDTO> details;
    private PaymentLifecycleSummaryDTO payment;
    private List<RefundSummaryDTO> refunds;
    private PromotionQuoteDTO quote;
    private PropertyContactDTO property;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUserFullName() {
        return userFullName;
    }

    public void setUserFullName(String userFullName) {
        this.userFullName = userFullName;
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

    public String getCancellationReasonCode() { return cancellationReasonCode; }
    public void setCancellationReasonCode(String cancellationReasonCode) { this.cancellationReasonCode = cancellationReasonCode; }
    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }

    public List<ReservationDetailDTO> getDetails() {
        return details;
    }

    public void setDetails(List<ReservationDetailDTO> details) {
        this.details = details;
    }

    public PaymentLifecycleSummaryDTO getPayment() {
        return payment;
    }

    public void setPayment(PaymentLifecycleSummaryDTO payment) {
        this.payment = payment;
    }

    public List<RefundSummaryDTO> getRefunds() {
        return refunds;
    }

    public void setRefunds(List<RefundSummaryDTO> refunds) {
        this.refunds = refunds;
    }

    public PromotionQuoteDTO getQuote() {
        return quote;
    }

    public void setQuote(PromotionQuoteDTO quote) {
        this.quote = quote;
    }

    public PropertyContactDTO getProperty() { return property; }
    public void setProperty(PropertyContactDTO property) { this.property = property; }

    public static class PropertyContactDTO {
        private Long id;
        private String name;
        private String address;
        private String phone;
        private String email;
        private String contactName;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getContactName() { return contactName; }
        public void setContactName(String contactName) { this.contactName = contactName; }
    }
}
