package com.hotel.services;

import com.hotel.entities.Hotel;
import com.hotel.entities.Role;
import com.hotel.entities.User;
import com.hotel.entities.UserProperty;
import com.hotel.repositories.RoleRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;

/** Owns the boundary where a pending applicant becomes an operational owner. */
@Service
@RequiredArgsConstructor
public class PropertyOwnershipLifecycleService {

    private final UserPropertyRepository userPropertyRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Transactional
    public UserProperty createPendingOwner(User user, Hotel hotel) {
        var existing = userPropertyRepository
                .findByUserIdAndHotelIdAndRelationshipType(user.getId(), hotel.getId(), "OWNER");
        if (existing.filter(mapping -> "ACTIVE".equalsIgnoreCase(mapping.getStatus())).isPresent()) {
            throw new IllegalStateException("The account already owns this property.");
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
                .orElseThrow(() -> new IllegalStateException("Pending property ownership was not found."));
        if ("ACTIVE".equalsIgnoreCase(mapping.getStatus())) {
            grantOwnerRole(mapping.getUser());
            return mapping;
        }
        if (!"PENDING".equalsIgnoreCase(mapping.getStatus())) {
            throw new IllegalStateException("Property ownership is no longer pending.");
        }
        long activeOwners = userPropertyRepository.countByHotelIdAndRelationshipTypeAndStatus(
                hotelId, "OWNER", "ACTIVE");
        if (activeOwners > 0) {
            throw new IllegalStateException("Property already has an active owner.");
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
    public boolean deactivatePendingOwner(Long hotelId, Long userId) {
        return userPropertyRepository
                .findPendingOwnerMappingForUpdate(userId, hotelId)
                .map(mapping -> {
                    mapping.setStatus("INACTIVE");
                    mapping.setIsPrimaryOwner(false);
                    mapping.setEndDate(LocalDateTime.now());
                    userPropertyRepository.save(mapping);
                    removeOwnerRoleIfUnused(mapping.getUser());
                    return true;
                }).orElse(false);
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
