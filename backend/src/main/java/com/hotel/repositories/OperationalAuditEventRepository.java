package com.hotel.repositories;

import com.hotel.entities.OperationalAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface OperationalAuditEventRepository extends JpaRepository<OperationalAuditEvent, Long>,
        JpaSpecificationExecutor<OperationalAuditEvent> {
}
