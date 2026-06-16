package com.hotel.security;

import com.hotel.entities.User;
import com.hotel.repositories.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));

        java.util.Set<GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getCode()))
                .collect(Collectors.toSet());

        java.util.Map<FunctionCode, Integer> permissionMasks = new java.util.HashMap<>();

        user.getRoles().forEach(role -> {
            if (role.getRolePermissions() != null) {
                role.getRolePermissions().forEach(rp -> {
                    try {
                        FunctionCode functionCode = FunctionCode.valueOf(rp.getFunction().getCode());
                        int existingMask = permissionMasks.getOrDefault(functionCode, 0);
                        permissionMasks.put(functionCode, existingMask | rp.getActionMask());
                    } catch (IllegalArgumentException e) {
                        // Ignore unknown functions
                    }
                });
            }
        });

        return new CustomUserDetails(
                user.getUsername(),
                user.getPasswordHash(),
                authorities,
                permissionMasks
        );
    }
}
