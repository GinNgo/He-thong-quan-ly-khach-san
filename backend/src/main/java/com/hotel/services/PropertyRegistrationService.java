package com.hotel.services;

import com.hotel.entities.*;
import com.hotel.repositories.*;
import com.hotel.dtos.RegisterRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PropertyRegistrationService {

    private final UserRepository userRepository;
    private final HotelRepository hotelRepository;
    private final UserPropertyRepository userPropertyRepository;
    private final SubscriptionPlanRepository planRepository;
    private final AccountSubscriptionRepository accountSubscriptionRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final PropertyOwnershipLifecycleService ownershipLifecycleService;

    @Autowired(required = false)
    private OperationalAuditService operationalAuditService;

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
        // 2. Create Property
        Hotel property = new Hotel();
        property.setName(propertyName);
        property.setAddressLine(propertyAddress);
        property.setStatus("DRAFT");
        property.setApprovalStatus("PENDING_APPROVAL");
        property.setOperationStatus("INACTIVE");
        property = hotelRepository.save(property);

        // 3. Map User to Property
        ownershipLifecycleService.createPendingOwner(user, property);

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

    @Transactional
    public Hotel approveProperty(Long propertyId) {
        Hotel before = hotelRepository.findById(propertyId).orElse(null);
        Hotel saved = ownershipLifecycleService.approveProperty(propertyId);
        audit("PROPERTY_APPROVED", saved, before, "Property approval completed");
        return saved;
    }

    @Transactional
    public Hotel rejectProperty(Long propertyId) {
        Hotel before = hotelRepository.findById(propertyId).orElse(null);
        Hotel saved = ownershipLifecycleService.rejectProperty(propertyId);
        audit("PROPERTY_REJECTED", saved, before, "Property approval rejected");
        return saved;
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

    private void audit(String eventType, Hotel hotel, Hotel before, String reason) {
        if (operationalAuditService == null || hotel == null) return;
        operationalAuditService.append(new OperationalAuditService.AuditCommand(
                "SYSTEM", null, "PROPERTY", eventType, "HOTEL", String.valueOf(hotel.getId()),
                null, null, reason, propertySnapshot(before), propertySnapshot(hotel), null));
    }

    private java.util.Map<String, Object> propertySnapshot(Hotel hotel) {
        if (hotel == null) return null;
        return java.util.Map.of("id", hotel.getId(), "name", hotel.getName(),
                "approvalStatus", hotel.getApprovalStatus(), "operationStatus", hotel.getOperationStatus(),
                "status", hotel.getStatus());
    }
}
