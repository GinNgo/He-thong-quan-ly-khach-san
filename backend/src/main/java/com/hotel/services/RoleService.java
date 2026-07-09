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
import java.util.stream.Collectors;

@Service
public class RoleService {
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
        Role role = new Role();
        role.setCode(dto.getCode());
        role.setName(dto.getName());
        role.setDescription(dto.getDescription());
        Role saved = roleRepository.save(role);
        return convertToDto(saved);
    }

    public RoleDto updateRole(Long id, RoleDto dto) {
        Role role = roleRepository.findById(id).orElseThrow(() -> new RuntimeException("Role not found"));
        role.setCode(dto.getCode());
        role.setName(dto.getName());
        role.setDescription(dto.getDescription());
        Role saved = roleRepository.save(role);
        return convertToDto(saved);
    }

    @Transactional
    public void deleteRole(Long id) {
        Role role = roleRepository.findById(id).orElseThrow(() -> new RuntimeException("Role not found"));
        userRepository.findAll().forEach(user -> {
            if (user.getRoles() != null && user.getRoles().removeIf(userRole -> userRole.getId().equals(role.getId()))) {
                userRepository.save(user);
            }
        });
        rolePermissionRepository.deleteByRoleId(id);
        roleRepository.delete(role);
    }

    private RoleDto convertToDto(Role role) {
        RoleDto dto = new RoleDto();
        dto.setId(role.getId());
        dto.setCode(role.getCode());
        dto.setName(role.getName());
        dto.setDescription(role.getDescription());
        return dto;
    }
}
