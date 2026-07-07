package com.hotel.services;

import com.hotel.entities.User;
import com.hotel.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

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

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    public User createUser(User user, java.util.Set<Long> roleIds, Long hotelId) {
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

        return userRepository.save(user);
    }

    public User updateUser(Long id, User userDetails, java.util.Set<Long> roleIds, Long hotelId) {
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

        return userRepository.save(user);
    }

    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }
}
