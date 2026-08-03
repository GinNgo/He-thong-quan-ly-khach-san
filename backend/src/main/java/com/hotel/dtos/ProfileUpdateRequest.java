package com.hotel.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ProfileUpdateRequest {

    @NotBlank(message = "Full name is required.")
    @Size(max = 150, message = "Full name must not exceed 150 characters.")
    private String fullName;

    @NotBlank(message = "Email is required.")
    @Email(message = "Email must be valid.")
    @Size(max = 320, message = "Email must not exceed 320 characters.")
    private String email;

    @Size(max = 30, message = "Phone must not exceed 30 characters.")
    @Pattern(
            regexp = "^[0-9+().\\-\\s]*$",
            message = "Phone contains unsupported characters.")
    private String phone;

    @Size(max = 2048, message = "Avatar URL must not exceed 2048 characters.")
    private String avatarUrl;

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
}
