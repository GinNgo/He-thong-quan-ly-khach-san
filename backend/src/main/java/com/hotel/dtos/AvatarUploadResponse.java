package com.hotel.dtos;

public record AvatarUploadResponse(
        String url,
        String contentType,
        int width,
        int height) {
}
