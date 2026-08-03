package com.hotel.services.social;

import com.hotel.entities.SocialProvider;

public record ExternalIdentityProfile(
        SocialProvider provider,
        String subject,
        String email,
        String displayName,
        String avatarUrl) {
}
