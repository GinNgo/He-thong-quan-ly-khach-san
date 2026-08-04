package com.hotel.paymentprovider.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FinancialAuditEventRepository extends JpaRepository<FinancialAuditEvent, Long>, JpaSpecificationExecutor<FinancialAuditEvent> {
}
