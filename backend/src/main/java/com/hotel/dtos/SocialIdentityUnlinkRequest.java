package com.hotel.dtos;

import jakarta.validation.constraints.Size;

public class SocialIdentityUnlinkRequest {

    @Size(max = 256, message = "Current password is too large.")
    private String currentPassword;

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }
}
