package com.hotel.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class FacebookLoginRequest {

    @NotBlank(message = "Facebook access token is required.")
    @Size(max = 16384, message = "Facebook access token is too large.")
    private String accessToken;

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }
}
