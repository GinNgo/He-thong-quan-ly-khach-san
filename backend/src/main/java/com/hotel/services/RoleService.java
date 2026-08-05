package com.hotel.services;

import com.hotel.dtos.RoleCreateRequest;
import com.hotel.dtos.RoleDto;
import com.hotel.dtos.RoleLifecycleRequest;
import com.hotel.dtos.RoleUpdateRequest;
import com.hotel.entities.Role;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.RolePermissionRepository;
import com.hotel.repositories.RoleRepository;
import com.hotel.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class RoleService {
    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired(required = false)
    private OperationalAuditService operationalAuditService;

    public List<RoleDto> getAllRoles() {
        return roleRepository.findAll().stream().map(this::convertToDto).collect(Collectors.toList());
    }

    public RoleDto getRoleById(Long id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found."));
        return convertToDto(role);
    }

    @Transactional
    public RoleDto createRole(RoleCreateRequest request) {
        NormalizedRoleInput input = normalizeAndValidate(
                request.getCode(), request.getName(), request.getDescription(), null);
        Role role = new Role();
        role.setCode(input.code());
        role.setName(input.name());
        role.setDescription(input.description());
        role.setStatus("ACTIVE");
        role.setSystemRole(false);
        Role saved = roleRepository.saveAndFlush(role);
        audit("ROLE_CREATED", saved, null, roleSnapshot(saved), normalizeReason(request.getReason()));
        return convertToDto(saved);
    }

    @Transactional
    public RoleDto updateRole(Long id, RoleUpdateRequest request) {
        Role role = roleRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found."));
        rejectSystemRoleMutation(role);
        requireExpectedVersion(request.getExpectedVersion(), role.getVersion());
        java.util.Map<String, Object> before = roleSnapshot(role);
        NormalizedRoleInput input = normalizeAndValidate(
                request.getCode(), request.getName(), request.getDescription(), id);
        role.setCode(input.code());
        role.setName(input.name());
        role.setDescription(input.description());
        role.setUpdatedAt(java.time.LocalDateTime.now());
        Role saved = roleRepository.saveAndFlush(role);
        audit("ROLE_UPDATED", saved, before, roleSnapshot(saved), normalizeReason(request.getReason()));
        return convertToDto(saved);
    }

    @Transactional
    public void deactivateRole(Long id, RoleLifecycleRequest request) {
        Role role = customRoleForUpdate(id);
        requireExpectedVersion(request.getExpectedVersion(), role.getVersion());
        if ("INACTIVE".equalsIgnoreCase(role.getStatus())) {
            throw new IllegalStateException("Vai trò đã ngừng hoạt động.");
        }
        ensureUnassigned(role);
        java.util.Map<String, Object> before = roleSnapshot(role);
        role.setStatus("INACTIVE");
        role.setUpdatedAt(java.time.LocalDateTime.now());
        Role saved = roleRepository.saveAndFlush(role);
        audit("ROLE_DEACTIVATED", saved, before, roleSnapshot(saved), normalizeReason(request.getReason()));
    }

    @Transactional
    public RoleDto reactivateRole(Long id, RoleLifecycleRequest request) {
        Role role = customRoleForUpdate(id);
        requireExpectedVersion(request.getExpectedVersion(), role.getVersion());
        if (!"INACTIVE".equalsIgnoreCase(role.getStatus())) {
            throw new IllegalStateException("Vai trò đang hoạt động.");
        }
        java.util.Map<String, Object> before = roleSnapshot(role);
        role.setStatus("ACTIVE");
        role.setUpdatedAt(java.time.LocalDateTime.now());
        Role saved = roleRepository.saveAndFlush(role);
        audit("ROLE_REACTIVATED", saved, before, roleSnapshot(saved), normalizeReason(request.getReason()));
        return convertToDto(saved);
    }

    private NormalizedRoleInput normalizeAndValidate(
            String rawCode,
            String rawName,
            String rawDescription,
            Long currentId) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        String name = rawName == null ? "" : rawName.trim();
        String description = rawDescription == null ? "" : rawDescription.trim();
        if (code.isBlank() || name.isBlank()) {
            throw new IllegalArgumentException("Mã và tên vai trò là bắt buộc.");
        }
        rejectReservedSystemCode(code);
        roleRepository.findByCodeIgnoreCase(code).filter(role -> !role.getId().equals(currentId)).ifPresent(role -> {
            throw new IllegalArgumentException("Mã vai trò đã tồn tại.");
        });
        return new NormalizedRoleInput(code, name, description);
    }

    private Role customRoleForUpdate(Long id) {
        Role role = roleRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found."));
        if (role.isGovernedSystemRole()) {
            throw new IllegalStateException("Không thể thay đổi trạng thái vai trò hệ thống.");
        }
        return role;
    }

    private void rejectSystemRoleMutation(Role role) {
        if (role.isGovernedSystemRole()) {
            throw new IllegalStateException("Mã, tên, mô tả và trạng thái vai trò hệ thống là bất biến.");
        }
    }

    private void rejectReservedSystemCode(String code) {
        if (Role.isSystemCode(code)) {
            throw new IllegalArgumentException("Mã vai trò hệ thống được dành riêng.");
        }
    }

    private void requireExpectedVersion(Long expectedVersion, Long currentVersion) {
        if (!Objects.equals(expectedVersion, currentVersion)) {
            throw new OptimisticLockingFailureException(
                    "Vai trò đã được thay đổi. Hãy tải lại dữ liệu trước khi thử lại.");
        }
    }

    private String normalizeReason(String value) {
        String reason = value == null ? "" : value.trim();
        if (reason.length() < 3 || reason.length() > 500) {
            throw new IllegalArgumentException("Lý do thay đổi phải có từ 3 đến 500 ký tự.");
        }
        return reason;
    }

    private void ensureUnassigned(Role role) {
        if (userRepository.countByRoleId(role.getId()) > 0) {
            throw new IllegalStateException("Không thể ngừng sử dụng vai trò đang được gán cho người dùng.");
        }
    }

    private RoleDto convertToDto(Role role) {
        boolean systemRole = role.isGovernedSystemRole();
        RoleDto dto = new RoleDto();
        dto.setId(role.getId());
        dto.setCode(role.getCode());
        dto.setName(role.getName());
        dto.setDescription(role.getDescription());
        dto.setStatus(systemRole ? Role.ACTIVE_STATUS : role.getStatus());
        dto.setSystemRole(systemRole);
        dto.setUserCount(userRepository.countByRoleId(role.getId()));
        dto.setRoleType(systemRole ? "SYSTEM" : "CUSTOM");
        dto.setUpdatedAt(role.getUpdatedAt());
        dto.setVersion(role.getVersion());
        return dto;
    }

    private java.util.Map<String, Object> roleSnapshot(Role role) {
        java.util.Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("id", role.getId());
        snapshot.put("code", role.getCode());
        snapshot.put("name", role.getName());
        snapshot.put("status", role.getStatus());
        snapshot.put("systemRole", role.getSystemRole());
        snapshot.put("version", role.getVersion());
        return snapshot;
    }

    private void audit(String eventType, Role role, Object before, Object after, String reason) {
        if (operationalAuditService == null) return;
        operationalAuditService.append(new OperationalAuditService.AuditCommand(
                "SYSTEM", null, "ROLE", eventType, "ROLE", String.valueOf(role.getId()),
                null, null, reason, before, after, null));
    }

    private record NormalizedRoleInput(String code, String name, String description) {
    }
}
