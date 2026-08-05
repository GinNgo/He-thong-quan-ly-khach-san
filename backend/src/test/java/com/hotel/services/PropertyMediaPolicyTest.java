package com.hotel.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PropertyMediaPolicyTest {

    private final PropertyMediaPolicy policy = new PropertyMediaPolicy();

    @Test
    void acceptsOnlyAbsoluteHttpsLinksWithoutCredentials() {
        assertEquals(
                "https://cdn.example/property/photo.jpg",
                policy.normalizeExternalUrl(" https://cdn.example/property/photo.jpg "));

        assertThrows(IllegalArgumentException.class,
                () -> policy.normalizeExternalUrl("http://cdn.example/photo.jpg"));
        assertThrows(IllegalArgumentException.class,
                () -> policy.normalizeExternalUrl("javascript:alert(1)"));
        assertThrows(IllegalArgumentException.class,
                () -> policy.normalizeExternalUrl("https://user:secret@cdn.example/photo.jpg"));
        assertThrows(IllegalArgumentException.class,
                () -> policy.normalizeExternalUrl("/api/public/uploads/avatar-9.png"));
    }

    @Test
    void requiresVietnameseAltTextAndBoundsOptionalEnglishText() {
        assertEquals("Phong deluxe", policy.requireAltTextVi(" Phong deluxe "));
        assertEquals("Deluxe room", policy.normalizeAltTextEn(" Deluxe room "));
        assertThrows(IllegalArgumentException.class, () -> policy.requireAltTextVi(" "));
        assertThrows(IllegalArgumentException.class, () -> policy.requireAltTextVi("a".repeat(256)));
        assertThrows(IllegalArgumentException.class, () -> policy.normalizeAltTextEn("a".repeat(256)));
    }
}
