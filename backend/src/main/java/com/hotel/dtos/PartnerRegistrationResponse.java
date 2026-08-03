package com.hotel.dtos;

public record PartnerRegistrationResponse(
        Long userId,
        Long propertyId,
        String status) {
}
