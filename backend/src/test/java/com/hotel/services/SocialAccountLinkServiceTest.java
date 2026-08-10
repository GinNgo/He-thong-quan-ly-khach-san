package com.hotel.services;

import com.hotel.entities.Role;
import com.hotel.entities.SocialIdentity;
import com.hotel.entities.SocialProvider;
import com.hotel.entities.User;
import com.hotel.exceptions.SocialAccountLinkException;
import com.hotel.repositories.SocialIdentityRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.services.social.ExternalIdentityProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocialAccountLinkServiceTest {

    @Mock
    private SocialIdentityRepository identities;
    @Mock
    private UserRepository users;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private SocialAccountProvisioningService provisioning;

    private SocialAccountLinkService service;

    @BeforeEach
    void setUp() {
        service = new SocialAccountLinkService(identities, users, passwordEncoder, provisioning);
    }

    @Test
    void concurrentProvisioningRecoversTheWinnerByImmutableSubject() {
        ExternalIdentityProfile profile = profile("subject-1", "guest@example.com");
        User winner = activeUser(10L, "guest@example.com");
        when(provisioning.resolveOrProvision(profile))
                .thenThrow(uniqueConstraintViolation());
        when(provisioning.recoverAfterUniqueCollision(profile)).thenReturn(winner);

        assertEquals(winner, service.resolveOrLink(profile));
        verify(provisioning).recoverAfterUniqueCollision(profile);
    }

    @Test
    void concurrentProvisioningWithoutWinnerReturnsRetryableConflict() {
        ExternalIdentityProfile profile = profile("subject-2", "guest@example.com");
        when(provisioning.resolveOrProvision(profile))
                .thenThrow(uniqueConstraintViolation());
        when(provisioning.recoverAfterUniqueCollision(profile))
                .thenThrow(SocialAccountLinkException.provisioningConflict());

        SocialAccountLinkException error = assertThrows(
                SocialAccountLinkException.class, () -> service.resolveOrLink(profile));
        assertEquals("SOCIAL_PROVISIONING_CONFLICT", error.code());
        assertTrue(error.retryable());
    }

    @Test
    void nonUniquePersistenceFailureIsNotMisreportedAsConcurrentProvisioning() {
        ExternalIdentityProfile profile = profile("subject-db", "guest@example.com");
        DataIntegrityViolationException failure = new DataIntegrityViolationException(
                "null constraint", new SQLException("failed_login_count is required", "23000", 515));
        when(provisioning.resolveOrProvision(profile)).thenThrow(failure);

        assertEquals(failure, assertThrows(
                DataIntegrityViolationException.class,
                () -> service.resolveOrLink(profile)));
        verify(provisioning, never()).recoverAfterUniqueCollision(profile);
    }

    private DataIntegrityViolationException uniqueConstraintViolation() {
        return new DataIntegrityViolationException(
                "unique provider subject",
                new SQLException("duplicate key", "23000", 2627));
    }

    @Test
    void explicitLinkRejectsProviderSubjectOwnedByAnotherUser() {
        User current = activeUser(20L, "current@example.com");
        User other = activeUser(21L, "other@example.com");
        SocialIdentity existing = identity(other, "subject-3", SocialProvider.GOOGLE);
        when(users.findByIdForUpdate(20L)).thenReturn(Optional.of(current));
        when(identities.findByProviderAndProviderSubject(SocialProvider.GOOGLE, "subject-3"))
                .thenReturn(Optional.of(existing));

        SocialAccountLinkException error = assertThrows(
                SocialAccountLinkException.class,
                () -> service.link(20L, profile("subject-3", "current@example.com")));
        assertEquals("SOCIAL_IDENTITY_IN_USE", error.code());
        verify(identities, never()).saveAndFlush(any(SocialIdentity.class));
    }

    @Test
    void explicitLinkRejectsSecondSubjectForSameProvider() {
        User current = activeUser(22L, "current@example.com");
        when(users.findByIdForUpdate(22L)).thenReturn(Optional.of(current));
        when(identities.findByProviderAndProviderSubject(SocialProvider.GOOGLE, "subject-4"))
                .thenReturn(Optional.empty());
        when(identities.findByUserIdAndProvider(22L, SocialProvider.GOOGLE))
                .thenReturn(Optional.of(identity(current, "old-subject", SocialProvider.GOOGLE)));

        SocialAccountLinkException error = assertThrows(
                SocialAccountLinkException.class,
                () -> service.link(22L, profile("subject-4", "current@example.com")));
        assertEquals("SOCIAL_PROVIDER_ALREADY_LINKED", error.code());
    }

    @Test
    void unlinkLastProviderRequiresAndChecksCurrentPassword() {
        User current = activeUser(23L, "current@example.com");
        SocialIdentity identity = identity(current, "subject-5", SocialProvider.GOOGLE);
        when(users.findByIdForUpdate(23L)).thenReturn(Optional.of(current));
        when(identities.findByUserIdAndProvider(23L, SocialProvider.GOOGLE))
                .thenReturn(Optional.of(identity));
        when(identities.countByUserId(23L)).thenReturn(1L);

        SocialAccountLinkException missing = assertThrows(
                SocialAccountLinkException.class,
                () -> service.unlink(23L, SocialProvider.GOOGLE, null));
        assertEquals("SOCIAL_UNLINK_PASSWORD_REQUIRED", missing.code());

        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);
        SocialAccountLinkException invalid = assertThrows(
                SocialAccountLinkException.class,
                () -> service.unlink(23L, SocialProvider.GOOGLE, "wrong"));
        assertEquals("SOCIAL_UNLINK_PASSWORD_INVALID", invalid.code());

        when(passwordEncoder.matches("correct", "hash")).thenReturn(true);
        assertTrue(service.unlink(23L, SocialProvider.GOOGLE, "correct"));
        verify(identities).delete(identity);
    }

    @Test
    void unlinkMissingProviderIsIdempotent() {
        User current = activeUser(24L, "current@example.com");
        when(users.findByIdForUpdate(24L)).thenReturn(Optional.of(current));
        when(identities.findByUserIdAndProvider(24L, SocialProvider.FACEBOOK))
                .thenReturn(Optional.empty());

        assertFalse(service.unlink(24L, SocialProvider.FACEBOOK, null));
        verify(identities, never()).delete(any(SocialIdentity.class));
    }

    @Test
    void listNeverExposesProviderSubjectAndMarksLastLinkPasswordRequirement() {
        User current = activeUser(25L, "current@example.com");
        SocialIdentity identity = identity(current, "subject-6", SocialProvider.FACEBOOK);
        when(users.findById(25L)).thenReturn(Optional.of(current));
        when(identities.countByUserId(25L)).thenReturn(1L);
        when(identities.findAllByUserIdOrderByProviderAsc(25L)).thenReturn(List.of(identity));

        var result = service.list(25L);

        assertEquals(1, result.size());
        assertEquals(SocialProvider.FACEBOOK, result.get(0).provider());
        assertEquals("current@example.com", result.get(0).providerEmail());
        assertTrue(result.get(0).passwordRequiredToUnlink());
    }

    private ExternalIdentityProfile profile(String subject, String email) {
        return new ExternalIdentityProfile(SocialProvider.GOOGLE, subject, email, "Guest", null);
    }

    private User activeUser(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setUsername(email);
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setStatus("ACTIVE");
        Role role = new Role();
        role.setCode("CUSTOMER");
        user.setRoles(java.util.Set.of(role));
        return user;
    }

    private SocialIdentity identity(User user, String subject, SocialProvider provider) {
        SocialIdentity identity = new SocialIdentity();
        identity.setUser(user);
        identity.setProvider(provider);
        identity.setProviderSubject(subject);
        identity.setProviderEmail(user.getEmail());
        identity.setLastLoginAt(java.time.LocalDateTime.now());
        return identity;
    }
}
