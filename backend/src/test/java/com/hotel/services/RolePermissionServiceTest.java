package com.hotel.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.dtos.UpdateRolePermissionsRequest;
import com.hotel.entities.AppFunction;
import com.hotel.entities.AppModule;
import com.hotel.entities.Role;
import com.hotel.entities.RolePermissionAudit;
import com.hotel.repositories.AppFunctionRepository;
import com.hotel.repositories.AppModuleRepository;
import com.hotel.repositories.RolePermissionAuditRepository;
import com.hotel.repositories.RolePermissionRepository;
import com.hotel.repositories.RoleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RolePermissionServiceTest {
    @Mock private RolePermissionRepository rolePermissionRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private AppModuleRepository appModuleRepository;
    @Mock private AppFunctionRepository appFunctionRepository;
    @Mock private RolePermissionAuditRepository rolePermissionAuditRepository;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private RolePermissionService service;

    private Role role;
    private AppFunction function;

    @BeforeEach
    void setUp() {
        role = new Role();
        role.setId(3L);
        role.setCode("CUSTOM_RECEPTION");
        role.setVersion(0L);
        role.setSystemRole(false);

        AppModule module = new AppModule();
        module.setId(1L);
        function = new AppFunction();
        function.setId(7L);
        function.setCode("ROOM");
        function.setModule(module);
    }

    @AfterEach
    void clearSecurityContext() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsGovernedRoleEvenWhenSystemFlagIsStale() {
        role.setCode("ADMIN");
        when(roleRepository.findById(3L)).thenReturn(Optional.of(role));

        assertThrows(IllegalStateException.class, () -> service.updateRolePermissions(3L, request(0L, 7L, 1)));
        verify(rolePermissionRepository, never()).findByRoleId(3L);
    }

    @Test
    void rejectsNullPayloadAndPermissionList() {
        when(roleRepository.findById(3L)).thenReturn(Optional.of(role));

        assertThrows(IllegalArgumentException.class, () -> service.updateRolePermissions(3L, null));
        UpdateRolePermissionsRequest request = new UpdateRolePermissionsRequest();
        request.setExpectedVersion(0L);
        assertThrows(IllegalArgumentException.class, () -> service.updateRolePermissions(3L, request));
    }

    @Test
    void rejectsDuplicateUnknownAndUnsupportedEntriesBeforeMutation() {
        when(roleRepository.findById(3L)).thenReturn(Optional.of(role));
        when(appFunctionRepository.findById(7L)).thenReturn(Optional.of(function));

        UpdateRolePermissionsRequest duplicate = request(0L, 7L, 1);
        duplicate.getPermissions().add(entry(7L, 2));
        assertThrows(IllegalArgumentException.class, () -> service.updateRolePermissions(3L, duplicate));

        assertThrows(IllegalArgumentException.class,
                () -> service.updateRolePermissions(3L, request(0L, 99L, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> service.updateRolePermissions(3L, request(0L, 7L, 64)));
        verify(rolePermissionRepository, never()).findByRoleId(3L);
    }

    @Test
    void rejectsStaleVersionAndDoesNotMutatePermissions() {
        when(roleRepository.findById(3L)).thenReturn(Optional.of(role));

        assertThrows(OptimisticLockingFailureException.class,
                () -> service.updateRolePermissions(3L, request(9L, 7L, 1)));
        verify(rolePermissionRepository, never()).findByRoleId(3L);
    }

    @Test
    void savesMatrixVersionAndAppendOnlyAudit() throws Exception {
        when(roleRepository.findById(3L)).thenReturn(Optional.of(role));
        when(rolePermissionRepository.findByRoleId(3L)).thenReturn(List.of());
        when(appFunctionRepository.findById(7L)).thenReturn(Optional.of(function));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(roleRepository.saveAndFlush(role)).thenAnswer(invocation -> {
            role.setVersion(1L);
            return role;
        });

        Long resultingVersion = service.updateRolePermissions(3L, request(0L, 7L, 5));

        assertEquals(1L, resultingVersion);
        verify(rolePermissionAuditRepository).save(any(RolePermissionAudit.class));
        verify(roleRepository).saveAndFlush(role);
    }

    private UpdateRolePermissionsRequest request(Long expectedVersion, Long functionId, Integer actionMask) {
        UpdateRolePermissionsRequest request = new UpdateRolePermissionsRequest();
        request.setExpectedVersion(expectedVersion);
        request.setPermissions(new java.util.ArrayList<>(List.of(entry(functionId, actionMask))));
        return request;
    }

    private UpdateRolePermissionsRequest.PermissionEntry entry(Long functionId, Integer actionMask) {
        UpdateRolePermissionsRequest.PermissionEntry entry = new UpdateRolePermissionsRequest.PermissionEntry();
        entry.setFunctionId(functionId);
        entry.setActionMask(actionMask);
        return entry;
    }
}
