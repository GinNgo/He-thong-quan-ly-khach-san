package com.hotel.security;

import com.hotel.entities.User;
import com.hotel.repositories.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.EnumMap;
import java.util.stream.Collectors;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final com.hotel.services.SubscriptionFeatureService subscriptionFeatureService;

    public CustomUserDetailsService(UserRepository userRepository, com.hotel.services.SubscriptionFeatureService subscriptionFeatureService) {
        this.userRepository = userRepository;
        this.subscriptionFeatureService = subscriptionFeatureService;
    }

    @Override
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        String normalizedIdentifier = normalizeIdentifier(usernameOrEmail);
        User user = userRepository.findByUsername(normalizedIdentifier)
                .or(() -> userRepository.findByEmail(normalizedIdentifier))
                .orElseThrow(() -> new UsernameNotFoundException("Invalid login credentials."));
        AccountStatusPolicy.requireActive(user);

        java.util.Set<GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getCode()))
                .collect(Collectors.toSet());

        java.util.Map<FunctionCode, Integer> permissionMasks = new EnumMap<>(FunctionCode.class);

        user.getRoles().forEach(role -> {
            if (role.getRolePermissions() != null) {
                role.getRolePermissions().forEach(rp -> {
                    try {
                        if (rp.getFunction() == null || rp.getFunction().getCode() == null) return;
                        FunctionCode functionCode = FunctionCode.valueOf(rp.getFunction().getCode());
                        int existingMask = permissionMasks.getOrDefault(functionCode, 0);
                        int actionMask = rp.getActionMask() == null ? 0 : rp.getActionMask();
                        permissionMasks.put(functionCode, existingMask | actionMask);
                    } catch (IllegalArgumentException e) {
                        // Ignore unknown functions
                    }
                });
            }
        });

        Long hotelId = user.getHotel() != null ? user.getHotel().getId() : null;
        java.util.Map<String, Integer> featureLimits = subscriptionFeatureService.getActiveFeaturesForUser(user.getId());

        return new CustomUserDetails(
                user.getUsername(),
                user.getPasswordHash(),
                authorities,
                permissionMasks,
                user.getId(),
                hotelId,
                featureLimits,
                user.getAuthRevokedAt()
        );
    }

    private String normalizeIdentifier(String value) {
        if (value == null || value.isBlank()) {
            throw new UsernameNotFoundException("Invalid login credentials.");
        }
        return value.strip().toLowerCase(Locale.ROOT);
    }
}
