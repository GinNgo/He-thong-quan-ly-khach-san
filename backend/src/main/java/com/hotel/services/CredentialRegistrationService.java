package com.hotel.services;

import com.hotel.dtos.RegisterRequest;
import com.hotel.dtos.RegistrationResponse;
import com.hotel.entities.Role;
import com.hotel.entities.User;
import com.hotel.exceptions.RegistrationConflictException;
import com.hotel.repositories.RoleRepository;
import com.hotel.repositories.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Locale;

@Service
public class CredentialRegistrationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;

    public CredentialRegistrationService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            EmailVerificationService emailVerificationService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationService = emailVerificationService;
    }

    public RegistrationResponse register(RegisterRequest request) {
        String username = normalizeIdentifier(request.getUsername());
        if (existsUsername(username)) {
            throw RegistrationConflictException.username();
        }

        String email = normalizeIdentifier(request.getEmail());
        if (existsEmail(email)) {
            throw RegistrationConflictException.email();
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(normalizeDisplayText(request.getFullName()));
        user.setPhone(normalizeOptionalText(request.getPhone()));
        user.setStatus("ACTIVE");
        user.setEmailVerifiedAt(null);
        user.setCreatedAt(LocalDateTime.now());

        Role customerRole = roleRepository.findByCode("CUSTOMER")
                .orElseThrow(() -> new IllegalStateException("Customer role is not configured."));
        user.setRoles(Collections.singleton(customerRole));

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            if (existsUsername(username)) {
                throw RegistrationConflictException.username();
            }
            if (existsEmail(email)) {
                throw RegistrationConflictException.email();
            }
            throw exception;
        }

        boolean verificationEmailSent = emailVerificationService.requestInitialVerification(user);
        return new RegistrationResponse("User registered successfully!", false, verificationEmailSent);
    }

    private boolean existsUsername(String username) {
        return userRepository.existsByUsernameIgnoreCase(username)
                || userRepository.existsByUsername(username);
    }

    private boolean existsEmail(String email) {
        return userRepository.existsByEmailIgnoreCase(email)
                || userRepository.existsByEmail(email);
    }

    private String normalizeIdentifier(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private String normalizeDisplayText(String value) {
        return value == null ? null : value.strip().replaceAll("\\s+", " ");
    }

    private String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.strip().replaceAll("\\s+", " ");
    }
}
