package com.hotel.services;

import com.hotel.entities.User;
import com.hotel.dtos.UserDto;
import com.hotel.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Comparator;
import java.util.stream.Collectors;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Autowired
    private com.hotel.repositories.RoleRepository roleRepository;

    @Autowired
    private com.hotel.repositories.HotelRepository hotelRepository;

    public List<UserDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public Optional<UserDto> getUserById(Long id) {
        return userRepository.findById(id).map(this::convertToDto);
    }

    public Optional<User> getEntityById(Long id) {
        return userRepository.findById(id);
    }

    public UserDto createUser(User user, java.util.Set<Long> roleIds, Long hotelId) {
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new RuntimeException("Username is already taken!");
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Email is already taken!");
        }

        user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash() != null ? user.getPasswordHash() : "123456"));
        user.setCreatedAt(java.time.LocalDateTime.now());
        
        if (roleIds != null && !roleIds.isEmpty()) {
            user.setRoles(new java.util.HashSet<>(roleRepository.findAllById(roleIds)));
        }

        if (hotelId != null) {
            user.setHotel(hotelRepository.findById(hotelId).orElse(null));
        }

        return convertToDto(userRepository.save(user));
    }

    public UserDto updateUser(Long id, User userDetails, java.util.Set<Long> roleIds, Long hotelId) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setFullName(userDetails.getFullName());
        user.setPhone(userDetails.getPhone());
        user.setStatus(userDetails.getStatus());

        if (userDetails.getPasswordHash() != null && !userDetails.getPasswordHash().isEmpty()) {
            user.setPasswordHash(passwordEncoder.encode(userDetails.getPasswordHash()));
        }

        if (roleIds != null) {
            user.setRoles(new java.util.HashSet<>(roleRepository.findAllById(roleIds)));
        }

        if (hotelId != null) {
            user.setHotel(hotelRepository.findById(hotelId).orElse(null));
        } else {
            user.setHotel(null);
        }

        return convertToDto(userRepository.save(user));
    }

    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

    public void changePassword(Long id, String currentPassword, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new RuntimeException("Mật khẩu hiện tại không đúng!");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private UserDto convertToDto(User user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setFullName(user.getFullName());
        dto.setPhone(user.getPhone());
        dto.setAvatarUrl(user.getAvatarUrl());
        dto.setStatus(user.getStatus());
        dto.setPoints(user.getPoints());
        dto.setCreatedAt(user.getCreatedAt());

        if (user.getRoles() != null) {
            dto.setRoles(user.getRoles().stream()
                    .sorted(Comparator.comparing(role -> role.getCode() == null ? "" : role.getCode()))
                    .map(role -> {
                        UserDto.RoleSummary summary = new UserDto.RoleSummary();
                        summary.setId(role.getId());
                        summary.setCode(role.getCode());
                        summary.setName(role.getName());
                        return summary;
                    })
                    .collect(Collectors.toList()));
        }

        if (user.getHotel() != null) {
            UserDto.HotelSummary hotel = new UserDto.HotelSummary();
            hotel.setId(user.getHotel().getId());
            hotel.setName(user.getHotel().getName());
            dto.setHotel(hotel);
        }

        return dto;
    }
}
