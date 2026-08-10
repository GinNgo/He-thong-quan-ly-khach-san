package com.hotel.repositories;

import com.hotel.entities.SupportConversation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SupportConversationRepository extends JpaRepository<SupportConversation, Long> {

    Optional<SupportConversation> findFirstByCustomerIdAndHotelIdAndChannelAndStatusInOrderByLastActivityAtDesc(
            Long customerId,
            Long hotelId,
            String channel,
            Collection<String> statuses);

    Optional<SupportConversation> findFirstByCustomerIdOrderByLastActivityAtDesc(Long customerId);

    Optional<SupportConversation> findFirstByCustomerIdAndChannelOrderByLastActivityAtDesc(
            Long customerId,
            String channel);

    List<SupportConversation> findByHotelIdInAndStatusNotOrderByLastActivityAtDesc(
            Collection<Long> hotelIds,
            String status);

    List<SupportConversation> findByHotelIdInAndChannelAndStatusNotOrderByLastActivityAtDesc(
            Collection<Long> hotelIds,
            String channel,
            String status);

    List<SupportConversation> findByStatusNotOrderByLastActivityAtDesc(String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select conversation from SupportConversation conversation where conversation.id = :id")
    Optional<SupportConversation> findByIdForUpdate(@Param("id") Long id);
}
