package com.hotel.services;

import com.hotel.entities.Hotel;
import com.hotel.entities.Role;
import com.hotel.entities.User;
import com.hotel.entities.UserProperty;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.RoleRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

/** Owns the boundary where a pending applicant becomes an operational owner. */
@Service
@RequiredArgsConstructor
public class PropertyOwnershipLifecycleService {

    private final UserPropertyRepository userPropertyRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final HotelRepository hotelRepository;

    @Transactional
    public UserProperty createPendingOwner(User user, Hotel hotel) {
        var existing = userPropertyRepository
                .findByUserIdAndHotelIdAndRelationshipType(user.getId(), hotel.getId(), "OWNER");
        if (existing.filter(mapping -> "ACTIVE".equalsIgnoreCase(mapping.getStatus())).isPresent()) {
            throw new IllegalStateException("Tài khoản đã là chủ sở hữu đang hoạt động của cơ sở.");
        }
        UserProperty mapping = existing.orElseGet(UserProperty::new);
        mapping.setUser(user);
        mapping.setHotel(hotel);
        mapping.setRelationshipType("OWNER");
        mapping.setIsPrimaryOwner(false);
        mapping.setStatus("PENDING");
        mapping.setStartDate(null);
        mapping.setEndDate(null);
        return userPropertyRepository.save(mapping);
    }

    @Transactional
    public UserProperty activateOwner(Long hotelId, Long userId) {
        UserProperty mapping = userPropertyRepository
                .findByUserIdAndHotelIdAndRelationshipType(userId, hotelId, "OWNER")
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy hồ sơ sở hữu đang chờ duyệt."));
        if ("ACTIVE".equalsIgnoreCase(mapping.getStatus())) {
            grantOwnerRole(mapping.getUser());
            return mapping;
        }
        if (!"PENDING".equalsIgnoreCase(mapping.getStatus())) {
            throw new IllegalStateException("Hồ sơ sở hữu không còn ở trạng thái chờ duyệt.");
        }
        long activeOwners = userPropertyRepository.countByHotelIdAndRelationshipTypeAndStatus(
                hotelId, "OWNER", "ACTIVE");
        if (activeOwners > 0) {
            throw new IllegalStateException("Cơ sở đã có chủ sở hữu đang hoạt động.");
        }
        mapping.setStatus("ACTIVE");
        mapping.setIsPrimaryOwner(true);
        mapping.setStartDate(LocalDateTime.now());
        mapping.setEndDate(null);
        UserProperty saved = userPropertyRepository.save(mapping);
        grantOwnerRole(mapping.getUser());
        return saved;
    }

    @Transactional
    public int activatePendingOwnersForProperty(Long hotelId) {
        List<UserProperty> pending = userPropertyRepository
                .findByHotelIdAndRelationshipTypeAndStatus(hotelId, "OWNER", "PENDING");
        if (pending.isEmpty()) return 0;
        if (pending.size() > 1) {
            throw new IllegalStateException("Cơ sở có nhiều hồ sơ sở hữu đang chờ duyệt.");
        }
        activateOwner(hotelId, pending.getFirst().getUser().getId());
        return 1;
    }

    @Transactional
    public boolean deactivatePendingOwner(Long hotelId, Long userId) {
        return userPropertyRepository
                .findByUserIdAndHotelIdAndRelationshipType(userId, hotelId, "OWNER")
                .filter(mapping -> "PENDING".equalsIgnoreCase(mapping.getStatus()))
                .map(mapping -> {
                    mapping.setStatus("INACTIVE");
                    mapping.setIsPrimaryOwner(false);
                    mapping.setEndDate(LocalDateTime.now());
                    userPropertyRepository.save(mapping);
                    removeOwnerRoleIfUnused(mapping.getUser());
                    return true;
                }).orElse(false);
    }

    @Transactional
    public int deactivatePendingOwnersForProperty(Long hotelId) {
        int changed = 0;
        for (UserProperty mapping : userPropertyRepository
                .findByHotelIdAndRelationshipTypeAndStatus(hotelId, "OWNER", "PENDING")) {
            mapping.setStatus("INACTIVE");
            mapping.setIsPrimaryOwner(false);
            mapping.setEndDate(LocalDateTime.now());
            userPropertyRepository.save(mapping);
            removeOwnerRoleIfUnused(mapping.getUser());
            changed++;
        }
        return changed;
    }

    @Transactional
    public Hotel approveProperty(Long hotelId) {
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found"));
        if ("APPROVED".equalsIgnoreCase(hotel.getApprovalStatus())
                && "ACTIVE".equalsIgnoreCase(hotel.getOperationStatus())) {
            return hotel;
        }
        if (!"PENDING_APPROVAL".equalsIgnoreCase(hotel.getApprovalStatus())) {
            throw new IllegalStateException("Chỉ có thể duyệt cơ sở đang chờ phê duyệt.");
        }
        activatePendingOwnersForProperty(hotelId);
        hotel.setApprovalStatus("APPROVED");
        hotel.setOperationStatus("ACTIVE");
        hotel.setStatus("ACTIVE");
        return hotelRepository.save(hotel);
    }

    @Transactional
    public Hotel rejectProperty(Long hotelId) {
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found"));
        if (!"PENDING_APPROVAL".equalsIgnoreCase(hotel.getApprovalStatus())) {
            throw new IllegalStateException("Chỉ có thể từ chối cơ sở đang chờ phê duyệt.");
        }
        deactivatePendingOwnersForProperty(hotelId);
        hotel.setApprovalStatus("REJECTED");
        hotel.setOperationStatus("INACTIVE");
        hotel.setStatus("REJECTED");
        return hotelRepository.save(hotel);
    }

    private void grantOwnerRole(User user) {
        Role ownerRole = roleRepository.findByCode("PROPERTY_OWNER")
                .orElseThrow(() -> new IllegalStateException("Role PROPERTY_OWNER not found in DB"));
        HashSet<Role> roles = user.getRoles() == null ? new HashSet<>() : new HashSet<>(user.getRoles());
        if (roles.add(ownerRole)) {
            user.setRoles(roles);
            userRepository.save(user);
        }
    }

    private void removeOwnerRoleIfUnused(User user) {
        boolean hasActiveOwnership = userPropertyRepository.findByUserIdAndRelationshipType(user.getId(), "OWNER")
                .stream().anyMatch(mapping -> "ACTIVE".equalsIgnoreCase(mapping.getStatus()));
        if (hasActiveOwnership || user.getRoles() == null) return;
        HashSet<Role> roles = new HashSet<>(user.getRoles());
        if (roles.removeIf(role -> "PROPERTY_OWNER".equalsIgnoreCase(role.getCode()))) {
            user.setRoles(roles);
            userRepository.save(user);
        }
    }
}
