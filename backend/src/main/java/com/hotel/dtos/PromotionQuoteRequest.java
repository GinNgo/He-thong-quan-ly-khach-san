package com.hotel.dtos;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record PromotionQuoteRequest(
        @NotNull Long propertyId,
        @NotNull Long roomTypeId,
        @NotNull @FutureOrPresent LocalDate checkInDate,
        @NotNull LocalDate checkOutDate,
        @Min(1) @Max(20) int quantity,
        @Min(1) @Max(40) int adultCount,
        @Min(0) @Max(20) int childCount,
        @Size(max = 80) String couponCode) {
}

