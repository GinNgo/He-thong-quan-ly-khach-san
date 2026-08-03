package com.hotel.dtos;

public record RegistrationResponse(
        String message,
        boolean welcomeEmailSent,
        boolean verificationEmailSent) {

    public RegistrationResponse(String message, boolean welcomeEmailSent) {
        this(message, welcomeEmailSent, false);
    }
}
