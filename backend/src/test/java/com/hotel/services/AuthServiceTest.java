package com.hotel.services;

import com.hotel.dtos.AuthResponse;
import com.hotel.dtos.LoginRequest;
import com.hotel.dtos.RegisterRequest;
import com.hotel.entities.User;
import com.hotel.repositories.UserRepository;
import com.hotel.repositories.RoleRepository;
import com.hotel.repositories.AppFunctionRepository;
import com.hotel.repositories.AppModuleRepository;
import com.hotel.security.ActionCode;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.CustomUserDetailsService;
import com.hotel.security.FunctionCode;
import com.hotel.security.JwtTokenProvider;
import com.hotel.services.social.FacebookIdentityVerifier;
import com.hotel.services.social.GoogleIdentityVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider jwtUtil;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AppModuleRepository appModuleRepository;

    @Mock
    private AppFunctionRepository appFunctionRepository;

    @Mock
    private GoogleIdentityVerifier googleIdentityVerifier;

    @Mock
    private FacebookIdentityVerifier facebookIdentityVerifier;

    @Mock
    private SocialAccountLinkService socialAccountLinkService;

    @Mock
    private CustomUserDetailsService customUserDetailsService;

    @InjectMocks
    private AuthService authService;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUsername("testuser");
        mockUser.setPasswordHash("hashed_password");
        mockUser.setStatus("ACTIVE");
    }

    @Test
    void login_Success() {
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("password123");

        CustomUserDetails details = new CustomUserDetails(
                "testuser",
                "hashed_password",
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("PROPERTY_OWNER")),
                java.util.Map.of(FunctionCode.BOOKING, ActionCode.VIEW | ActionCode.CREATE),
                1L,
                10L,
                java.util.Map.of());
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                details, null, details.getAuthorities());
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(mockUser));
        when(jwtUtil.generateToken(any(Authentication.class))).thenReturn("mocked-jwt-token");

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("mocked-jwt-token", response.getAccessToken());
        assertEquals("testuser", response.getUsername());
        assertEquals(1L, response.getUserId());
        assertEquals(java.util.List.of("PROPERTY_OWNER"), response.getRoles());
        assertEquals(1, response.getPermissions().size());
        assertEquals("BOOKING", response.getPermissions().get(0).getFunction());
        assertEquals(3, response.getPermissions().get(0).getActionMask());
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(jwtUtil).generateToken(any(Authentication.class));
    }

    @Test
    void register_Success() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setPassword("password123");
        request.setEmail("newuser@example.com");

        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("newuser@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded_password");
        com.hotel.entities.Role role = new com.hotel.entities.Role();
        role.setCode("CUSTOMER");
        when(roleRepository.findByCode("CUSTOMER")).thenReturn(java.util.Optional.of(role));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(2L);
            return u;
        });

        String result = authService.register(request);

        assertEquals("User registered successfully!", result);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_Failure_UsernameExists() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("existinguser");

        when(userRepository.existsByUsername("existinguser")).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.register(request));
        assertEquals("Username is already taken!", ex.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }
}
