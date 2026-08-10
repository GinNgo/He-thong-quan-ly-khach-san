package com.hotel.services;

import com.hotel.dtos.SocialIdentityResponse;
import com.hotel.entities.SocialIdentity;
import com.hotel.entities.SocialProvider;
import com.hotel.entities.User;
import com.hotel.exceptions.SocialAccountLinkException;
import com.hotel.repositories.SocialIdentityRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.security.AccountStatusPolicy;
import com.hotel.services.social.ExternalIdentityProfile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.sql.SQLException;
import java.util.List;

@Service
public class SocialAccountLinkService {

    private final SocialIdentityRepository socialIdentityRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SocialAccountProvisioningService provisioningService;

    public SocialAccountLinkService(
            SocialIdentityRepository socialIdentityRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            SocialAccountProvisioningService provisioningService) {
        this.socialIdentityRepository = socialIdentityRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.provisioningService = provisioningService;
    }

    public User resolveOrLink(ExternalIdentityProfile profile) {
        validate(profile);
        try {
            return provisioningService.resolveOrProvision(profile).user();
        } catch (DataIntegrityViolationException collision) {
            if (!isUniqueConstraintViolation(collision)) {
                throw collision;
            }
            // The failed transaction is isolated in the provisioning service. A new
            // transaction can safely resolve the winner of a concurrent unique insert.
            return provisioningService.recoverAfterUniqueCollision(profile);
        }
    }

    private boolean isUniqueConstraintViolation(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && (sqlException.getErrorCode() == 2601 || sqlException.getErrorCode() == 2627)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @Transactional(readOnly = true)
    public List<SocialIdentityResponse> list(Long userId) {
        requireUser(userId);
        long identityCount = socialIdentityRepository.countByUserId(userId);
        return socialIdentityRepository.findAllByUserIdOrderByProviderAsc(userId).stream()
                .map(identity -> toResponse(identity, identityCount == 1))
                .toList();
    }

    @Transactional
    public SocialIdentityResponse link(Long userId, ExternalIdentityProfile profile) {
        validate(profile);
        User user = lockUser(userId);
        AccountStatusPolicy.requireActive(user);

        String subject = profile.subject().strip();
        SocialIdentity subjectOwner = socialIdentityRepository
                .findByProviderAndProviderSubject(profile.provider(), subject)
                .orElse(null);
        if (subjectOwner != null) {
            if (!subjectOwner.getUser().getId().equals(userId)) {
                throw SocialAccountLinkException.identityInUse();
            }
            subjectOwner.setProviderEmail(normalizeEmail(profile.email()));
            subjectOwner.setLastLoginAt(LocalDateTime.now());
            return toResponse(subjectOwner, socialIdentityRepository.countByUserId(userId) == 1);
        }

        if (socialIdentityRepository.findByUserIdAndProvider(userId, profile.provider()).isPresent()) {
            throw SocialAccountLinkException.providerAlreadyLinked();
        }

        SocialIdentity identity = new SocialIdentity();
        identity.setUser(user);
        identity.setProvider(profile.provider());
        identity.setProviderSubject(subject);
        identity.setProviderEmail(normalizeEmail(profile.email()));
        identity.setLastLoginAt(LocalDateTime.now());
        try {
            SocialIdentity saved = socialIdentityRepository.saveAndFlush(identity);
            return toResponse(saved, socialIdentityRepository.countByUserId(userId) == 1);
        } catch (DataIntegrityViolationException collision) {
            throw SocialAccountLinkException.identityInUse();
        }
    }

    @Transactional
    public boolean unlink(Long userId, SocialProvider provider, String currentPassword) {
        User user = lockUser(userId);
        AccountStatusPolicy.requireActive(user);
        SocialIdentity identity = socialIdentityRepository
                .findByUserIdAndProvider(userId, provider)
                .orElse(null);
        if (identity == null) {
            return false;
        }

        if (socialIdentityRepository.countByUserId(userId) == 1) {
            if (currentPassword == null || currentPassword.isBlank()) {
                throw SocialAccountLinkException.unlinkPasswordRequired();
            }
            if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
                throw SocialAccountLinkException.unlinkPasswordInvalid();
            }
        }
        socialIdentityRepository.delete(identity);
        return true;
    }

    private SocialIdentityResponse toResponse(SocialIdentity identity, boolean passwordRequired) {
        return new SocialIdentityResponse(
                identity.getProvider(),
                identity.getProviderEmail(),
                identity.getCreatedAt(),
                identity.getLastLoginAt(),
                passwordRequired);
    }

    private User lockUser(Long userId) {
        if (userId == null) {
            throw SocialAccountLinkException.authenticationRequired();
        }
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(SocialAccountLinkException::accountNotFound);
    }

    private User requireUser(Long userId) {
        if (userId == null) {
            throw SocialAccountLinkException.authenticationRequired();
        }
        return userRepository.findById(userId)
                .orElseThrow(SocialAccountLinkException::accountNotFound);
    }

    private void validate(ExternalIdentityProfile profile) {
        if (profile == null || profile.provider() == null
                || profile.subject() == null || profile.subject().isBlank()
                || profile.email() == null || profile.email().isBlank()) {
            throw new IllegalArgumentException("Social identity profile is incomplete.");
        }
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(java.util.Locale.ROOT);
    }

}
