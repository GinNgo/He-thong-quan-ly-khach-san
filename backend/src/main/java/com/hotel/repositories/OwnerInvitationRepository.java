package com.hotel.repositories;

import com.hotel.entities.OwnerInvitation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OwnerInvitationRepository extends JpaRepository<OwnerInvitation, Long> {
    boolean existsByHotelIdAndInvitedEmailAndStatus(Long hotelId, String invitedEmail, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invite from OwnerInvitation invite join fetch invite.hotel where invite.tokenHash = :tokenHash")
    Optional<OwnerInvitation> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
    Optional<OwnerInvitation> findByTokenHash(String tokenHash);

    @Query("select invite.hotel.id from OwnerInvitation invite where invite.id = :id")
    Optional<Long> findHotelIdById(@Param("id") Long id);

    @Query("select invite.hotel.id from OwnerInvitation invite where invite.tokenHash = :tokenHash")
    Optional<Long> findHotelIdByTokenHash(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invite from OwnerInvitation invite where invite.id = :id")
    Optional<OwnerInvitation> findByIdForUpdate(@Param("id") Long id);
}
