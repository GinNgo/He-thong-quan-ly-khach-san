package com.hotel.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PasswordResetCompletionRequest {

    @NotBlank(message = "Reset token is required")
    @Size(max = 256, message = "Reset token is invalid")
    private String token;

    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 256, message = "Password must be between 8 and 256 characters")
    private String newPassword;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
