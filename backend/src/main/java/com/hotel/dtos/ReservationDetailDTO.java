package com.hotel.dtos;

import java.math.BigDecimal;
import java.util.List;

public class ReservationDetailDTO {
    private Long id;
    private Long reservationId;
    private Long roomId;
    private String roomNumber;
    private BigDecimal priceAtBooking;
    private Long roomTypeId;
    private String roomTypeName;
    private Integer quantity;
    private Integer adults;
    private Integer children;
    private BigDecimal subtotal;
    private List<Long> assignedRoomIds;
    private List<String> assignedRoomNumbers;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public void setReservationId(Long reservationId) {
        this.reservationId = reservationId;
    }

    public Long getRoomId() {
        return roomId;
    }

    public void setRoomId(Long roomId) {
        this.roomId = roomId;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(String roomNumber) {
        this.roomNumber = roomNumber;
    }

    public BigDecimal getPriceAtBooking() {
        return priceAtBooking;
    }

    public void setPriceAtBooking(BigDecimal priceAtBooking) {
        this.priceAtBooking = priceAtBooking;
    }

    public Long getRoomTypeId() { return roomTypeId; }
    public void setRoomTypeId(Long roomTypeId) { this.roomTypeId = roomTypeId; }
    public String getRoomTypeName() { return roomTypeName; }
    public void setRoomTypeName(String roomTypeName) { this.roomTypeName = roomTypeName; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Integer getAdults() { return adults; }
    public void setAdults(Integer adults) { this.adults = adults; }
    public Integer getChildren() { return children; }
    public void setChildren(Integer children) { this.children = children; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public List<Long> getAssignedRoomIds() { return assignedRoomIds; }
    public void setAssignedRoomIds(List<Long> assignedRoomIds) { this.assignedRoomIds = assignedRoomIds; }
    public List<String> getAssignedRoomNumbers() { return assignedRoomNumbers; }
    public void setAssignedRoomNumbers(List<String> assignedRoomNumbers) { this.assignedRoomNumbers = assignedRoomNumbers; }
}
