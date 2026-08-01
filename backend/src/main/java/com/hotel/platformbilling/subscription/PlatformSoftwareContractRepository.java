package com.hotel.platformbilling.subscription;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlatformSoftwareContractRepository extends JpaRepository<SoftwareContract, Long> {

    Optional<SoftwareContract> findByPublicId(String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select contract from PlatformSoftwareContract contract where contract.publicId = :publicId")
    Optional<SoftwareContract> findByPublicIdForUpdate(@Param("publicId") String publicId);

    Optional<SoftwareContract> findByOrderId(Long orderId);

    List<SoftwareContract> findByTargetHotelIdOrderByCreatedAtDesc(Long targetHotelId);

    Optional<SoftwareContract> findFirstByTargetHotelIdAndStatusOrderByCreatedAtDesc(
            Long targetHotelId,
            SoftwareContract.Status status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select contract from PlatformSoftwareContract contract "
            + "where contract.targetHotel.id = :targetHotelId and contract.status = :status")
    Optional<SoftwareContract> findByTargetHotelIdAndStatusForUpdate(
            @Param("targetHotelId") Long targetHotelId,
            @Param("status") SoftwareContract.Status status);
}
