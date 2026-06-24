package com.hotel.services;

import com.hotel.dtos.RoleDto;
import com.hotel.entities.Role;
import com.hotel.repositories.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RoleService {
    @Autowired
    private RoleRepository roleRepository;

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
        Role saved = roleRepository.save(role);
        return convertToDto(saved);
    }

    public RoleDto updateRole(Long id, RoleDto dto) {
        Role role = roleRepository.findById(id).orElseThrow(() -> new RuntimeException("Role not found"));
        role.setCode(dto.getCode());
        role.setName(dto.getName());
        Role saved = roleRepository.save(role);
        return convertToDto(saved);
    }

    public void deleteRole(Long id) {
        roleRepository.deleteById(id);
    }

    private RoleDto convertToDto(Role role) {
        RoleDto dto = new RoleDto();
        dto.setId(role.getId());
        dto.setCode(role.getCode());
        dto.setName(role.getName());
        return dto;
    }
}
