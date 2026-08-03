package com.hotel.exceptions;

import org.springframework.http.HttpStatus;

public class AvatarUploadException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String publicMessage;
    private final boolean retryable;

    private AvatarUploadException(
            HttpStatus status,
            String code,
            String publicMessage,
            boolean retryable,
            Throwable cause) {
        super(publicMessage, cause);
        this.status = status;
        this.code = code;
        this.publicMessage = publicMessage;
        this.retryable = retryable;
    }

    public static AvatarUploadException emptyFile() {
        return new AvatarUploadException(
                HttpStatus.BAD_REQUEST,
                "AVATAR_FILE_EMPTY",
                "Select a non-empty image file.",
                false,
                null);
    }

    public static AvatarUploadException tooLarge() {
        return new AvatarUploadException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "AVATAR_FILE_TOO_LARGE",
                "The profile image must not exceed 5 MB.",
                false,
                null);
    }

    public static AvatarUploadException unsupportedFormat() {
        return new AvatarUploadException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "AVATAR_FORMAT_UNSUPPORTED",
                "Only valid JPG, PNG and WEBP profile images are supported.",
                false,
                null);
    }

    public static AvatarUploadException contentTypeMismatch() {
        return new AvatarUploadException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "AVATAR_CONTENT_TYPE_MISMATCH",
                "The declared image type does not match the uploaded file.",
                false,
                null);
    }

    public static AvatarUploadException invalidSignature() {
        return new AvatarUploadException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "AVATAR_SIGNATURE_INVALID",
                "The uploaded file is not a valid supported image.",
                false,
                null);
    }

    public static AvatarUploadException invalidDimensions() {
        return new AvatarUploadException(
                HttpStatus.BAD_REQUEST,
                "AVATAR_DIMENSIONS_INVALID",
                "The profile image dimensions are invalid or exceed the allowed limit.",
                false,
                null);
    }

    public static AvatarUploadException userNotFound() {
        return new AvatarUploadException(
                HttpStatus.NOT_FOUND,
                "AVATAR_OWNER_NOT_FOUND",
                "The authenticated account could not be found.",
                false,
                null);
    }

    public static AvatarUploadException fileNotFound() {
        return new AvatarUploadException(
                HttpStatus.NOT_FOUND,
                "AVATAR_NOT_FOUND",
                "The requested profile image was not found.",
                false,
                null);
    }

    public static AvatarUploadException storageFailure(Throwable cause) {
        return new AvatarUploadException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AVATAR_STORAGE_UNAVAILABLE",
                "The profile image could not be stored. Try again later.",
                true,
                cause);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String publicMessage() {
        return publicMessage;
    }

    public boolean retryable() {
        return retryable;
    }
}
