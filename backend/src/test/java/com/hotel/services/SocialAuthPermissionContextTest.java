package com.hotel.services;

import com.hotel.dtos.AuthResponse;
import com.hotel.entities.AppFunction;
import com.hotel.entities.Role;
import com.hotel.entities.RolePermission;
import com.hotel.entities.User;
import com.hotel.repositories.AppFunctionRepository;
import com.hotel.repositories.AppModuleRepository;
import com.hotel.repositories.RoleRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.security.JwtTokenProvider;
import com.hotel.services.social.FacebookIdentityVerifier;
import com.hotel.services.social.GoogleIdentityVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocialAuthPermissionContextTest {

    @Mock AuthenticationManager authenticationManager;
    @Mock UserRepository users;
    @Mock RoleRepository roles;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtTokenProvider tokenProvider;
    @Mock AppModuleRepository modules;
    @Mock AppFunctionRepository functions;
    @Mock EmailService emailService;
    @Mock GoogleIdentityVerifier google;
    @Mock FacebookIdentityVerifier facebook;
    @Mock SocialAccountLinkService links;

    @Test
    void socialAuthResponseAggregatesTheSameRolePermissionMasks() {
        AppFunction function = new AppFunction();
        function.setCode("BOOKING_VIEW");
        Role role = new Role();
        role.setCode("RECEPTIONIST");
        RolePermission first = new RolePermission();
        first.setFunction(function);
        first.setActionMask(1);
        RolePermission second = new RolePermission();
        second.setFunction(function);
        second.setActionMask(4);
        role.setRolePermissions(Set.of(first, second));

        User user = new User();
        user.setId(77L);
        user.setUsername("receptionist@example.com");
        user.setStatus("ACTIVE");
        user.setRoles(Set.of(role));
        when(tokenProvider.generateToken(any())).thenReturn("social-token");

        AuthService service = new AuthService(
                authenticationManager, users, roles, passwordEncoder, tokenProvider,
                modules, functions, emailService, google, facebook, links);

        AuthResponse response = service.createSocialAuthResponse(user);

        assertEquals("social-token", response.getAccessToken());
        assertEquals(77L, response.getUserId());
        assertEquals(1, response.getPermissions().size());
        assertEquals("BOOKING_VIEW", response.getPermissions().get(0).getFunction());
        assertEquals(5, response.getPermissions().get(0).getActionMask());
    }
}
