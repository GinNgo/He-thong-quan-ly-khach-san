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
        if (userDetails.getEmail() != null && !userDetails.getEmail().equalsIgnoreCase(user.getEmail())) {
            if (userRepository.existsByEmail(userDetails.getEmail())) {
                throw new RuntimeException("Email is already taken!");
            }
            user.setEmail(userDetails.getEmail());
        }
        user.setPhone(userDetails.getPhone());
        if (userDetails.getAvatarUrl() != null) {
            user.setAvatarUrl(userDetails.getAvatarUrl());
        }
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

    public UserDto updateProfile(Long id, String fullName, String email, String phone, String avatarUrl) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (email != null && !email.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email is already taken!");
        }

        user.setFullName(fullName);
        user.setEmail(email);
        user.setPhone(phone);
        user.setAvatarUrl(avatarUrl);
        return convertToDto(userRepository.save(user));
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

    @Autowired
    private com.hotel.repositories.AccountSubscriptionRepository accountSubscriptionRepository;

    @Autowired
    private com.hotel.repositories.UserPropertyRepository userPropertyRepository;

    @Autowired
    private SubscriptionFeatureService subscriptionFeatureService;

    @Autowired
    private com.hotel.repositories.ChatMessageRepository chatMessageRepository;

    @Autowired
    private com.hotel.repositories.ReservationRepository reservationRepository;

    @Autowired
    private com.hotel.repositories.PropertyClaimRequestRepository propertyClaimRequestRepository;

    public Optional<UserDto> getUserWithSaaSContext(Long id) {
        Optional<User> optionalUser = userRepository.findById(id);
        if (optionalUser.isEmpty()) return Optional.empty();

        User user = optionalUser.get();
        UserDto dto = convertToDto(user);

        // Fetch assigned properties
        List<com.hotel.entities.UserProperty> userProperties = userPropertyRepository.findByUserId(id);
        List<UserDto.HotelSummary> properties = userProperties.stream().map(up -> {
            UserDto.HotelSummary hs = new UserDto.HotelSummary();
            hs.setId(up.getHotel().getId());
            hs.setName(up.getHotel().getName());
            return hs;
        }).collect(Collectors.toList());
        dto.setAssignedProperties(properties);
        dto.setUnreadMessageCount(chatMessageRepository.countByReceiverIdAndIsReadFalse(id));
        dto.setPendingBookingCount(reservationRepository.countByUserIdAndStatusIn(
                id, java.util.List.of("DRAFT", "PENDING", "PENDING_PAYMENT", "CONFIRMED")));
        if (!userProperties.isEmpty()) {
            boolean pending = userProperties.stream().anyMatch(up -> "PENDING_APPROVAL".equals(up.getHotel().getApprovalStatus()));
            dto.setPartnerRegistrationStatus(pending ? "PENDING" : "APPROVED");
        } else {
            dto.setPartnerRegistrationStatus(propertyClaimRequestRepository
                    .findFirstByRequesterUserIdOrderByCreatedAtDesc(id)
                    .map(com.hotel.entities.PropertyClaimRequest::getStatus).orElse("NONE"));
        }

        // Fetch active subscription
        List<com.hotel.entities.AccountSubscription> subs = accountSubscriptionRepository.findByUserIdAndStatus(id, "ACTIVE");
        if (!subs.isEmpty()) {
            com.hotel.entities.AccountSubscription activeSub = subs.get(0);
            dto.setPlan(activeSub.getPlan().getCode());
            dto.setSubscriptionStatus(activeSub.getStatus());
            dto.setStartAt(activeSub.getStartAt());
            dto.setEndAt(activeSub.getEndAt());
            dto.setIsLifetime(activeSub.getIsLifetime());

            // Get limits
            java.util.Map<String, Integer> limits = subscriptionFeatureService.getActiveFeaturesForUser(id);
            dto.setLimits(limits);

            // Current usage mock (this would normally calculate from DB based on User limit)
            java.util.Map<String, Integer> currentUsage = new java.util.HashMap<>();
            currentUsage.put("MAX_PROPERTIES", userProperties.size());
            dto.setCurrentUsage(currentUsage);
        } else {
            dto.setSubscriptionStatus("FREE");
        }

        return Optional.of(dto);
    }
}
