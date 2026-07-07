package com.hotel.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;
import java.util.Map;

public class CustomUserDetails extends User {
    private final Map<FunctionCode, Integer> permissionMasks;
    private final Long userId;
    private final Long hotelId;

    public CustomUserDetails(String username, String password, Collection<? extends GrantedAuthority> authorities, Map<FunctionCode, Integer> permissionMasks, Long userId, Long hotelId) {
        super(username, password, authorities);
        this.permissionMasks = permissionMasks;
        this.userId = userId;
        this.hotelId = hotelId;
    }

    public Map<FunctionCode, Integer> getPermissionMasks() {
        return permissionMasks;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getHotelId() {
        return hotelId;
    }
}
