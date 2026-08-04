package com.hotel.services;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

@Component
public class PropertyMediaPolicy {

    private static final String MANAGED_UPLOAD_PREFIX = "/api/public/uploads/";

    public String normalizeExternalUrl(String value) {
        String url = value == null ? "" : value.trim();
        if (url.isEmpty() || url.length() > 1000) {
            throw new IllegalArgumentException("Image URL is required and must not exceed 1000 characters.");
        }
        if (url.startsWith(MANAGED_UPLOAD_PREFIX)) {
            throw new IllegalArgumentException("Managed images must be added through the upload endpoint.");
        }
        try {
            URI parsed = new URI(url);
            String scheme = parsed.getScheme() == null ? "" : parsed.getScheme().toLowerCase(Locale.ROOT);
            if (!"https".equals(scheme) || parsed.getHost() == null || parsed.getUserInfo() != null) {
                throw new IllegalArgumentException("External image URLs must use HTTPS with a valid host.");
            }
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Image URL is invalid.");
        }
        return url;
    }

    public String requireAltTextVi(String value) {
        String altText = value == null ? "" : value.trim();
        if (altText.isEmpty() || altText.length() > 255) {
            throw new IllegalArgumentException(
                    "Vietnamese image alternative text is required and must not exceed 255 characters.");
        }
        return altText;
    }

    public String normalizeAltTextEn(String value) {
        if (value == null || value.isBlank()) return null;
        String altText = value.trim();
        if (altText.length() > 255) {
            throw new IllegalArgumentException("English image alternative text must not exceed 255 characters.");
        }
        return altText;
    }
}
