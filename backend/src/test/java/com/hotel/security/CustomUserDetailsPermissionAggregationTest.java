package com.hotel.security;

import com.hotel.entities.AppFunction;
import com.hotel.entities.Role;
import com.hotel.entities.RolePermission;
import com.hotel.entities.User;
import com.hotel.repositories.UserRepository;
import com.hotel.services.SubscriptionFeatureService;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomUserDetailsPermissionAggregationTest {

    @Test
    void loadsRolesAndOrAggregatesPermissionMasksFromTheServerModel() {
        UserRepository users = mock(UserRepository.class);
        SubscriptionFeatureService features = mock(SubscriptionFeatureService.class);
        CustomUserDetailsService service = new CustomUserDetailsService(users, features);

        AppFunction booking = function("BOOKING");
        Role receptionist = role("RECEPTIONIST", permission(booking, ActionCode.VIEW));
        Role supervisor = role("SUPERVISOR", permission(booking, ActionCode.UPDATE));
        Role legacy = role("LEGACY", permission(function("CLIENT_SUPPLIED_UNKNOWN"), ActionCode.DELETE));

        User user = new User();
        user.setId(41L);
        user.setUsername("staff@example.com");
        user.setPasswordHash("hash");
        user.setStatus("ACTIVE");
        user.setRoles(Set.of(receptionist, supervisor, legacy));

        when(users.findByUsername("staff@example.com")).thenReturn(Optional.of(user));
        when(features.getActiveFeaturesForUser(41L)).thenReturn(java.util.Map.of());

        CustomUserDetails details = (CustomUserDetails) service.loadUserByUsername(" STAFF@EXAMPLE.COM ");

        assertEquals(ActionCode.VIEW | ActionCode.UPDATE,
                details.getPermissionMasks().get(FunctionCode.BOOKING));
        assertEquals(Set.of("RECEPTIONIST", "SUPERVISOR", "LEGACY"),
                details.getAuthorities().stream()
                        .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                        .collect(java.util.stream.Collectors.toSet()));
        assertFalse(details.getPermissionMasks().containsKey(FunctionCode.SYSTEM));
    }

    private AppFunction function(String code) {
        AppFunction function = new AppFunction();
        function.setCode(code);
        return function;
    }

    private RolePermission permission(AppFunction function, int mask) {
        RolePermission permission = new RolePermission();
        permission.setFunction(function);
        permission.setActionMask(mask);
        return permission;
    }

    private Role role(String code, RolePermission permission) {
        Role role = new Role();
        role.setCode(code);
        role.setRolePermissions(Set.of(permission));
        return role;
    }
}
