package com.hotel.repositories;

import com.hotel.entities.OperationalAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface OperationalAuditEventRepository extends JpaRepository<OperationalAuditEvent, Long>,
        JpaSpecificationExecutor<OperationalAuditEvent> {

    @Query("""
            select event
            from OperationalAuditEvent event
            where event.scope = 'TENANT'
              and event.hotelId = :propertyId
              and event.domain = 'PROPERTY'
              and event.aggregateType = 'HOTEL'
              and event.aggregateId = :aggregateId
              and event.eventType in :eventTypes
            """)
    Page<OperationalAuditEvent> findPropertyTransitionHistory(
            @Param("propertyId") Long propertyId,
            @Param("aggregateId") String aggregateId,
            @Param("eventTypes") Collection<String> eventTypes,
            Pageable pageable);
}
