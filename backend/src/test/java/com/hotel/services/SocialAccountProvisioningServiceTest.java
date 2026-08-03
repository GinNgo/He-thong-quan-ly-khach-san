package com.hotel.services;

import com.hotel.entities.Role;
import com.hotel.entities.SocialIdentity;
import com.hotel.entities.SocialProvider;
import com.hotel.entities.User;
import com.hotel.exceptions.SocialAccountLinkException;
import com.hotel.repositories.RoleRepository;
import com.hotel.repositories.SocialIdentityRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.services.social.ExternalIdentityProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocialAccountProvisioningServiceTest {

    @Mock SocialIdentityRepository identities;
    @Mock UserRepository users;
    @Mock RoleRepository roles;
    @Mock PasswordEncoder passwordEncoder;

    private SocialAccountProvisioningService service;

    @BeforeEach
    void setUp() {
        service = new SocialAccountProvisioningService(identities, users, roles, passwordEncoder);
    }

    @Test
    void unknownSubjectWithExistingEmailRequiresAuthenticatedLink() {
        ExternalIdentityProfile profile = profile("new-subject", "Guest@Example.com");
        when(identities.findByProviderAndProviderSubject(SocialProvider.GOOGLE, "new-subject"))
                .thenReturn(Optional.empty());
        when(users.findByEmailIgnoreCase("guest@example.com"))
                .thenReturn(Optional.of(activeUser(1L, "guest@example.com")));

        SocialAccountLinkException error = assertThrows(
                SocialAccountLinkException.class,
                () -> service.resolveOrProvision(profile));

        assertEquals("SOCIAL_LINK_REQUIRED", error.code());
        verify(users, never()).saveAndFlush(any(User.class));
        verify(identities, never()).saveAndFlush(any(SocialIdentity.class));
    }

    @Test
    void unknownSubjectAndEmailCreatesOneCustomerAndImmutableIdentity() {
        ExternalIdentityProfile profile = profile("new-subject", "Guest@Example.com");
        Role customer = new Role();
        customer.setCode("CUSTOMER");
        when(identities.findByProviderAndProviderSubject(SocialProvider.GOOGLE, "new-subject"))
                .thenReturn(Optional.empty());
        when(users.findByEmailIgnoreCase("guest@example.com")).thenReturn(Optional.empty());
        when(users.existsByUsername("guest@example.com")).thenReturn(false);
        when(passwordEncoder.encode(any(String.class))).thenReturn("generated-hash");
        when(roles.findByCode("CUSTOMER")).thenReturn(Optional.of(customer));
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(42L);
            return user;
        });
        when(identities.saveAndFlush(any(SocialIdentity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.resolveOrProvision(profile);

        assertEquals(42L, result.user().getId());
        assertEquals("guest@example.com", result.user().getEmail());
        ArgumentCaptor<SocialIdentity> identity = ArgumentCaptor.forClass(SocialIdentity.class);
        verify(identities).saveAndFlush(identity.capture());
        assertEquals("new-subject", identity.getValue().getProviderSubject());
        assertEquals(SocialProvider.GOOGLE, identity.getValue().getProvider());
    }

    private ExternalIdentityProfile profile(String subject, String email) {
        return new ExternalIdentityProfile(SocialProvider.GOOGLE, subject, email, "Guest", null);
    }

    private User activeUser(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setUsername(email);
        user.setEmail(email);
        user.setStatus("ACTIVE");
        return user;
    }
}
