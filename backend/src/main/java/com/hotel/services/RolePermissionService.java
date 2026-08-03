package com.hotel.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.dtos.AppFunctionDto;
import com.hotel.dtos.AppModuleDto;
import com.hotel.dtos.UpdateRolePermissionsRequest;
import com.hotel.entities.AppFunction;
import com.hotel.entities.AppModule;
import com.hotel.entities.Role;
import com.hotel.entities.RolePermission;
import com.hotel.entities.RolePermissionAudit;
import com.hotel.repositories.AppFunctionRepository;
import com.hotel.repositories.AppModuleRepository;
import com.hotel.repositories.RolePermissionAuditRepository;
import com.hotel.repositories.RolePermissionRepository;
import com.hotel.repositories.RoleRepository;
import com.hotel.security.ActionCode;
import com.hotel.security.CustomUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class RolePermissionService {
    private static final int SUPPORTED_ACTION_MASK = ActionCode.VIEW
            | ActionCode.CREATE
            | ActionCode.UPDATE
            | ActionCode.DELETE
            | ActionCode.EXPORT
            | ActionCode.APPROVE;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private AppModuleRepository appModuleRepository;

    @Autowired
    private AppFunctionRepository appFunctionRepository;

    @Autowired
    private RolePermissionAuditRepository rolePermissionAuditRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired(required = false)
    private OperationalAuditService operationalAuditService;

    public List<AppModuleDto> getRolePermissionsAsTree(Long roleId) {
        roleRepository.findById(roleId).orElseThrow(() -> new RuntimeException("Role not found"));

        List<RolePermission> permissions = rolePermissionRepository.findByRoleId(roleId);
        Map<Long, Integer> permissionMap = permissions.stream()
                .collect(Collectors.toMap(
                        rp -> rp.getFunction().getId(),
                        rp -> rp.getActionMask() == null ? 0 : rp.getActionMask(),
                        (left, right) -> left | right));

        List<AppModule> allModules = appModuleRepository.findAll().stream()
                .sorted(Comparator.comparing(AppModule::getId))
                .toList();
        List<AppFunction> allFunctions = appFunctionRepository.findAll().stream()
                .sorted(Comparator
                        .comparing((AppFunction f) -> f.getModule().getId())
                        .thenComparing(f -> f.getSortOrder() == null ? 999 : f.getSortOrder())
                        .thenComparing(AppFunction::getId))
                .toList();

        return allModules.stream().map(module -> {
            AppModuleDto moduleDto = new AppModuleDto();
            moduleDto.setId(module.getId());
            moduleDto.setCode(module.getCode());
            moduleDto.setName(module.getName());
            List<AppFunctionDto> functions = allFunctions.stream()
                    .filter(function -> function.getModule().getId().equals(module.getId()))
                    .map(function -> {
                        AppFunctionDto functionDto = new AppFunctionDto();
                        functionDto.setId(function.getId());
                        functionDto.setModuleId(module.getId());
                        functionDto.setCode(function.getCode());
                        functionDto.setName(function.getName());
                        functionDto.setUrl(function.getUrl());
                        functionDto.setIcon(function.getIcon());
                        functionDto.setSortOrder(function.getSortOrder());
                        functionDto.setActionMask(permissionMap.getOrDefault(function.getId(), 0));
                        return functionDto;
                    })
                    .sorted(Comparator.comparing(function -> function.getSortOrder() == null ? 999 : function.getSortOrder()))
                    .toList();
            moduleDto.setFunctions(functions);
            return moduleDto;
        }).toList();
    }

    @Transactional
    public Long updateRolePermissions(Long roleId, UpdateRolePermissionsRequest request) {
        Role role = roleRepository.findById(roleId).orElseThrow(() -> new RuntimeException("Role not found"));
        if (isGovernedSystemRole(role)) {
            throw new IllegalStateException("System role permissions are immutable.");
        }
        if (request == null || request.getExpectedVersion() == null) {
            throw new IllegalArgumentException("expectedVersion and permissions are required.");
        }
        if (role.getVersion() == null || !Objects.equals(role.getVersion(), request.getExpectedVersion())) {
            throw new OptimisticLockingFailureException("Role permissions changed. Reload and try again.");
        }
        if (request.getPermissions() == null) {
            throw new IllegalArgumentException("permissions must be a non-null list.");
        }

        Map<Long, Integer> requestedMasks = validateAndNormalize(request);
        List<RolePermission> existingPermissions = rolePermissionRepository.findByRoleId(roleId);
        Map<Long, RolePermission> existingByFunctionId = existingPermissions.stream()
                .collect(Collectors.toMap(permission -> permission.getFunction().getId(), permission -> permission));
        String previousState = permissionSnapshot(existingPermissions);

        for (Map.Entry<Long, Integer> entry : requestedMasks.entrySet()) {
            int actionMask = entry.getValue();
            RolePermission existing = existingByFunctionId.get(entry.getKey());
            if (actionMask == 0) {
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
            AppFunction function = appFunctionRepository.findById(entry.getKey())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown function id: " + entry.getKey()));
            RolePermission permission = new RolePermission();
            permission.setRole(role);
            permission.setFunction(function);
            permission.setActionMask(actionMask);
            rolePermissionRepository.save(permission);
        }

        existingPermissions.stream()
                .filter(permission -> !requestedMasks.containsKey(permission.getFunction().getId()))
                .forEach(rolePermissionRepository::delete);

        role.setUpdatedAt(LocalDateTime.now());
        Role saved = roleRepository.saveAndFlush(role);
        Long resultingVersion = saved.getVersion() == null ? role.getVersion() + 1 : saved.getVersion();
        String nextState = permissionSnapshot(requestedMasks);
        rolePermissionAuditRepository.save(new RolePermissionAudit(
                roleId,
                currentActorId(),
                request.getExpectedVersion(),
                resultingVersion,
                previousState,
                nextState,
                LocalDateTime.now()));
        if (operationalAuditService != null) {
            operationalAuditService.append(new OperationalAuditService.AuditCommand(
                    "SYSTEM", null, "ROLE", "ROLE_PERMISSIONS_UPDATED", "ROLE", String.valueOf(roleId),
                    null, null, "Role permission matrix updated",
                    Map.of("version", request.getExpectedVersion(), "permissions", previousState),
                    Map.of("version", resultingVersion, "permissions", nextState), null));
        }
        return resultingVersion;
    }

    private Map<Long, Integer> validateAndNormalize(UpdateRolePermissionsRequest request) {
        if (request.getPermissions() == null) {
            throw new IllegalArgumentException("permissions must be a non-null list.");
        }
        Map<Long, Integer> requestedMasks = new TreeMap<>();
        Set<Long> seenFunctionIds = new HashSet<>();
        for (UpdateRolePermissionsRequest.PermissionEntry entry : request.getPermissions()) {
            if (entry == null || entry.getFunctionId() == null || entry.getActionMask() == null) {
                throw new IllegalArgumentException("Each permission entry requires functionId and actionMask.");
            }
            if (!seenFunctionIds.add(entry.getFunctionId())) {
                throw new IllegalArgumentException("Duplicate function id: " + entry.getFunctionId());
            }
            int actionMask = entry.getActionMask();
            if (actionMask < 0 || (actionMask & ~SUPPORTED_ACTION_MASK) != 0) {
                throw new IllegalArgumentException("Action mask contains unsupported actions.");
            }
            appFunctionRepository.findById(entry.getFunctionId())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown function id: " + entry.getFunctionId()));
            requestedMasks.put(entry.getFunctionId(), actionMask);
        }
        return requestedMasks;
    }

    private boolean isGovernedSystemRole(Role role) {
        return Boolean.TRUE.equals(role.getSystemRole())
                || RoleService.SYSTEM_ROLE_CODES.contains(role.getCode());
    }

    private String permissionSnapshot(List<RolePermission> permissions) {
        Map<Long, Integer> values = new TreeMap<>();
        permissions.forEach(permission -> values.put(
                permission.getFunction().getId(), permission.getActionMask() == null ? 0 : permission.getActionMask()));
        return permissionSnapshot(values);
    }

    private String permissionSnapshot(Map<Long, Integer> permissions) {
        try {
            return objectMapper.writeValueAsString(new LinkedHashMap<>(permissions));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize role permission audit snapshot.", exception);
        }
    }

    private Long currentActorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails details) {
            return details.getUserId();
        }
        return null;
    }
}
