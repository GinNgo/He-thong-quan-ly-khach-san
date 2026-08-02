package com.hotel.security;

import com.hotel.entities.User;
import com.hotel.repositories.UserRepository;
import com.hotel.services.SubscriptionFeatureService;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomUserDetailsServiceAccountStatusTest {

    @Test
    void rejectsSuspendedAccountBeforeLoadingPermissionsOrEntitlements() {
        UserRepository userRepository = mock(UserRepository.class);
        SubscriptionFeatureService subscriptionFeatureService = mock(SubscriptionFeatureService.class);
        CustomUserDetailsService service = new CustomUserDetailsService(
                userRepository,
                subscriptionFeatureService);
        User user = new User();
        user.setId(42L);
        user.setUsername("suspended@example.com");
        user.setPasswordHash("hash");
        user.setStatus("SUSPENDED");
        user.setRoles(Collections.emptySet());
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));

        assertThrows(
                AccountDisabledAuthenticationException.class,
                () -> service.loadUserByUsername(user.getUsername()));
        verify(subscriptionFeatureService, never()).getActiveFeaturesForUser(user.getId());
    }
}
