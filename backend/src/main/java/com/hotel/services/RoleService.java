package com.hotel.services;

import com.hotel.dtos.RoleDto;
import com.hotel.entities.Role;
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
    private static final Set<String> SYSTEM_ROLE_CODES = Set.of(
            "SUPER_ADMIN", "ADMIN", "CUSTOMER", "PROPERTY_OWNER", "HOTEL_ADMIN",
            "HOTEL_MANAGER", "RECEPTIONIST", "ACCOUNTANT");
    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private UserRepository userRepository;

    public List<RoleDto> getAllRoles() {
        return roleRepository.findAll().stream().map(this::convertToDto).collect(Collectors.toList());
    }

    public RoleDto getRoleById(Long id) {
        Role role = roleRepository.findById(id).orElseThrow(() -> new RuntimeException("Role not found"));
        return convertToDto(role);
    }

    public RoleDto createRole(RoleDto dto) {
        normalizeAndValidate(dto, null);
        Role role = new Role();
        role.setCode(dto.getCode());
        role.setName(dto.getName());
        role.setDescription(dto.getDescription());
        role.setStatus("ACTIVE");
        role.setSystemRole(false);
        Role saved = roleRepository.save(role);
        return convertToDto(saved);
    }

    public RoleDto updateRole(Long id, RoleDto dto) {
        Role role = roleRepository.findById(id).orElseThrow(() -> new RuntimeException("Role not found"));
        normalizeAndValidate(dto, id);
        if (Boolean.TRUE.equals(role.getSystemRole()) && !role.getCode().equals(dto.getCode())) {
            throw new IllegalArgumentException("Không thể thay đổi mã vai trò hệ thống.");
        }
        role.setCode(dto.getCode());
        role.setName(dto.getName());
        role.setDescription(dto.getDescription());
        if (dto.getStatus() != null) role.setStatus(dto.getStatus());
        Role saved = roleRepository.save(role);
        return convertToDto(saved);
    }

    @Transactional
    public void deleteRole(Long id) {
        Role role = roleRepository.findById(id).orElseThrow(() -> new RuntimeException("Role not found"));
        if (Boolean.TRUE.equals(role.getSystemRole()) || SYSTEM_ROLE_CODES.contains(role.getCode())) {
            throw new IllegalStateException("Không thể ngừng sử dụng vai trò hệ thống.");
        }
        role.setStatus("INACTIVE");
        roleRepository.save(role);
    }

    private void normalizeAndValidate(RoleDto dto, Long currentId) {
        if (dto == null) throw new IllegalArgumentException("Dữ liệu vai trò không hợp lệ.");
        dto.setCode(dto.getCode() == null ? "" : dto.getCode().trim().toUpperCase(Locale.ROOT));
        dto.setName(dto.getName() == null ? "" : dto.getName().trim());
        dto.setDescription(dto.getDescription() == null ? "" : dto.getDescription().trim());
        if (dto.getCode().isBlank() || dto.getName().isBlank()) {
            throw new IllegalArgumentException("Mã và tên vai trò là bắt buộc.");
        }
        roleRepository.findByCode(dto.getCode()).filter(role -> !role.getId().equals(currentId)).ifPresent(role -> {
            throw new IllegalArgumentException("Mã vai trò đã tồn tại.");
        });
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
        return dto;
    }
}
