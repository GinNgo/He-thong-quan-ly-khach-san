package com.hotel.controllers;

import com.hotel.dtos.RoleCreateRequest;
import com.hotel.dtos.RoleDto;
import com.hotel.security.ActionCode;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import com.hotel.services.RoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RoleControllerHttpTest {

    @Mock private RoleService roleService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        RoleController controller = new RoleController();
        ReflectionTestUtils.setField(controller, "roleService", roleService);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void createRole_InvalidPayload_ReturnsStructuredValidationError() throws Exception {
        mockMvc.perform(post("/api/roles")
                        .contentType("application/json")
                        .content("{\"code\":\"bad code\",\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.code").exists())
                .andExpect(jsonPath("$.fieldErrors.name").exists());

        verify(roleService, never()).createRole(any());
    }

    @Test
    void createRole_DoesNotExposeClientControlledStatusOrSystemFlag() throws Exception {
        RoleDto response = role(20L, "NIGHT_AUDITOR", "ACTIVE");
        when(roleService.createRole(any())).thenReturn(response);

        mockMvc.perform(post("/api/roles")
                        .contentType("application/json")
                        .content("{\"code\":\"NIGHT_AUDITOR\",\"name\":\"Night auditor\",\"status\":\"INACTIVE\",\"systemRole\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.systemRole").value(false));

        ArgumentCaptor<RoleCreateRequest> request = ArgumentCaptor.forClass(RoleCreateRequest.class);
        verify(roleService).createRole(request.capture());
        assertEquals("NIGHT_AUDITOR", request.getValue().getCode());
    }

    @Test
    void updateRole_InvalidPayload_Returns400BeforeService() throws Exception {
        mockMvc.perform(put("/api/roles/20")
                        .contentType("application/json")
                        .content("{\"code\":\"\",\"name\":\"Night auditor\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verify(roleService, never()).updateRole(any(), any());
    }

    @Test
    void deactivateAssignedRole_Returns409AndPreservesMessage() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalStateException("Role is assigned."))
                .when(roleService).deactivateRole(20L);

        mockMvc.perform(delete("/api/roles/20"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Role is assigned."));
    }

    @Test
    void lifecycleRoutes_UseDeleteForDeactivationAndUpdateForReactivation() throws Exception {
        Method deactivate = RoleController.class.getMethod("deleteRole", Long.class);
        Method reactivate = RoleController.class.getMethod("reactivateRole", Long.class);

        Permission deactivatePermission = deactivate.getAnnotation(Permission.class);
        Permission reactivatePermission = reactivate.getAnnotation(Permission.class);
        assertEquals(FunctionCode.ROLE, deactivatePermission.function());
        assertEquals(ActionCode.DELETE, deactivatePermission.action());
        assertEquals(FunctionCode.ROLE, reactivatePermission.function());
        assertEquals(ActionCode.UPDATE, reactivatePermission.action());
    }

    private RoleDto role(Long id, String code, String status) {
        RoleDto dto = new RoleDto();
        dto.setId(id);
        dto.setCode(code);
        dto.setName("Night auditor");
        dto.setStatus(status);
        dto.setSystemRole(false);
        dto.setRoleType("CUSTOM");
        dto.setUserCount(0L);
        return dto;
    }
}
