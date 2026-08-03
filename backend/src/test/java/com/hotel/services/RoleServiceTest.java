package com.hotel.services;

import com.hotel.dtos.RoleCreateRequest;
import com.hotel.dtos.RoleDto;
import com.hotel.dtos.RoleUpdateRequest;
import com.hotel.entities.Role;
import com.hotel.repositories.RolePermissionRepository;
import com.hotel.repositories.RoleRepository;
import com.hotel.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock private RoleRepository roleRepository;
    @Mock private RolePermissionRepository rolePermissionRepository;
    @Mock private UserRepository userRepository;
    @Mock private OperationalAuditService operationalAuditService;

    @InjectMocks private RoleService roleService;

    private Role customRole;

    @BeforeEach
    void setUp() {
        customRole = new Role();
        customRole.setId(20L);
        customRole.setCode("NIGHT_AUDITOR");
        customRole.setName("Night auditor");
        customRole.setDescription("Night shift");
        customRole.setStatus("ACTIVE");
        customRole.setSystemRole(false);
        customRole.setVersion(0L);
    }

    @Test
    void createRole_NormalizesGlobalTemplateAndOwnsLifecycleFields() {
        RoleCreateRequest request = createRequest(" night_auditor ", "  Night auditor  ", "  Night shift  ");
        when(roleRepository.findByCodeIgnoreCase("NIGHT_AUDITOR")).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> {
            Role saved = invocation.getArgument(0);
            saved.setId(20L);
            saved.setVersion(0L);
            return saved;
        });

        RoleDto result = roleService.createRole(request);

        assertEquals("NIGHT_AUDITOR", result.getCode());
        assertEquals("Night auditor", result.getName());
        assertEquals("Night shift", result.getDescription());
        assertEquals("ACTIVE", result.getStatus());
        assertFalse(result.getSystemRole());
        assertEquals("CUSTOM", result.getRoleType());
        verify(roleRepository).findByCodeIgnoreCase("NIGHT_AUDITOR");
    }

    @Test
    void createRole_RejectsCaseInsensitiveGlobalCodeCollision() {
        RoleCreateRequest request = createRequest("night_auditor", "Duplicate", "");
        when(roleRepository.findByCodeIgnoreCase("NIGHT_AUDITOR")).thenReturn(Optional.of(customRole));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> roleService.createRole(request));

        assertEquals("Mã vai trò đã tồn tại.", error.getMessage());
        verify(roleRepository, never()).save(any());
    }

    @Test
    void updateRole_LocksRowAndCannotChangeStatusThroughMetadataRequest() {
        customRole.setStatus("INACTIVE");
        RoleUpdateRequest request = updateRequest(" night_auditor ", "  Night operations  ", "  Updated  ");
        when(roleRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(customRole));
        when(roleRepository.findByCodeIgnoreCase("NIGHT_AUDITOR")).thenReturn(Optional.of(customRole));
        when(roleRepository.save(customRole)).thenReturn(customRole);

        RoleDto result = roleService.updateRole(20L, request);

        assertEquals("Night operations", result.getName());
        assertEquals("Updated", result.getDescription());
        assertEquals("INACTIVE", result.getStatus());
        verify(roleRepository).findByIdForUpdate(20L);
    }

    @Test
    void deactivateRole_WhenAssigned_RejectsWithoutMutatingCatalog() {
        when(roleRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(customRole));
        when(userRepository.countByRoleId(20L)).thenReturn(2L);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> roleService.deactivateRole(20L));

        assertEquals("Không thể ngừng sử dụng vai trò đang được gán cho người dùng.", error.getMessage());
        assertEquals("ACTIVE", customRole.getStatus());
        verify(roleRepository, never()).save(any());
    }

    @Test
    void deactivateAndReactivateRole_UseSoftLifecycleWithoutDeleting() {
        when(roleRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(customRole));
        when(userRepository.countByRoleId(20L)).thenReturn(0L);
        when(roleRepository.save(customRole)).thenReturn(customRole);

        roleService.deactivateRole(20L);
        RoleDto reactivated = roleService.reactivateRole(20L);

        assertEquals("ACTIVE", reactivated.getStatus());
        verify(roleRepository, never()).delete(any());
        verify(roleRepository, never()).deleteById(any());
    }

    @Test
    void deactivateRole_RejectsSeededSystemCodeEvenWhenFlagIsStale() {
        customRole.setCode("RECEPTIONIST");
        customRole.setSystemRole(false);
        when(roleRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(customRole));

        assertThrows(IllegalStateException.class, () -> roleService.deactivateRole(20L));

        verify(userRepository, never()).countByRoleId(any());
        verify(roleRepository, never()).save(any());
    }

    private RoleCreateRequest createRequest(String code, String name, String description) {
        RoleCreateRequest request = new RoleCreateRequest();
        request.setCode(code);
        request.setName(name);
        request.setDescription(description);
        return request;
    }

    private RoleUpdateRequest updateRequest(String code, String name, String description) {
        RoleUpdateRequest request = new RoleUpdateRequest();
        request.setCode(code);
        request.setName(name);
        request.setDescription(description);
        return request;
    }
}
