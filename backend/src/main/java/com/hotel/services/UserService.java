package com.hotel.services;

import com.hotel.entities.User;
import com.hotel.dtos.StaffLifecycleRequest;
import com.hotel.dtos.UserDto;
import com.hotel.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.net.URI;
import java.text.Normalizer;
import java.util.regex.Pattern;

@Service
public class UserService {

    private static final Pattern PROFILE_WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern PROFILE_PHONE = Pattern.compile("^[0-9+().\\-\\s]*$");
    private static final Pattern OWNED_AVATAR_PATH = Pattern.compile(
            "^/api/public/uploads/[A-Za-z0-9][A-Za-z0-9._-]{0,254}$");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Autowired
    private AuthSessionRevocationService authSessionRevocationService;

    @Autowired
    private com.hotel.repositories.RoleRepository roleRepository;

    @Autowired
    private com.hotel.repositories.HotelRepository hotelRepository;

    @Autowired
    private PropertyAccessService propertyAccessService;

    @Autowired(required = false)
    private OperationalAuditService operationalAuditService;

    public List<UserDto> getAllUsers() {
        List<User> users;
        java.util.Set<Long> visibleHotelIds = null;
        if (propertyAccessService.isSystemAdministrator()) {
            users = userRepository.findAll();
        } else {
            visibleHotelIds = propertyAccessService.accessibleHotelIds();
            if (visibleHotelIds.isEmpty()) {
                users = List.of();
            } else {
                java.util.Map<Long, User> manageable = new java.util.LinkedHashMap<>();
                userRepository.findAccessibleUsers(visibleHotelIds)
                        .forEach(user -> manageable.put(user.getId(), user));
                userPropertyRepository.findHistoricalStaffUsersByHotelIds(visibleHotelIds)
                        .forEach(user -> manageable.put(user.getId(), user));
                users = new java.util.ArrayList<>(manageable.values());
            }
        }
        java.util.Set<Long> assignmentScope = visibleHotelIds;
        return users.stream()
                .map(user -> convertToDto(user, assignmentScope))
                .collect(Collectors.toList());
    }

    public Optional<UserDto> getUserById(Long id) {
        boolean systemAdministrator = propertyAccessService.isSystemAdministrator();
        java.util.Set<Long> hotelIds = systemAdministrator ? null : propertyAccessService.accessibleHotelIds();
        if (!systemAdministrator && !isManageableUser(id, hotelIds)) {
            return Optional.empty();
        }
        return userRepository.findById(id).map(user -> convertToDto(user, hotelIds));
    }

    public Optional<User> getEntityById(Long id) {
        return userRepository.findById(id);
    }

    @Transactional
    public UserDto createUser(User user, java.util.Set<Long> roleIds, Long hotelId) {
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new RuntimeException("Username is already taken!");
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Email is already taken!");
        }

        boolean systemAdministrator = propertyAccessService.isSystemAdministrator();
        java.util.Set<com.hotel.entities.Role> roles = roleIds == null
                ? java.util.Set.of()
                : new java.util.HashSet<>(roleRepository.findAllById(roleIds));
        if (roleIds != null && roles.size() != roleIds.size()) {
            throw new IllegalArgumentException("Vai trò không hợp lệ.");
        }
        if (!systemAdministrator && roles.stream()
                .map(com.hotel.entities.Role::getCode)
                .anyMatch(java.util.Set.of("SUPER_ADMIN", "ADMIN", "PROPERTY_OWNER")::contains)) {
            throw new SecurityException("Bạn không được cấp vai trò quản trị hệ thống hoặc chủ cơ sở.");
        }

        com.hotel.entities.Hotel hotel = null;
        if (hotelId != null) {
            hotel = systemAdministrator
                    ? hotelRepository.findById(hotelId)
                            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cơ sở."))
                    : propertyAccessService.requireManagedHotel(hotelId);
        } else if (!systemAdministrator) {
            throw new IllegalArgumentException("Vui lòng chọn cơ sở cho nhân viên.");
        }

        checkStaffQuota(hotelId, systemAdministrator);

        user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash() != null ? user.getPasswordHash() : "123456"));
        user.setCreatedAt(java.time.LocalDateTime.now());
        user.setRoles(roles);
        user.setHotel(hotel);
        User saved = userRepository.save(user);

        if (hotel != null) {
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            com.hotel.entities.UserProperty mapping = new com.hotel.entities.UserProperty();
            mapping.setUser(saved);
            mapping.setHotel(hotel);
            mapping.setRelationshipType("STAFF");
            mapping.setIsPrimaryOwner(false);
            mapping.setStatus("ACTIVE");
            mapping.setStartDate(now);
            mapping.setStatusReason("Initial staff assignment");
            mapping.setStatusChangedAt(now);
            mapping.setStatusChangedBy(propertyAccessService.currentUser());
            userPropertyRepository.save(mapping);
        }

        auditStaff("STAFF_CREATED", saved, hotel, null, staffSnapshot(saved, hotel), "Staff account created");
        return convertToDto(saved);
    }

    @Transactional
    public UserDto updateUser(Long id, User userDetails, java.util.Set<Long> roleIds, Long hotelId) {
        boolean systemAdministrator = propertyAccessService.isSystemAdministrator();
        User user = requireManageableUser(id, systemAdministrator);
        java.util.Map<String, Object> before = staffSnapshot(user, user.getHotel());
        java.util.Set<com.hotel.entities.Role> roles = roleIds == null
                ? user.getRoles()
                : new java.util.HashSet<>(roleRepository.findAllById(roleIds));
        if (roleIds != null && roles.size() != roleIds.size()) {
            throw new IllegalArgumentException("Vai trò không hợp lệ.");
        }
        if (!systemAdministrator && roles != null && roles.stream()
                .map(com.hotel.entities.Role::getCode)
                .anyMatch(java.util.Set.of("SUPER_ADMIN", "ADMIN", "PROPERTY_OWNER")::contains)) {
            throw new SecurityException("Bạn không được cấp vai trò quản trị hệ thống hoặc chủ cơ sở.");
        }

        com.hotel.entities.Hotel hotel = null;
        if (hotelId != null) {
            hotel = systemAdministrator
                    ? hotelRepository.findById(hotelId)
                            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cơ sở."))
                    : propertyAccessService.requireManagedHotel(hotelId);
        } else if (!systemAdministrator) {
            throw new IllegalArgumentException("Vui lòng chọn cơ sở cho nhân viên.");
        }

        List<com.hotel.entities.UserProperty> staffAssignments =
                userPropertyRepository.findStaffAssignmentsForUpdate(id);
        if (!staffAssignments.isEmpty()
                && userDetails.getStatus() != null
                && !userDetails.getStatus().equalsIgnoreCase(user.getStatus())) {
            throw new IllegalStateException("Use the staff deactivate/reactivate action to change access status.");
        }
        if (!systemAdministrator && staffAssignments.stream().noneMatch(item ->
                item.getHotel() != null
                        && item.getHotel().getId().equals(hotelId)
                        && "ACTIVE".equals(item.getStatus()))) {
            throw new IllegalStateException("Deactivate and rehire the staff member to change property assignment.");
        }

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
        if (userDetails.getStatus() != null) {
            user.setStatus(userDetails.getStatus());
        }

        if (userDetails.getPasswordHash() != null && !userDetails.getPasswordHash().isEmpty()) {
            user.setPasswordHash(passwordEncoder.encode(userDetails.getPasswordHash()));
        }

        user.setRoles(roles);
        user.setHotel(hotel);
        User saved = userRepository.save(user);
        auditStaff("STAFF_UPDATED", saved, hotel, before, staffSnapshot(saved, hotel), "Staff account updated");
        return convertToDto(saved);
    }

    @Transactional
    public UserDto deactivateStaff(Long id, StaffLifecycleRequest request) {
        boolean systemAdministrator = propertyAccessService.isSystemAdministrator();
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        java.util.Map<String, Object> before = staffSnapshot(user, user.getHotel());
        User actor = propertyAccessService.currentUser();
        String reason = requireLifecycleReason(request);
        com.hotel.entities.Hotel hotel = requireLifecycleHotel(request, systemAdministrator);
        List<com.hotel.entities.UserProperty> assignments =
                userPropertyRepository.findStaffAssignmentsForUpdate(id);
        requireLifecycleAuthority(user, actor, assignments, hotel.getId(), systemAdministrator);

        com.hotel.entities.UserProperty assignment = assignments.stream()
                .filter(item -> item.getHotel() != null
                        && hotel.getId().equals(item.getHotel().getId())
                        && "ACTIVE".equals(item.getStatus()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("The staff assignment is not active."));

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        assignment.setStatus("INACTIVE");
        assignment.setEndDate(now);
        assignment.setStatusReason(reason);
        assignment.setStatusChangedAt(now);
        assignment.setStatusChangedBy(actor);
        userPropertyRepository.save(assignment);

        if (userPropertyRepository.countByUserIdAndStatus(id, "ACTIVE") == 0) {
            user.setStatus("INACTIVE");
            userRepository.save(user);
        }
        auditStaff("STAFF_DEACTIVATED", user, hotel, before, staffSnapshot(user, hotel), reason);
        return convertToDto(user, systemAdministrator ? null : propertyAccessService.accessibleHotelIds());
    }

    @Transactional
    public UserDto reactivateStaff(Long id, StaffLifecycleRequest request) {
        boolean systemAdministrator = propertyAccessService.isSystemAdministrator();
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        java.util.Map<String, Object> before = staffSnapshot(user, user.getHotel());
        User actor = propertyAccessService.currentUser();
        String reason = requireLifecycleReason(request);
        com.hotel.entities.Hotel hotel = requireLifecycleHotel(request, systemAdministrator);
        List<com.hotel.entities.UserProperty> assignments =
                userPropertyRepository.findStaffAssignmentsForUpdate(id);
        requireLifecycleAuthority(user, actor, assignments, hotel.getId(), systemAdministrator);

        boolean hasHistory = assignments.stream().anyMatch(item -> item.getHotel() != null
                && hotel.getId().equals(item.getHotel().getId()));
        if (!hasHistory) {
            throw new IllegalStateException("No historical staff assignment exists for this property.");
        }
        if (assignments.stream().anyMatch(item -> item.getHotel() != null
                && hotel.getId().equals(item.getHotel().getId())
                && "ACTIVE".equals(item.getStatus()))) {
            throw new IllegalStateException("The staff assignment is already active.");
        }
        if (user.getStatus() != null
                && !"ACTIVE".equalsIgnoreCase(user.getStatus())
                && !"INACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new IllegalStateException("A suspended or disabled account requires system account review.");
        }

        checkStaffQuota(hotel.getId(), systemAdministrator);

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        com.hotel.entities.UserProperty assignment = new com.hotel.entities.UserProperty();
        assignment.setUser(user);
        assignment.setHotel(hotel);
        assignment.setRelationshipType("STAFF");
        assignment.setIsPrimaryOwner(false);
        assignment.setStatus("ACTIVE");
        assignment.setStartDate(now);
        assignment.setStatusReason(reason);
        assignment.setStatusChangedAt(now);
        assignment.setStatusChangedBy(actor);
        userPropertyRepository.save(assignment);

        user.setStatus("ACTIVE");
        user.setHotel(hotel);
        User saved = userRepository.save(user);
        auditStaff("STAFF_REACTIVATED", saved, hotel, before, staffSnapshot(saved, hotel), reason);
        return convertToDto(saved, systemAdministrator ? null : propertyAccessService.accessibleHotelIds());
    }

    @Transactional
    public UserDto updateProfile(Long id, String fullName, String phone, String avatarUrl) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String normalizedFullName = normalizeRequiredProfileText(fullName, "Full name", 150);
        String normalizedPhone = normalizePhone(phone);
        String normalizedAvatarUrl = normalizeAvatarUrl(avatarUrl);

        user.setFullName(normalizedFullName);
        user.setPhone(normalizedPhone);
        user.setAvatarUrl(normalizedAvatarUrl);
        return convertToDto(userRepository.save(user));
    }

    private String normalizeRequiredProfileText(String value, String fieldName, int maxLength) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        String normalized = PROFILE_WHITESPACE.matcher(
                Normalizer.normalize(value, Normalizer.Form.NFKC).strip()).replaceAll(" ");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " is too long.");
        }
        return normalized;
    }

    private String normalizePhone(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = PROFILE_WHITESPACE.matcher(
                Normalizer.normalize(value, Normalizer.Form.NFKC).strip()).replaceAll(" ");
        if (normalized.length() > 30 || !PROFILE_PHONE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Phone contains unsupported characters.");
        }
        return normalized;
    }

    private String normalizeAvatarUrl(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).strip();
        if (OWNED_AVATAR_PATH.matcher(normalized).matches() && !normalized.contains("..")) {
            return normalized;
        }

        try {
            URI uri = URI.create(normalized);
            if ("https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && !uri.getHost().isBlank()
                    && uri.getRawUserInfo() == null) {
                return uri.toASCIIString();
            }
        } catch (IllegalArgumentException ignored) {
            // Return the same stable validation error for malformed and unsafe URLs.
        }
        throw new IllegalArgumentException(
                "Avatar URL must use HTTPS or an application-managed upload path.");
    }

    @Transactional
    public void changePassword(Long id, String currentPassword, String newPassword) {
        com.hotel.security.PasswordPolicy.requireValid(newPassword);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw com.hotel.security.PasswordChangeException.currentPasswordInvalid();
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        authSessionRevocationService.revokeUserSession(user.getId(), "PASSWORD_CHANGE");
    }

    private User requireManageableUser(Long id, boolean systemAdministrator) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (systemAdministrator) {
            return user;
        }
        if (propertyAccessService.currentUser().getId().equals(id)) {
            throw new SecurityException("Bạn không thể sửa hoặc xóa chính tài khoản đang đăng nhập.");
        }
        if (!isAccessibleUser(id)) {
            throw new SecurityException("Bạn không có quyền quản lý tài khoản này.");
        }
        if (user.getRoles() != null && user.getRoles().stream()
                .map(com.hotel.entities.Role::getCode)
                .anyMatch(java.util.Set.of("SUPER_ADMIN", "ADMIN", "PROPERTY_OWNER")::contains)) {
            throw new SecurityException("Bạn không có quyền quản lý tài khoản đặc quyền.");
        }
        return user;
    }

    private boolean isAccessibleUser(Long id) {
        java.util.Set<Long> hotelIds = propertyAccessService.accessibleHotelIds();
        return !hotelIds.isEmpty() && userRepository.isUserAccessible(id, hotelIds);
    }

    private boolean isManageableUser(Long id, java.util.Set<Long> hotelIds) {
        if (hotelIds == null || hotelIds.isEmpty()) {
            return false;
        }
        if (userRepository.isUserAccessible(id, hotelIds)) {
            return true;
        }
        return userPropertyRepository.findByUserIdAndRelationshipType(id, "STAFF").stream()
                .anyMatch(item -> item.getHotel() != null && hotelIds.contains(item.getHotel().getId()));
    }

    /** Locks the property's entitlement before reading usage so concurrent staff adds serialize. */
    private void checkStaffQuota(Long hotelId, boolean systemAdministrator) {
        if (systemAdministrator) {
            return;
        }
        propertyEntitlementService.getCurrentForUpdate(hotelId);
        long currentStaff = userPropertyRepository.countActiveStaffByHotelId(hotelId);
        subscriptionFeatureService.checkFeatureLimitForProperty(hotelId, "MAX_STAFF", currentStaff, 1);
    }

    private String requireLifecycleReason(StaffLifecycleRequest request) {
        if (request == null || request.getReason() == null || request.getReason().trim().length() < 3) {
            throw new IllegalArgumentException("A lifecycle reason of at least 3 characters is required.");
        }
        String reason = request.getReason().trim();
        if (reason.length() > 500) {
            throw new IllegalArgumentException("The lifecycle reason must not exceed 500 characters.");
        }
        return reason;
    }

    private com.hotel.entities.Hotel requireLifecycleHotel(
            StaffLifecycleRequest request,
            boolean systemAdministrator) {
        if (request == null || request.getHotelId() == null) {
            throw new IllegalArgumentException("Property is required for staff lifecycle changes.");
        }
        return systemAdministrator
                ? hotelRepository.findById(request.getHotelId())
                        .orElseThrow(() -> new IllegalArgumentException("Property not found."))
                : propertyAccessService.requireManagedHotel(request.getHotelId());
    }

    private void requireLifecycleAuthority(
            User target,
            User actor,
            List<com.hotel.entities.UserProperty> assignments,
            Long hotelId,
            boolean systemAdministrator) {
        if (actor != null && actor.getId() != null && actor.getId().equals(target.getId())) {
            throw new SecurityException("You cannot change your own staff lifecycle.");
        }
        if (!systemAdministrator && target.getRoles() != null && target.getRoles().stream()
                .map(com.hotel.entities.Role::getCode)
                .anyMatch(java.util.Set.of("SUPER_ADMIN", "ADMIN", "PROPERTY_OWNER")::contains)) {
            throw new SecurityException("You cannot manage a privileged account.");
        }
        if (!systemAdministrator && assignments.stream().noneMatch(item -> item.getHotel() != null
                && hotelId.equals(item.getHotel().getId()))) {
            throw new SecurityException("You cannot manage this staff assignment.");
        }
    }

    private UserDto convertToDto(User user) {
        return convertToDto(user, null);
    }

    private UserDto convertToDto(User user, java.util.Set<Long> visibleHotelIds) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setEmailVerifiedAt(user.getEmailVerifiedAt());
        dto.setPendingEmail(user.getPendingEmail());
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

        List<UserDto.StaffAssignmentSummary> assignments =
                userPropertyRepository.findByUserIdAndRelationshipTypeOrderByStartDateDesc(user.getId(), "STAFF")
                        .stream()
                        .filter(item -> visibleHotelIds == null
                                || (item.getHotel() != null && visibleHotelIds.contains(item.getHotel().getId())))
                        .map(item -> {
                            UserDto.StaffAssignmentSummary summary = new UserDto.StaffAssignmentSummary();
                            summary.setId(item.getId());
                            summary.setHotelId(item.getHotel().getId());
                            summary.setHotelName(item.getHotel().getName());
                            summary.setStatus(item.getStatus());
                            summary.setStatusReason(item.getStatusReason());
                            summary.setStartDate(item.getStartDate());
                            summary.setEndDate(item.getEndDate());
                            return summary;
                        })
                        .toList();
        dto.setStaffAssignments(assignments);

        return dto;
    }

    private java.util.Map<String, Object> staffSnapshot(User user, com.hotel.entities.Hotel hotel) {
        java.util.Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("id", user.getId());
        snapshot.put("username", user.getUsername());
        snapshot.put("status", user.getStatus());
        snapshot.put("roleCodes", user.getRoles() == null ? java.util.List.of() : user.getRoles().stream()
                .map(com.hotel.entities.Role::getCode).sorted().toList());
        snapshot.put("hotelId", hotel == null ? null : hotel.getId());
        return snapshot;
    }

    private void auditStaff(String eventType, User user, com.hotel.entities.Hotel hotel,
                            Object before, Object after, String reason) {
        if (operationalAuditService == null) return;
        String scope = hotel == null ? "SYSTEM" : "TENANT";
        operationalAuditService.append(new OperationalAuditService.AuditCommand(
                scope, hotel == null ? null : hotel.getId(), "STAFF", eventType, "USER",
                String.valueOf(user.getId()), null, null,
                reason == null || reason.isBlank() ? "Staff mutation completed" : reason,
                before, after, null));
    }

    @Autowired
    private com.hotel.repositories.AccountSubscriptionRepository accountSubscriptionRepository;

    @Autowired
    private com.hotel.repositories.UserPropertyRepository userPropertyRepository;

    @Autowired
    private SubscriptionFeatureService subscriptionFeatureService;

    @Autowired
    private PropertySubscriptionEntitlementService propertyEntitlementService;

    @Autowired
    private com.hotel.repositories.ChatMessageRepository chatMessageRepository;

    @Autowired
    private com.hotel.repositories.ReservationRepository reservationRepository;

    @Autowired
    private com.hotel.repositories.PropertyClaimRequestRepository propertyClaimRequestRepository;

    @Transactional(readOnly = true)
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
