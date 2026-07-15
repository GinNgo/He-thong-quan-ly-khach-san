package com.hotel.services;

import com.hotel.entities.Hotel;
import com.hotel.entities.User;
import com.hotel.entities.UserProperty;
import com.hotel.repositories.HotelRepository;
import com.hotel.repositories.UserPropertyRepository;
import com.hotel.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PropertyAccessService {

    private final UserRepository userRepository;
    private final UserPropertyRepository userPropertyRepository;
    private final HotelRepository hotelRepository;

    public Hotel requireManagedHotel(Long requestedHotelId) {
        if (requestedHotelId == null) {
            throw new IllegalArgumentException("Vui lòng chọn cơ sở đang quản lý.");
        }
        Hotel hotel = hotelRepository.findById(requestedHotelId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cơ sở."));
        if (!isSystemAdministrator() && !accessibleHotelIds().contains(requestedHotelId)) {
            throw new SecurityException("Bạn không có quyền quản lý cơ sở này.");
        }
        return hotel;
    }

    public void requireCanManage(Long hotelId) {
        requireManagedHotel(hotelId);
    }

    public Set<Long> accessibleHotelIds() {
        User user = currentUser();
        Set<Long> hotelIds = new LinkedHashSet<>();
        List<UserProperty> assignments = userPropertyRepository.findByUserId(user.getId());
        assignments.stream()
                .filter(item -> "ACTIVE".equals(item.getStatus()))
                .map(UserProperty::getHotel)
                .filter(java.util.Objects::nonNull)
                .map(Hotel::getId)
                .forEach(hotelIds::add);
        if (user.getHotel() != null) hotelIds.add(user.getHotel().getId());
        return hotelIds;
    }

    public boolean isSystemAdministrator() {
        Authentication authentication = authentication();
        return authentication.getAuthorities().stream()
                .map(item -> item.getAuthority().replace("ROLE_", ""))
                .anyMatch(authority -> Set.of("SUPER_ADMIN", "ADMIN").contains(authority));
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
}
