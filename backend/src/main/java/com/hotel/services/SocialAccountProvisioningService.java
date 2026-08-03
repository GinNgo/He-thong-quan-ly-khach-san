package com.hotel.services;

import com.hotel.entities.Role;
import com.hotel.entities.SocialIdentity;
import com.hotel.entities.User;
import com.hotel.exceptions.SocialAccountLinkException;
import com.hotel.repositories.RoleRepository;
import com.hotel.repositories.SocialIdentityRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.security.AccountStatusPolicy;
import com.hotel.services.social.ExternalIdentityProfile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Locale;
import java.util.UUID;

@Service
public class SocialAccountProvisioningService {

    private final SocialIdentityRepository socialIdentityRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public SocialAccountProvisioningService(
            SocialIdentityRepository socialIdentityRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {
        this.socialIdentityRepository = socialIdentityRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Resolution resolveOrProvision(ExternalIdentityProfile profile) {
        String subject = normalizeRequired(profile.subject(), "Provider subject");
        String email = normalizeEmail(profile.email());
        LocalDateTime now = LocalDateTime.now();

        SocialIdentity existing = socialIdentityRepository
                .findByProviderAndProviderSubject(profile.provider(), subject)
                .orElse(null);
        if (existing != null) {
            User user = existing.getUser();
            AccountStatusPolicy.requireActive(user);
            existing.setProviderEmail(email);
            existing.setLastLoginAt(now);
            enrichProfile(user, profile);
            return new Resolution(user, false);
        }

        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw SocialAccountLinkException.linkRequired();
        }

        User user = createUser(email, profile, now);
        userRepository.saveAndFlush(user);

        SocialIdentity identity = new SocialIdentity();
        identity.setUser(user);
        identity.setProvider(profile.provider());
        identity.setProviderSubject(subject);
        identity.setProviderEmail(email);
        identity.setLastLoginAt(now);
        socialIdentityRepository.saveAndFlush(identity);
        return new Resolution(user, true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public User recoverAfterUniqueCollision(ExternalIdentityProfile profile) {
        String subject = normalizeRequired(profile.subject(), "Provider subject");
        SocialIdentity existing = socialIdentityRepository
                .findByProviderAndProviderSubject(profile.provider(), subject)
                .orElse(null);
        if (existing != null) {
            User user = existing.getUser();
            AccountStatusPolicy.requireActive(user);
            existing.setProviderEmail(normalizeEmail(profile.email()));
            existing.setLastLoginAt(LocalDateTime.now());
            enrichProfile(user, profile);
            return user;
        }
        if (userRepository.findByEmailIgnoreCase(normalizeEmail(profile.email())).isPresent()) {
            throw SocialAccountLinkException.linkRequired();
        }
        throw SocialAccountLinkException.provisioningConflict();
    }

    private User createUser(String email, ExternalIdentityProfile profile, LocalDateTime now) {
        User user = new User();
        String username = userRepository.existsByUsername(email)
                ? profile.provider().name().toLowerCase(Locale.ROOT) + "_" + UUID.randomUUID()
                : email;
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(nonBlank(profile.displayName(), email));
        user.setAvatarUrl(blankToNull(profile.avatarUrl()));
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setStatus("ACTIVE");
        user.setCreatedAt(now);
        Role customerRole = roleRepository.findByCode("CUSTOMER")
                .orElseThrow(() -> new IllegalStateException("CUSTOMER role is not configured."));
        user.setRoles(Collections.singleton(customerRole));
        return user;
    }

    private void enrichProfile(User user, ExternalIdentityProfile profile) {
        if ((user.getFullName() == null || user.getFullName().isBlank())
                && profile.displayName() != null && !profile.displayName().isBlank()) {
            user.setFullName(profile.displayName().trim());
        }
        if ((user.getAvatarUrl() == null || user.getAvatarUrl().isBlank())
                && profile.avatarUrl() != null && !profile.avatarUrl().isBlank()) {
            user.setAvatarUrl(blankToNull(profile.avatarUrl()));
        }
    }

    private String normalizeEmail(String email) {
        return normalizeRequired(email, "Provider email").toLowerCase(Locale.ROOT);
    }

    private String normalizeRequired(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.strip();
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return trimmed.length() <= 255 ? trimmed : trimmed.substring(0, 255);
    }

    public record Resolution(User user, boolean created) {
    }
}
