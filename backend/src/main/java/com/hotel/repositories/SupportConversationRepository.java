package com.hotel.repositories;

import com.hotel.entities.SupportConversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SupportConversationRepository extends JpaRepository<SupportConversation, Long> {
    Page<SupportConversation> findByCustomerIdAndUpdatedAtGreaterThanEqualOrderByUpdatedAtDescIdDesc(
            Long customerId, Instant cutoff, Pageable pageable);
    Page<SupportConversation> findByUpdatedAtGreaterThanEqualOrderByUpdatedAtDescIdDesc(
            Instant cutoff, Pageable pageable);
    Optional<SupportConversation> findByIdAndCustomerId(Long id, Long customerId);
    Optional<SupportConversation> findFirstByCustomerIdOrderByUpdatedAtDesc(Long customerId);

    Optional<SupportConversation> findFirstByCustomerIdAndHotelIdAndChannelAndStatusInOrderByLastActivityAtDesc(
            Long customerId, Long hotelId, String channel, Collection<String> statuses);

    List<SupportConversation> findByHotelIdInAndStatusNotOrderByLastActivityAtDesc(
            Collection<Long> hotelIds, String status);

    List<SupportConversation> findByHotelIdInOrderByLastActivityAtDesc(Collection<Long> hotelIds);

    List<SupportConversation> findByStatusNotOrderByLastActivityAtDesc(String status);

    List<SupportConversation> findAllByOrderByLastActivityAtDesc();

    @Query("select conversation from SupportConversation conversation where conversation.id = :id")
    Optional<SupportConversation> findCurrentById(@Param("id") Long id);
}
