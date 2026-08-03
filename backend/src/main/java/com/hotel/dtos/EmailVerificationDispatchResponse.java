package com.hotel.dtos;

public record EmailVerificationDispatchResponse(
        String message,
        boolean emailSent,
        boolean alreadyVerified,
        String pendingEmail) {
}
