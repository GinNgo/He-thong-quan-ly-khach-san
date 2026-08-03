package com.hotel.dtos;

public record EmailVerificationResultResponse(
        String message,
        boolean emailChanged,
        String email) {
}
