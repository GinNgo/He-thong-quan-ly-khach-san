package com.hotel.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SocialIdentityLinkRequest {

    @NotBlank(message = "Provider credential is required.")
    @Size(max = 16384, message = "Provider credential is too large.")
    private String credential;

    public String getCredential() {
        return credential;
    }

    public void setCredential(String credential) {
        this.credential = credential;
    }
}
