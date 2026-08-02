package com.hotel.security;

import com.hotel.entities.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountStatusPolicyTest {

    @Test
    void acceptsOnlyActiveStatusIgnoringCaseAndWhitespace() {
        User active = user(" active ");

        assertDoesNotThrow(() -> AccountStatusPolicy.requireActive(active));
    }

    @Test
    void rejectsEveryNonActiveStatus() {
        for (String status : new String[]{null, "", "SUSPENDED", "DISABLED", "INACTIVE", "PENDING"}) {
            assertThrows(
                    AccountDisabledAuthenticationException.class,
                    () -> AccountStatusPolicy.requireActive(user(status)),
                    () -> "Expected status to be rejected: " + status);
        }
    }

    private User user(String status) {
        User user = new User();
        user.setStatus(status);
        return user;
    }
}
