package com.hotel.dtos;

import com.hotel.entities.SocialProvider;

import java.time.LocalDateTime;

public record SocialIdentityResponse(
        SocialProvider provider,
        String providerEmail,
        LocalDateTime linkedAt,
        LocalDateTime lastLoginAt,
        boolean passwordRequiredToUnlink) {
}
