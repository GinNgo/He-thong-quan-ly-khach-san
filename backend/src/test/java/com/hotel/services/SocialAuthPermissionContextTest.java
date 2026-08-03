package com.hotel.services;

import com.hotel.dtos.AuthResponse;
import com.hotel.entities.User;
import com.hotel.repositories.AppFunctionRepository;
import com.hotel.repositories.AppModuleRepository;
import com.hotel.repositories.RoleRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.security.JwtTokenProvider;
import com.hotel.security.ActionCode;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.CustomUserDetailsService;
import com.hotel.security.FunctionCode;
import com.hotel.services.social.FacebookIdentityVerifier;
import com.hotel.services.social.GoogleIdentityVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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
    @Mock GoogleIdentityVerifier google;
    @Mock FacebookIdentityVerifier facebook;
    @Mock SocialAccountLinkService links;
    @Mock CustomUserDetailsService userDetailsService;

    @Test
    void socialAuthResponseAggregatesTheSameRolePermissionMasks() {
        User user = new User();
        user.setId(77L);
        user.setUsername("receptionist@example.com");
        user.setStatus("ACTIVE");
        CustomUserDetails details = new CustomUserDetails(
                user.getUsername(),
                "social-password-placeholder",
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("RECEPTIONIST")),
                java.util.Map.of(FunctionCode.BOOKING, ActionCode.VIEW | ActionCode.UPDATE),
                user.getId(),
                5L,
                java.util.Map.of());
        when(userDetailsService.loadUserByUsername(user.getUsername())).thenReturn(details);
        when(tokenProvider.generateToken(any())).thenReturn("social-token");

        AuthService service = new AuthService(
                authenticationManager, users, roles, passwordEncoder, tokenProvider,
                modules, functions, google, facebook, links, userDetailsService);

        AuthResponse response = service.createSocialAuthResponse(user);

        assertEquals("social-token", response.getAccessToken());
        assertEquals(77L, response.getUserId());
        assertEquals(1, response.getPermissions().size());
        assertEquals(java.util.List.of("RECEPTIONIST"), response.getRoles());
        assertEquals("BOOKING", response.getPermissions().get(0).getFunction());
        assertEquals(5, response.getPermissions().get(0).getActionMask());
        assertSame(details, org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal());
    }
}
