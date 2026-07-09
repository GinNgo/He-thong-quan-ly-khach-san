package com.hotel.services;

import com.hotel.dtos.AppFunctionDto;
import com.hotel.dtos.AppModuleDto;
import com.hotel.dtos.UpdateRolePermissionsRequest;
import com.hotel.entities.AppFunction;
import com.hotel.entities.AppModule;
import com.hotel.entities.Role;
import com.hotel.entities.RolePermission;
import com.hotel.repositories.AppFunctionRepository;
import com.hotel.repositories.AppModuleRepository;
import com.hotel.repositories.RolePermissionRepository;
import com.hotel.repositories.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RolePermissionService {

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private AppModuleRepository appModuleRepository;

    @Autowired
    private AppFunctionRepository appFunctionRepository;

    public List<AppModuleDto> getRolePermissionsAsTree(Long roleId) {
        Role role = roleRepository.findById(roleId).orElseThrow(() -> new RuntimeException("Role not found"));
        
        // Assuming rolePermissionRepository has findByRole
        // We will fetch all RolePermissions manually by filtering
        List<RolePermission> permissions = rolePermissionRepository.findAll().stream()
                .filter(rp -> rp.getRole().getId().equals(roleId))
                .collect(Collectors.toList());
        
        Map<Long, Integer> permissionMap = permissions.stream()
                .collect(Collectors.toMap(rp -> rp.getFunction().getId(), RolePermission::getActionMask));

        List<AppModule> allModules = appModuleRepository.findAll().stream()
                .sorted(Comparator.comparing(AppModule::getId))
                .collect(Collectors.toList());
        List<AppFunction> allFunctions = appFunctionRepository.findAll().stream()
                .sorted(Comparator
                        .comparing((AppFunction f) -> f.getModule().getId())
                        .thenComparing(f -> f.getSortOrder() != null ? f.getSortOrder() : 999)
                        .thenComparing(AppFunction::getId))
                .collect(Collectors.toList());

        return allModules.stream().map(module -> {
            AppModuleDto moduleDto = new AppModuleDto();
            moduleDto.setId(module.getId());
            moduleDto.setCode(module.getCode());
            moduleDto.setName(module.getName());
            
            List<AppFunctionDto> functionDtos = allFunctions.stream()
                    .filter(f -> f.getModule().getId().equals(module.getId()))
                    .map(f -> {
                        AppFunctionDto fDto = new AppFunctionDto();
                        fDto.setId(f.getId());
                        fDto.setModuleId(module.getId());
                        fDto.setCode(f.getCode());
                        fDto.setName(f.getName());
                        fDto.setUrl(f.getUrl());
                        fDto.setIcon(f.getIcon());
                        fDto.setSortOrder(f.getSortOrder());
                        fDto.setActionMask(permissionMap.getOrDefault(f.getId(), 0));
                        return fDto;
                    })
                    .sorted(Comparator.comparing(f -> f.getSortOrder() != null ? f.getSortOrder() : 999))
                    .collect(Collectors.toList());
            
            moduleDto.setFunctions(functionDtos);
            return moduleDto;
        }).collect(Collectors.toList());
    }

    @Transactional
    public void updateRolePermissions(Long roleId, UpdateRolePermissionsRequest request) {
        Role role = roleRepository.findById(roleId).orElseThrow(() -> new RuntimeException("Role not found"));
        
        List<RolePermission> existingPermissions = rolePermissionRepository.findByRoleId(roleId);
        Map<Long, RolePermission> existingByFunctionId = existingPermissions.stream()
                .collect(Collectors.toMap(rp -> rp.getFunction().getId(), rp -> rp));
        Set<Long> seenFunctionIds = new HashSet<>();
        
        if (request == null || request.getPermissions() == null) {
            rolePermissionRepository.deleteByRoleId(roleId);
            return;
        }

        for (UpdateRolePermissionsRequest.PermissionEntry entry : request.getPermissions()) {
            if (entry.getFunctionId() == null) {
                continue;
            }

            seenFunctionIds.add(entry.getFunctionId());
            int actionMask = entry.getActionMask() != null ? entry.getActionMask() : 0;
            RolePermission existing = existingByFunctionId.get(entry.getFunctionId());
            
            if (actionMask <= 0) {
                if (existing != null) {
                    rolePermissionRepository.delete(existing);
                }
                continue;
            }

            if (existing != null) {
                existing.setActionMask(actionMask);
                rolePermissionRepository.save(existing);
                continue;
            }

            Optional<AppFunction> function = appFunctionRepository.findById(entry.getFunctionId());
            if (function.isPresent()) {
                RolePermission rp = new RolePermission();
                rp.setRole(role);
                rp.setFunction(function.get());
                rp.setActionMask(actionMask);
                rolePermissionRepository.save(rp);
            }
        }

        existingPermissions.stream()
                .filter(rp -> !seenFunctionIds.contains(rp.getFunction().getId()))
                .forEach(rolePermissionRepository::delete);
    }
}
