package com.hotel.services;

import com.hotel.dtos.UserDto;
import com.hotel.entities.User;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileUpdateServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserPropertyRepository userPropertyRepository;
    @InjectMocks private UserService userService;

    @Test
    void normalizesProfileFieldsAndAcceptsOwnedUploadPath() {
        User user = user();
        when(userRepository.findById(41L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(userPropertyRepository.findByUserIdAndRelationshipTypeOrderByStartDateDesc(41L, "STAFF"))
                .thenReturn(java.util.List.of());

        UserDto result = userService.updateProfile(
                41L,
                "  Nguyen    Van A  ",
                "  +84   900 000 000  ",
                "  /api/public/uploads/avatar-41.webp  ");

        assertEquals("Nguyen Van A", result.getFullName());
        assertEquals("+84 900 000 000", result.getPhone());
        assertEquals("/api/public/uploads/avatar-41.webp", result.getAvatarUrl());
        verify(userRepository).save(user);
    }

    @Test
    void acceptsAbsoluteHttpsAvatarWithoutChangingAccountIdentity() {
        User user = user();
        when(userRepository.findById(41L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(userPropertyRepository.findByUserIdAndRelationshipTypeOrderByStartDateDesc(41L, "STAFF"))
                .thenReturn(java.util.List.of());

        UserDto result = userService.updateProfile(
                41L,
                "Nguyen Van A",
                null,
                "https://cdn.example.com/avatars/customer.png");

        assertEquals("profile-owner@example.com", result.getEmail());
        assertEquals("profile-owner@example.com", result.getUsername());
        assertEquals("https://cdn.example.com/avatars/customer.png", result.getAvatarUrl());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)",
            "data:image/svg+xml,<svg></svg>",
            "file:///tmp/avatar.png",
            "//evil.example/avatar.png",
            "http://cdn.example.com/avatar.png",
            "/api/public/uploads/../secret.png"
    })
    void rejectsUnsafeAvatarSchemesAndPathsWithoutSaving(String avatarUrl) {
        User user = user();
        when(userRepository.findById(41L)).thenReturn(Optional.of(user));

        assertThrows(IllegalArgumentException.class, () -> userService.updateProfile(
                41L, "Nguyen Van A", "+84 900 000 000", avatarUrl));

        verify(userRepository, never()).save(any());
    }

    private User user() {
        User user = new User();
        user.setId(41L);
        user.setUsername("profile-owner@example.com");
        user.setEmail("profile-owner@example.com");
        user.setFullName("Profile Owner");
        user.setPhone("+84 901 000 000");
        user.setRoles(Set.of());
        user.setStatus("ACTIVE");
        return user;
    }
}
