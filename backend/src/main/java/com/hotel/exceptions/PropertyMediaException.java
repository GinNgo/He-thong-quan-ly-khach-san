package com.hotel.exceptions;

import org.springframework.http.HttpStatus;

public class PropertyMediaException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final boolean retryable;

    private PropertyMediaException(
            HttpStatus status,
            String code,
            String message,
            boolean retryable,
            Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
        this.retryable = retryable;
    }

    public static PropertyMediaException fromUpload(AvatarUploadException exception) {
        String message = switch (exception.code()) {
            case "AVATAR_FILE_EMPTY" -> "Select a non-empty property image file.";
            case "AVATAR_FILE_TOO_LARGE" -> "The property image must not exceed 5 MB.";
            case "AVATAR_CONTENT_TYPE_MISMATCH" -> "The declared image type does not match the property image file.";
            case "AVATAR_SIGNATURE_INVALID", "AVATAR_FORMAT_UNSUPPORTED" ->
                    "Only valid JPG, PNG and WebP property images are supported.";
            case "AVATAR_DIMENSIONS_INVALID" ->
                    "The property image dimensions are invalid or exceed the allowed limit.";
            default -> "The property image could not be stored. Try again later.";
        };
        return new PropertyMediaException(
                exception.status(),
                exception.code().replace("AVATAR_", "PROPERTY_MEDIA_"),
                message,
                exception.retryable(),
                exception);
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public boolean retryable() { return retryable; }
}
