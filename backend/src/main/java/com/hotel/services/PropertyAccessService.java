package com.hotel.services;

import com.hotel.entities.Hotel;
import com.hotel.entities.User;
import com.hotel.entities.UserProperty;
import com.hotel.exceptions.PropertyNotOperationalException;
import com.hotel.exceptions.ResourceNotFoundException;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PropertyAccessService {

    private final UserRepository userRepository;
    private final UserPropertyRepository userPropertyRepository;
    private final HotelRepository hotelRepository;

    public Hotel requireManagedHotel(Long requestedHotelId) {
        Hotel hotel = requireAssignedHotel(requestedHotelId);
        requireOperational(hotel);
        return hotel;
    }

    public Hotel requireTenantManagedHotel(Long requestedHotelId) {
        Hotel hotel = requireTenantAssignedHotel(requestedHotelId);
        requireOperational(hotel);
        return hotel;
    }

    public Hotel requireAssignedHotel(Long requestedHotelId) {
        if (requestedHotelId == null) {
            throw new IllegalArgumentException("Vui lòng chọn cơ sở đang quản lý.");
        }
        Hotel hotel = hotelRepository.findById(requestedHotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cơ sở."));
        if (!isSystemAdministrator() && !assignedHotelIds().contains(requestedHotelId)) {
            throw new ResourceNotFoundException("Không tìm thấy cơ sở.");
        }
        return hotel;
    }

    public Hotel requireTenantAssignedHotel(Long requestedHotelId) {
        if (requestedHotelId == null) {
            throw new IllegalArgumentException("Vui lòng chọn cơ sở đang quản lý.");
        }
        Hotel hotel = hotelRepository.findById(requestedHotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cơ sở."));
        if (!assignedHotelIds().contains(requestedHotelId)) {
            throw new ResourceNotFoundException("Không tìm thấy cơ sở.");
        }
        return hotel;
    }

    public void requireTenantCanManage(Long hotelId) {
        requireTenantManagedHotel(hotelId);
    }

    public void requireTenantAccessibleOrNotFound(Long hotelId, String entityName) {
        if (hotelId == null) {
            throw new ResourceNotFoundException("Không tìm thấy " + entityName + ".");
        }
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy " + entityName + "."));
        if (!assignedHotelIds().contains(hotelId)) {
            throw new ResourceNotFoundException("Không tìm thấy " + entityName + ".");
        }
        requireOperational(hotel);
    }

    public void requireCanManage(Long hotelId) {
        requireManagedHotel(hotelId);
    }

    /**
     * Same as requireCanManage but throws ResourceNotFoundException (→ 404)
     * to prevent IDOR enumeration across tenants.
     */
    public void requireAccessibleOrNotFound(Long hotelId, String entityName) {
        if (hotelId == null) {
            throw new ResourceNotFoundException("Không tìm thấy " + entityName + ".");
        }
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy " + entityName + "."));
        if (!isSystemAdministrator() && !assignedHotelIds().contains(hotelId)) {
            throw new ResourceNotFoundException("Không tìm thấy " + entityName + ".");
        }
        requireOperational(hotel);
    }

    public Set<Long> assignedHotelIds() {
        User user = currentUser();
        Set<Long> hotelIds = new LinkedHashSet<>();
        List<UserProperty> assignments = userPropertyRepository.findByUserId(user.getId());
        assignments.stream()
                .filter(item -> "ACTIVE".equals(normalizeStatus(item.getStatus())))
                .map(UserProperty::getHotel)
                .filter(java.util.Objects::nonNull)
                .map(Hotel::getId)
                .filter(java.util.Objects::nonNull)
                .forEach(hotelIds::add);
        if (user.getHotel() != null && user.getHotel().getId() != null) hotelIds.add(user.getHotel().getId());
        return hotelIds;
    }

    public Set<Long> accessibleHotelIds() {
        Set<Long> assignedIds = assignedHotelIds();
        if (assignedIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> operationalIds = new LinkedHashSet<>();
        hotelRepository.findAllById(assignedIds).stream()
                .filter(this::isOperational)
                .map(Hotel::getId)
                .forEach(operationalIds::add);
        return operationalIds;
    }

    public boolean isOperational(Hotel hotel) {
        return hotel != null
                && "APPROVED".equals(normalizeStatus(hotel.getApprovalStatus()))
                && "ACTIVE".equals(normalizeStatus(hotel.getOperationStatus()));
    }

    public boolean isSystemAdministrator() {
        Authentication authentication = authentication();
        return authentication.getAuthorities().stream()
                .map(item -> item.getAuthority().replace("ROLE_", ""))
                .anyMatch(authority -> Set.of("SUPER_ADMIN").contains(authority));
    }

    public User currentUser() {
        Authentication authentication = authentication();
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new SecurityException("Không tìm thấy tài khoản đăng nhập."));
    }

    private Authentication authentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            throw new SecurityException("Bạn cần đăng nhập để quản lý cơ sở.");
        }
        return authentication;
    }

    private void requireOperational(Hotel hotel) {
        if (!isOperational(hotel)) {
            throw new PropertyNotOperationalException(
                    hotel == null ? null : hotel.getApprovalStatus(),
                    hotel == null ? null : hotel.getOperationStatus());
        }
    }

    private String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
    }
}
