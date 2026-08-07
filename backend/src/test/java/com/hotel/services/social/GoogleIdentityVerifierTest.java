package com.hotel.services.social;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GoogleIdentityVerifierTest {

    @Test
    void missingClientId_FailsBeforeCallingGoogle() {
        GoogleIdentityVerifier verifier = new GoogleIdentityVerifier();
        ReflectionTestUtils.setField(verifier, "googleClientId", "");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> verifier.verify("token"));

        assertEquals("Google login is not configured.", error.getMessage());
    }

    @Test
    void missingToken_FailsBeforeCallingGoogle() {
        GoogleIdentityVerifier verifier = new GoogleIdentityVerifier();
        ReflectionTestUtils.setField(verifier, "googleClientId", "client-id");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> verifier.verify(""));

        assertEquals("Google ID token is required.", error.getMessage());
    }
}
