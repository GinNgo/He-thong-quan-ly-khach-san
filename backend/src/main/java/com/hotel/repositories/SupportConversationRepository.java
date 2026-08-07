package com.hotel.repositories;

import com.hotel.entities.SupportConversation;
import jakarta.persistence.LockModeType;
<<<<<<< HEAD
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
=======
>>>>>>> codex/ui-functional-audit-polish
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

<<<<<<< HEAD
import java.time.Instant;
=======
>>>>>>> codex/ui-functional-audit-polish
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SupportConversationRepository extends JpaRepository<SupportConversation, Long> {
<<<<<<< HEAD
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select conversation from SupportConversation conversation where conversation.id = :id")
    Optional<SupportConversation> findLockedById(@Param("id") Long id);
=======

    Optional<SupportConversation> findFirstByCustomerIdAndHotelIdAndChannelAndStatusInOrderByLastActivityAtDesc(
            Long customerId,
            Long hotelId,
            String channel,
            Collection<String> statuses);

    Optional<SupportConversation> findFirstByCustomerIdOrderByLastActivityAtDesc(Long customerId);

    List<SupportConversation> findByHotelIdInAndStatusNotOrderByLastActivityAtDesc(
            Collection<Long> hotelIds,
            String status);

    List<SupportConversation> findByStatusNotOrderByLastActivityAtDesc(String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select conversation from SupportConversation conversation where conversation.id = :id")
    Optional<SupportConversation> findByIdForUpdate(@Param("id") Long id);
>>>>>>> codex/ui-functional-audit-polish
}
