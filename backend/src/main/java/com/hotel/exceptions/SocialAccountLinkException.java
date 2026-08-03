package com.hotel.exceptions;

import org.springframework.http.HttpStatus;

public class SocialAccountLinkException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final boolean retryable;

    private SocialAccountLinkException(
            HttpStatus status,
            String code,
            String message,
            boolean retryable) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryable = retryable;
    }

    public static SocialAccountLinkException linkRequired() {
        return new SocialAccountLinkException(
                HttpStatus.CONFLICT,
                "SOCIAL_LINK_REQUIRED",
                "An account already uses this email. Sign in with your password, then link the provider in account settings.",
                false);
    }

    public static SocialAccountLinkException identityInUse() {
        return new SocialAccountLinkException(
                HttpStatus.CONFLICT,
                "SOCIAL_IDENTITY_IN_USE",
                "This provider account is already linked to another user.",
                false);
    }

    public static SocialAccountLinkException providerAlreadyLinked() {
        return new SocialAccountLinkException(
                HttpStatus.CONFLICT,
                "SOCIAL_PROVIDER_ALREADY_LINKED",
                "A different account from this provider is already linked.",
                false);
    }

    public static SocialAccountLinkException unlinkPasswordRequired() {
        return new SocialAccountLinkException(
                HttpStatus.CONFLICT,
                "SOCIAL_UNLINK_PASSWORD_REQUIRED",
                "Enter the current password before removing the last linked provider.",
                false);
    }

    public static SocialAccountLinkException unlinkPasswordInvalid() {
        return new SocialAccountLinkException(
                HttpStatus.BAD_REQUEST,
                "SOCIAL_UNLINK_PASSWORD_INVALID",
                "The current password is incorrect.",
                false);
    }

    public static SocialAccountLinkException unsupportedProvider() {
        return new SocialAccountLinkException(
                HttpStatus.BAD_REQUEST,
                "SOCIAL_PROVIDER_UNSUPPORTED",
                "This social provider is not supported.",
                false);
    }

    public static SocialAccountLinkException authenticationRequired() {
        return new SocialAccountLinkException(
                HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_REQUIRED",
                "Sign in before managing linked accounts.",
                false);
    }

    public static SocialAccountLinkException accountNotFound() {
        return new SocialAccountLinkException(
                HttpStatus.NOT_FOUND,
                "SOCIAL_ACCOUNT_NOT_FOUND",
                "The authenticated account was not found.",
                false);
    }

    public static SocialAccountLinkException provisioningConflict() {
        return new SocialAccountLinkException(
                HttpStatus.CONFLICT,
                "SOCIAL_PROVISIONING_CONFLICT",
                "The provider account changed concurrently. Retry sign-in safely.",
                true);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public boolean retryable() {
        return retryable;
    }
}
