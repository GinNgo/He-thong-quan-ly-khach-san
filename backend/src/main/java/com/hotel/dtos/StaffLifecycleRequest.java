package com.hotel.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public class StaffLifecycleRequest {
    @NotNull(message = "Property is required")
    @Positive(message = "Property is invalid")
    private Long hotelId;

    @NotBlank(message = "Lifecycle reason is required")
    @Size(min = 3, max = 500, message = "Lifecycle reason must contain between 3 and 500 characters")
    private String reason;

    @NotNull(message = "Expected version is required")
    @PositiveOrZero(message = "Expected version is invalid")
    private Long expectedVersion;

    public Long getHotelId() {
        return hotelId;
    }

    public void setHotelId(Long hotelId) {
        this.hotelId = hotelId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(Long expectedVersion) {
        this.expectedVersion = expectedVersion;
    }
}
