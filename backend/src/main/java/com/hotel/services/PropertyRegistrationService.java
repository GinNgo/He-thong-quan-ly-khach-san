package com.hotel.services;

import com.hotel.entities.*;
import com.hotel.repositories.*;
import com.hotel.dtos.RegisterRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PropertyRegistrationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final HotelRepository hotelRepository;
    private final UserPropertyRepository userPropertyRepository;
    private final SubscriptionPlanRepository planRepository;
    private final AccountSubscriptionRepository accountSubscriptionRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Transactional
    public User registerPropertyOwner(String email, String password, String fullName, String phone,
                                      String propertyName, String propertyAddress, String authenticatedUsername) {

        // 1. Create or get User
        User user = userRepository.findByEmail(email).orElse(null);
        if (user != null && (authenticatedUsername == null
                || (!authenticatedUsername.equalsIgnoreCase(user.getUsername())
                && !authenticatedUsername.equalsIgnoreCase(user.getEmail())))) {
            throw new IllegalArgumentException("Email đã được sử dụng. Vui lòng đăng nhập đúng tài khoản để đăng ký đối tác.");
        }
        if (user == null) {
            if (password == null || password.length() < 6) {
                throw new IllegalArgumentException("Mật khẩu phải có ít nhất 6 ký tự.");
            }
            user = new User();
            user.setUsername(email); // Use email as username
            user.setEmail(email);
            user.setFullName(fullName);
            user.setPhone(phone);
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setStatus("ACTIVE");
            user.setCreatedAt(LocalDateTime.now());

            user = userRepository.save(user);
        }

        if (userPropertyRepository.findByUserId(user.getId()).stream()
                .anyMatch(mapping -> "PENDING_APPROVAL".equals(mapping.getHotel().getApprovalStatus()))) {
            throw new IllegalStateException("Hồ sơ đối tác của tài khoản đang chờ duyệt.");
        }
        Role ownerRole = roleRepository.findByCode("PROPERTY_OWNER")
                .orElseThrow(() -> new RuntimeException("Role PROPERTY_OWNER not found in DB"));
        java.util.Set<Role> roles = user.getRoles() == null
                ? new java.util.HashSet<>() : new java.util.HashSet<>(user.getRoles());
        roles.add(ownerRole);
        user.setRoles(roles);
        user = userRepository.save(user);

        // 2. Create Property
        Hotel property = new Hotel();
        property.setName(propertyName);
        property.setAddressLine(propertyAddress);
        property.setStatus("DRAFT");
        property.setApprovalStatus("PENDING_APPROVAL");
        property.setOperationStatus("INACTIVE");
        property = hotelRepository.save(property);

        // 3. Map User to Property
        UserProperty up = new UserProperty();
        up.setUser(user);
        up.setHotel(property);
        up.setRelationshipType("OWNER");
        up.setIsPrimaryOwner(true);
        up.setStatus("ACTIVE");
        up.setStartDate(LocalDateTime.now());
        userPropertyRepository.save(up);

        // 4. Assign Default Plan (e.g. BASIC)
        SubscriptionPlan basicPlan = planRepository.findByCode("BASIC").orElse(null);
        if (basicPlan != null) {
            AccountSubscription sub = new AccountSubscription();
            sub.setUser(user);
            sub.setPlan(basicPlan);
            sub.setStartAt(LocalDateTime.now());
            sub.setIsLifetime(true);
            sub.setStatus("ACTIVE");
            accountSubscriptionRepository.save(sub);
        }

        return user;
    }

    @Transactional(readOnly = true)
    public java.util.Map<String, Object> registrationStatus(String username) {
        User user = userRepository.findByUsername(username)
                .or(() -> userRepository.findByEmail(username))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản."));
        var mappings = userPropertyRepository.findByUserId(user.getId());
        String status = mappings.stream().anyMatch(item -> "PENDING_APPROVAL".equals(item.getHotel().getApprovalStatus()))
                ? "PENDING" : mappings.isEmpty() ? "NONE" : "APPROVED";
        return java.util.Map.of("status", status, "propertyCount", mappings.size());
    }
}
