package com.hotel.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.Map;

@Service
public class ChatAuthorizationService {

    public CustomUserDetails requireUser(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails;
        }
        throw new AuthenticationCredentialsNotFoundException("Authenticated chat principal is required");
    }

    public void requirePermission(CustomUserDetails userDetails, int action) {
        if (!hasPermission(userDetails, action)) {
            throw new AccessDeniedException("Missing AI_CHAT permission");
        }
    }

    public boolean hasPermission(CustomUserDetails userDetails, int action) {
        if (isSystemAdministrator(userDetails)) {
            return true;
        }

        Map<FunctionCode, Integer> masks = userDetails.getPermissionMasks();
        Integer mask = masks == null ? null : masks.get(FunctionCode.AI_CHAT);
        return mask != null && (mask & action) == action;
    }

    public boolean isSystemAdministrator(CustomUserDetails userDetails) {
        return userDetails.getAuthorities().stream()
                .anyMatch(authority -> "SUPER_ADMIN".equals(authority.getAuthority())
                        || "ROLE_SUPER_ADMIN".equals(authority.getAuthority()));
    }
}
