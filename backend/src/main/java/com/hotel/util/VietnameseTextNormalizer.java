package com.hotel.util;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

public final class VietnameseTextNormalizer {

    private VietnameseTextNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replace("đ", "d")
                .replace("Đ", "D")
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public static String joinAndNormalize(String... parts) {
        return normalize(String.join(" ", Arrays.stream(parts)
                .filter(Objects::nonNull)
                .filter(part -> !part.isBlank())
                .toList()));
    }
}
