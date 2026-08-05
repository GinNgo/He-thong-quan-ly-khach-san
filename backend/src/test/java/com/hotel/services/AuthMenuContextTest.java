package com.hotel.services;

import com.hotel.controllers.AuthController;
import com.hotel.entities.AppFunction;
import com.hotel.entities.AppModule;
import com.hotel.repositories.AppFunctionRepository;
import com.hotel.repositories.AppModuleRepository;
import com.hotel.repositories.RoleRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.security.ActionCode;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.CustomUserDetailsService;
import com.hotel.security.FunctionCode;
import com.hotel.security.JwtTokenProvider;
import com.hotel.services.social.FacebookIdentityVerifier;
import com.hotel.services.social.GoogleIdentityVerifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthMenuContextTest {

    @BeforeEach
    void resetContextBeforeTest() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void menuUsesOnlyAuthoritativePrincipalMasksAndDoesNotReloadClientContext() {
        UserRepository users = mock(UserRepository.class);
        AppModuleRepository modules = mock(AppModuleRepository.class);
        AppFunctionRepository functions = mock(AppFunctionRepository.class);
        AuthService service = service(users, modules, functions);

        AppModule module = new AppModule();
        module.setId(1L);
        module.setCode("OPERATIONS");
        module.setName("Operations");
        AppFunction booking = menuFunction(11L, module, "BOOKING", "/admin/bookings", 1);
        AppFunction system = menuFunction(12L, module, "SYSTEM", "/admin/system", 2);
        when(modules.findAll()).thenReturn(new ArrayList<>(List.of(module)));
        when(functions.findAll()).thenReturn(new ArrayList<>(List.of(booking, system)));

        CustomUserDetails details = new CustomUserDetails(
                "staff@example.com",
                "hash",
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("RECEPTIONIST")),
                Map.of(FunctionCode.BOOKING, ActionCode.VIEW),
                9L,
                3L,
                Map.of());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities()));

        var menu = service.getMyMenu();

        assertEquals(1, menu.size());
        assertEquals(List.of("BOOKING"), menu.get(0).getFunctions().stream().map(f -> f.getCode()).toList());
        verify(users, never()).findByUsername(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void menuRequiresAuthenticationInBothControllerAndService() throws Exception {
        AuthService service = service(mock(UserRepository.class), mock(AppModuleRepository.class),
                mock(AppFunctionRepository.class));

        assertThrows(AuthenticationCredentialsNotFoundException.class, service::getMyMenu);
        PreAuthorize annotation = AuthController.class.getMethod("getMyMenu").getAnnotation(PreAuthorize.class);
        assertEquals("isAuthenticated()", annotation.value());
    }

    private AuthService service(UserRepository users, AppModuleRepository modules,
                                AppFunctionRepository functions) {
        return new AuthService(
                mock(org.springframework.security.authentication.AuthenticationManager.class),
                users,
                mock(RoleRepository.class),
                mock(org.springframework.security.crypto.password.PasswordEncoder.class),
                mock(JwtTokenProvider.class),
                modules,
                functions,
                mock(GoogleIdentityVerifier.class),
                mock(FacebookIdentityVerifier.class),
                mock(SocialAccountLinkService.class),
                mock(CustomUserDetailsService.class));
    }

    private AppFunction menuFunction(Long id, AppModule module, String code, String url, int order) {
        AppFunction function = new AppFunction();
        function.setId(id);
        function.setModule(module);
        function.setCode(code);
        function.setName(code);
        function.setUrl(url);
        function.setSortOrder(order);
        return function;
    }
}
