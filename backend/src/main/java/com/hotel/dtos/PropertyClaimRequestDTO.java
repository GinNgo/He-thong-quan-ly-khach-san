package com.hotel.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;
import java.util.Set;

public record PropertyClaimRequestDTO(
        @NotBlank(message = "Verification method is required.")
        @Pattern(
                regexp = "EMAIL|PHONE|BUSINESS_LICENSE",
                message = "Verification method must be EMAIL, PHONE or BUSINESS_LICENSE.")
        String verificationMethod,

        @NotBlank(message = "Verification data is required.")
        @Size(max = 1000, message = "Verification data must not exceed 1000 characters.")
        String verificationData,

        @Size(max = 500, message = "Note must not exceed 500 characters.")
        String note) {

    private static final Set<String> ALLOWED_METHODS =
            Set.of("EMAIL", "PHONE", "BUSINESS_LICENSE");

    public PropertyClaimRequestDTO {
        verificationMethod = normalizeMethod(verificationMethod);
        verificationData = normalizeRequiredText(verificationData);
        note = normalizeOptionalText(note);
    }

    public PropertyClaimRequestDTO requireValid() {
        if (verificationMethod == null || !ALLOWED_METHODS.contains(verificationMethod)) {
            throw new IllegalArgumentException(
                    "Verification method must be EMAIL, PHONE or BUSINESS_LICENSE.");
        }
        if (verificationData == null || verificationData.isBlank()) {
            throw new IllegalArgumentException("Verification data is required.");
        }
        if (verificationData.length() > 1000) {
            throw new IllegalArgumentException("Verification data must not exceed 1000 characters.");
        }
        if (note != null && note.length() > 500) {
            throw new IllegalArgumentException("Note must not exceed 500 characters.");
        }
        return this;
    }

    private static String normalizeMethod(String value) {
        return value == null ? null : value.strip().toUpperCase(Locale.ROOT);
    }

    private static String normalizeRequiredText(String value) {
        return value == null ? null : value.strip();
    }

    private static String normalizeOptionalText(String value) {
        if (value == null) return null;
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
