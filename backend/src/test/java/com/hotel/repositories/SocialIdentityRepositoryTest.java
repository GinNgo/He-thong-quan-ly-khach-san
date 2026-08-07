package com.hotel.repositories;

import com.hotel.entities.SocialIdentity;
import com.hotel.entities.SocialProvider;
import com.hotel.entities.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
class SocialIdentityRepositoryTest {

    @Autowired
    private SocialIdentityRepository socialIdentityRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void providerSubjectPersistsAsStableUserLink() {
        User user = new User();
        user.setUsername("guest@example.com");
        user.setEmail("guest@example.com");
        user.setPasswordHash("not-used-for-social-login");
        user.setStatus("ACTIVE");
        user = userRepository.saveAndFlush(user);

        SocialIdentity identity = new SocialIdentity();
        identity.setUser(user);
        identity.setProvider(SocialProvider.GOOGLE);
        identity.setProviderSubject("google-subject-123");
        identity.setProviderEmail("guest@example.com");
        identity.setLastLoginAt(LocalDateTime.now());
        socialIdentityRepository.saveAndFlush(identity);

        var persisted = socialIdentityRepository.findByProviderAndProviderSubject(
                SocialProvider.GOOGLE,
                "google-subject-123");

        assertTrue(persisted.isPresent());
        assertEquals(user.getId(), persisted.orElseThrow().getUser().getId());
    }
}
