package com.hotel.controllers;

import com.hotel.dtos.PropertyLifecycleDecisionResponse;
import com.hotel.dtos.PropertyLifecycleReasonRequest;
import com.hotel.dtos.PropertyLifecycleSummary;
import com.hotel.dtos.PropertyReviewHistoryItem;
import com.hotel.security.ActionCode;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.FunctionCode;
import com.hotel.security.Permission;
import com.hotel.services.PropertyLifecycleWorkflowService;
import com.hotel.services.PropertyReviewHistoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/properties")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('SUPER_ADMIN','ADMIN')")
public class AdminPropertyLifecycleController {

    private final PropertyLifecycleWorkflowService workflowService;
    private final PropertyReviewHistoryService propertyReviewHistoryService;

    @GetMapping("/lifecycle")
    @Permission(function = FunctionCode.PROPERTY_LIFECYCLE, action = ActionCode.VIEW)
    public List<PropertyLifecycleSummary> properties() {
        return workflowService.properties();
    }

    @GetMapping("/{id}/history")
    @Permission(function = FunctionCode.PROPERTY_LIFECYCLE, action = ActionCode.VIEW)
    public List<PropertyReviewHistoryItem> history(@PathVariable Long id) {
        return propertyReviewHistoryService.adminHistory(id);
    }

    @PostMapping("/{id}/suspend")
    @Permission(function = FunctionCode.PROPERTY_LIFECYCLE, action = ActionCode.APPROVE)
    public PropertyLifecycleDecisionResponse suspend(
            @PathVariable Long id,
            @Valid @RequestBody PropertyLifecycleReasonRequest request,
            Authentication authentication) {
        return workflowService.suspend(
                requireAuthoritativePrincipal(authentication).getUserId(), id, request.reason());
    }

    @PostMapping("/{id}/reactivate")
    @Permission(function = FunctionCode.PROPERTY_LIFECYCLE, action = ActionCode.APPROVE)
    public PropertyLifecycleDecisionResponse reactivate(
            @PathVariable Long id,
            @Valid @RequestBody PropertyLifecycleReasonRequest request,
            Authentication authentication) {
        return workflowService.reactivate(
                requireAuthoritativePrincipal(authentication).getUserId(), id, request.reason());
    }

    @PostMapping("/{id}/close")
    @Permission(function = FunctionCode.PROPERTY_LIFECYCLE, action = ActionCode.APPROVE)
    public PropertyLifecycleDecisionResponse close(
            @PathVariable Long id,
            @Valid @RequestBody PropertyLifecycleReasonRequest request,
            Authentication authentication) {
        return workflowService.close(
                requireAuthoritativePrincipal(authentication).getUserId(), id, request.reason());
    }

    private CustomUserDetails requireAuthoritativePrincipal(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required.");
        }
        if (!(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new AccessDeniedException("Authoritative authenticated account context is required.");
        }
        return userDetails;
    }
}
