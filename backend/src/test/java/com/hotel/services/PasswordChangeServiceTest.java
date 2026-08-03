package com.hotel.services;

import com.hotel.entities.User;
import com.hotel.repositories.UserRepository;
import com.hotel.security.PasswordChangeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordChangeServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthSessionRevocationService authSessionRevocationService;

    @InjectMocks
    private UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(42L);
        user.setPasswordHash("old-hash");
    }

    @Test
    void changesPasswordAndRevokesEveryExistingSession() {
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current-password", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

        userService.changePassword(42L, "current-password", "new-password");

        assertEquals("new-hash", user.getPasswordHash());
        verify(userRepository).save(user);
        verify(authSessionRevocationService).revokeUserSession(42L, "PASSWORD_CHANGE");
    }

    @Test
    void returnsStableErrorWhenCurrentPasswordIsWrong() {
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "old-hash")).thenReturn(false);

        PasswordChangeException exception = assertThrows(
                PasswordChangeException.class,
                () -> userService.changePassword(42L, "wrong-password", "new-password"));

        assertEquals(PasswordChangeException.CURRENT_PASSWORD_INVALID, exception.getCode());
        verify(userRepository, never()).save(user);
        verify(authSessionRevocationService, never()).revokeUserSession(42L, "PASSWORD_CHANGE");
    }

    @Test
    void rejectsNewPasswordOutsideTheSharedPolicy() {
        assertThrows(
                IllegalArgumentException.class,
                () -> userService.changePassword(42L, "current-password", "short"));

        verify(userRepository, never()).findById(42L);
        verify(authSessionRevocationService, never()).revokeUserSession(42L, "PASSWORD_CHANGE");
    }
}
