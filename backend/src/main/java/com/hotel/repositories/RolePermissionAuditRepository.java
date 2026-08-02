package com.hotel.repositories;

import com.hotel.entities.RolePermissionAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RolePermissionAuditRepository extends JpaRepository<RolePermissionAudit, Long> {
    List<RolePermissionAudit> findByRoleIdOrderByOccurredAtDesc(Long roleId);
}
