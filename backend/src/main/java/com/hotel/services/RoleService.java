package com.hotel.services;

import com.hotel.dtos.RoleCreateRequest;
import com.hotel.dtos.RoleDto;
import com.hotel.dtos.RoleUpdateRequest;
import com.hotel.entities.Role;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.RolePermissionRepository;
import com.hotel.repositories.RoleRepository;
import com.hotel.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RoleService {
    static final Set<String> SYSTEM_ROLE_CODES = Set.of(
            "SUPER_ADMIN", "ADMIN", "CUSTOMER", "PROPERTY_OWNER", "HOTEL_ADMIN",
            "HOTEL_MANAGER", "RECEPTIONIST", "ACCOUNTANT");
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
        Role saved = roleRepository.save(role);
        audit("ROLE_CREATED", saved, null, roleSnapshot(saved), "Custom role created");
        return convertToDto(saved);
    }

    @Transactional
    public RoleDto updateRole(Long id, RoleUpdateRequest request) {
        Role role = roleRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found."));
        java.util.Map<String, Object> before = roleSnapshot(role);
        NormalizedRoleInput input = normalizeAndValidate(
                request.getCode(), request.getName(), request.getDescription(), id);
        if (Boolean.TRUE.equals(role.getSystemRole()) && !role.getCode().equals(input.code())) {
            throw new IllegalArgumentException("Không thể thay đổi mã vai trò hệ thống.");
        }
        role.setCode(input.code());
        role.setName(input.name());
        role.setDescription(input.description());
        Role saved = roleRepository.save(role);
        audit("ROLE_UPDATED", saved, before, roleSnapshot(saved), "Role metadata updated");
        return convertToDto(saved);
    }

    @Transactional
    public void deactivateRole(Long id) {
        Role role = customRoleForUpdate(id);
        ensureUnassigned(role);
        java.util.Map<String, Object> before = roleSnapshot(role);
        role.setStatus("INACTIVE");
        Role saved = roleRepository.save(role);
        audit("ROLE_DEACTIVATED", saved, before, roleSnapshot(saved), "Custom role deactivated");
    }

    @Transactional
    public RoleDto reactivateRole(Long id) {
        Role role = customRoleForUpdate(id);
        java.util.Map<String, Object> before = roleSnapshot(role);
        role.setStatus("ACTIVE");
        Role saved = roleRepository.save(role);
        audit("ROLE_REACTIVATED", saved, before, roleSnapshot(saved), "Custom role reactivated");
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
        roleRepository.findByCodeIgnoreCase(code).filter(role -> !role.getId().equals(currentId)).ifPresent(role -> {
            throw new IllegalArgumentException("Mã vai trò đã tồn tại.");
        });
        return new NormalizedRoleInput(code, name, description);
    }

    private Role customRoleForUpdate(Long id) {
        Role role = roleRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found."));
        if (Boolean.TRUE.equals(role.getSystemRole()) || SYSTEM_ROLE_CODES.contains(role.getCode())) {
            throw new IllegalStateException("Không thể thay đổi trạng thái vai trò hệ thống.");
        }
        return role;
    }

    private void ensureUnassigned(Role role) {
        if (userRepository.countByRoleId(role.getId()) > 0) {
            throw new IllegalStateException("Không thể ngừng sử dụng vai trò đang được gán cho người dùng.");
        }
    }

    private RoleDto convertToDto(Role role) {
        RoleDto dto = new RoleDto();
        dto.setId(role.getId());
        dto.setCode(role.getCode());
        dto.setName(role.getName());
        dto.setDescription(role.getDescription());
        dto.setStatus(role.getStatus());
        dto.setSystemRole(role.getSystemRole());
        dto.setUserCount(userRepository.countByRoleId(role.getId()));
        dto.setRoleType(Boolean.TRUE.equals(role.getSystemRole()) ? "SYSTEM" : "CUSTOM");
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
