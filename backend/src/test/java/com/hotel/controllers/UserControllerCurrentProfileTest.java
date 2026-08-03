package com.hotel.controllers;

import com.hotel.dtos.UserDto;
import com.hotel.security.CustomUserDetails;
import com.hotel.services.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerCurrentProfileTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController controller;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void readsOnlyTheAuthenticatedUsersProfile() {
        authenticate(41L, "current@example.com");
        UserDto expected = new UserDto();
        expected.setId(41L);
        expected.setUsername("current@example.com");
        when(userService.getUserWithSaaSContext(41L)).thenReturn(Optional.of(expected));

        ResponseEntity<UserDto> response = controller.getCurrentUser();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
        verify(userService).getUserWithSaaSContext(41L);
        verify(userService, never()).getUserWithSaaSContext(99L);
    }

    @Test
    void returnsNotFoundWhenTheAuthenticatedProfileNoLongerExists() {
        authenticate(52L, "removed@example.com");
        when(userService.getUserWithSaaSContext(52L)).thenReturn(Optional.empty());

        ResponseEntity<UserDto> response = controller.getCurrentUser();

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(userService).getUserWithSaaSContext(52L);
    }

    private void authenticate(Long userId, String username) {
        CustomUserDetails principal = new CustomUserDetails(
                username,
                "password",
                List.of(),
                Map.of(),
                userId,
                null,
                Map.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
