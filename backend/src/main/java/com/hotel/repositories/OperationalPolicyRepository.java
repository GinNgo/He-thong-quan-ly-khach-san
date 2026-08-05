package com.hotel.repositories;

import com.hotel.entities.OperationalPolicyVersion;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface OperationalPolicyRepository extends JpaRepository<OperationalPolicyVersion, Long> {
    List<OperationalPolicyVersion> findByHotelIdOrderByVersionNumberDesc(Long hotelId);

    Optional<OperationalPolicyVersion> findFirstByHotelIdOrderByVersionNumberDesc(Long hotelId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from OperationalPolicyVersion p where p.id = :id")
    Optional<OperationalPolicyVersion> findByIdForUpdate(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from OperationalPolicyVersion p where p.hotel.id = :hotelId and p.status = 'PUBLISHED' order by p.effectiveFrom")
    List<OperationalPolicyVersion> findPublishedForUpdate(Long hotelId);

    List<OperationalPolicyVersion> findByHotelIdAndStatusOrderByEffectiveFromDesc(Long hotelId, String status);
}
