package com.hotel.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;
import java.util.Map;

public class CustomUserDetails extends User {
    private final Map<FunctionCode, Integer> permissionMasks;

    public CustomUserDetails(String username, String password, Collection<? extends GrantedAuthority> authorities, Map<FunctionCode, Integer> permissionMasks) {
        super(username, password, authorities);
        this.permissionMasks = permissionMasks;
    }

    public Map<FunctionCode, Integer> getPermissionMasks() {
        return permissionMasks;
    }
}
