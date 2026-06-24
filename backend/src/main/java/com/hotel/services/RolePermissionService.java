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

import java.util.List;
import java.util.Map;
import java.util.Optional;
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

        List<AppModule> allModules = appModuleRepository.findAll();
        List<AppFunction> allFunctions = appFunctionRepository.findAll();

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
                    }).collect(Collectors.toList());
            
            moduleDto.setFunctions(functionDtos);
            return moduleDto;
        }).collect(Collectors.toList());
    }

    public void updateRolePermissions(Long roleId, UpdateRolePermissionsRequest request) {
        Role role = roleRepository.findById(roleId).orElseThrow(() -> new RuntimeException("Role not found"));
        
        List<RolePermission> existingPermissions = rolePermissionRepository.findAll().stream()
                .filter(rp -> rp.getRole().getId().equals(roleId))
                .collect(Collectors.toList());
        
        for (UpdateRolePermissionsRequest.PermissionEntry entry : request.getPermissions()) {
            Optional<RolePermission> existing = existingPermissions.stream()
                    .filter(rp -> rp.getFunction().getId().equals(entry.getFunctionId()))
                    .findFirst();
            
            if (existing.isPresent()) {
                RolePermission rp = existing.get();
                rp.setActionMask(entry.getActionMask());
                rolePermissionRepository.save(rp);
            } else {
                AppFunction function = appFunctionRepository.findById(entry.getFunctionId()).orElse(null);
                if (function != null) {
                    RolePermission rp = new RolePermission();
                    rp.setRole(role);
                    rp.setFunction(function);
                    rp.setActionMask(entry.getActionMask());
                    rolePermissionRepository.save(rp);
                }
            }
        }
    }
}
