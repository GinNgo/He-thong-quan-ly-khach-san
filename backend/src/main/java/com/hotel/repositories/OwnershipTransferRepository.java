package com.hotel.repositories;

import com.hotel.entities.OwnershipTransfer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OwnershipTransferRepository extends JpaRepository<OwnershipTransfer, Long> {
    Optional<OwnershipTransfer> findFirstByHotelIdAndStatusOrderByCreatedAtDesc(Long hotelId, String status);
    boolean existsByHotelIdAndToUserIdAndStatus(Long hotelId, Long toUserId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select transfer from OwnershipTransfer transfer join fetch transfer.hotel join fetch transfer.fromUser join fetch transfer.toUser where transfer.id = :id")
    Optional<OwnershipTransfer> findByIdForUpdate(@Param("id") Long id);

    @Query("select transfer.hotel.id from OwnershipTransfer transfer where transfer.id = :id")
    Optional<Long> findHotelIdById(@Param("id") Long id);
}
