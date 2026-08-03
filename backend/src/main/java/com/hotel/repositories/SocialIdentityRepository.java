package com.hotel.repositories;

import com.hotel.entities.SocialIdentity;
import com.hotel.entities.SocialProvider;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface SocialIdentityRepository extends JpaRepository<SocialIdentity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SocialIdentity> findByProviderAndProviderSubject(
            SocialProvider provider,
            String providerSubject);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SocialIdentity> findByUserIdAndProvider(Long userId, SocialProvider provider);

    List<SocialIdentity> findAllByUserIdOrderByProviderAsc(Long userId);

    long countByUserId(Long userId);
}
