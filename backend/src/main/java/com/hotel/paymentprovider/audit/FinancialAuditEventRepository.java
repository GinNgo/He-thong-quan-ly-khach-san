package com.hotel.paymentprovider.audit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialAuditEventRepository extends JpaRepository<FinancialAuditEvent, Long> {
}
