package com.hotel.controllers;

import com.hotel.entities.User;
import com.hotel.dtos.ProfileUpdateRequest;
import com.hotel.dtos.PropertyOptionDto;
import com.hotel.dtos.StaffCreateRequest;
import com.hotel.dtos.StaffListItemDto;
import com.hotel.dtos.StaffRoleOptionDto;
import com.hotel.dtos.StaffUpdateRequest;
import com.hotel.dtos.UserDto;
import com.hotel.services.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import com.hotel.security.Permission;
import com.hotel.security.FunctionCode;
import com.hotel.security.ActionCode;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*", maxAge = 3600)
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping
    @Permission(function = FunctionCode.USER, action = ActionCode.VIEW)
    public ResponseEntity<List<UserDto>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/staff")
    @Permission(function = FunctionCode.USER, action = ActionCode.VIEW)
    public ResponseEntity<List<StaffListItemDto>> getStaff() {
        return ResponseEntity.ok(userService.getStaff());
    }

    @GetMapping("/staff/properties")
    @Permission(function = FunctionCode.USER, action = ActionCode.VIEW)
    public ResponseEntity<List<PropertyOptionDto>> getStaffPropertyOptions() {
        return ResponseEntity.ok(userService.getStaffPropertyOptions());
    }

    @GetMapping("/staff/roles")
    @Permission(function = FunctionCode.USER, action = ActionCode.VIEW)
    public ResponseEntity<List<StaffRoleOptionDto>> getAssignableStaffRoles() {
        return ResponseEntity.ok(userService.getAssignableStaffRoles());
    }

    @PostMapping("/staff")
    @Permission(function = FunctionCode.USER, action = ActionCode.CREATE)
    public ResponseEntity<UserDto> createStaff(@Valid @RequestBody StaffCreateRequest request) {
        return ResponseEntity.ok(userService.createStaff(request));
    }

    @PutMapping("/staff/{id}")
    @Permission(function = FunctionCode.USER, action = ActionCode.UPDATE)
    public ResponseEntity<UserDto> updateStaff(
            @PathVariable Long id,
            @Valid @RequestBody StaffUpdateRequest request) {
        return ResponseEntity.ok(userService.updateStaff(id, request));
    }

    @GetMapping("/{id}")
    @Permission(function = FunctionCode.USER, action = ActionCode.VIEW)
    public ResponseEntity<UserDto> getUserById(@PathVariable Long id) {
        Optional<UserDto> user = userService.getUserById(id);
        return user.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    @Permission(function = FunctionCode.USER, action = ActionCode.CREATE)
    public ResponseEntity<UserDto> createUser(@RequestBody com.hotel.dtos.UserRequest request) {
        if (request.getHotelId() != null) {
            throw new IllegalArgumentException("Use the dedicated staff endpoint for property assignments.");
        }
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(request.getPassword());
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        user.setStatus(request.getStatus() != null ? request.getStatus() : "ACTIVE");
        
        return ResponseEntity.ok(userService.createUser(user, request.getRoleIds(), null));
    }

    @PutMapping("/{id}")
    @Permission(function = FunctionCode.USER, action = ActionCode.UPDATE)
    public ResponseEntity<UserDto> updateUser(@PathVariable Long id, @RequestBody com.hotel.dtos.UserRequest request) {
        User userDetails = new User();
        userDetails.setFullName(request.getFullName());
        userDetails.setPhone(request.getPhone());
        userDetails.setStatus(request.getStatus());
        userDetails.setPasswordHash(request.getPassword());
        
        return ResponseEntity.ok(userService.updateUser(id, userDetails, request.getRoleIds(), request.getHotelId()));
    }

    @PostMapping("/{id}/deactivate")
    @Permission(function = FunctionCode.USER, action = ActionCode.DELETE)
    public ResponseEntity<UserDto> deactivateStaff(
            @PathVariable Long id,
            @RequestBody com.hotel.dtos.StaffLifecycleRequest request) {
        return ResponseEntity.ok(userService.deactivateStaff(id, request));
    }

    @PostMapping("/{id}/reactivate")
    @Permission(function = FunctionCode.USER, action = ActionCode.UPDATE)
    public ResponseEntity<UserDto> reactivateStaff(
            @PathVariable Long id,
            @RequestBody com.hotel.dtos.StaffLifecycleRequest request) {
        return ResponseEntity.ok(userService.reactivateStaff(id, request));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser() {
        com.hotel.security.CustomUserDetails userDetails = (com.hotel.security.CustomUserDetails) org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Optional<UserDto> user = userService.getUserWithSaaSContext(userDetails.getUserId());
        return user.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/me")
    public ResponseEntity<UserDto> updateCurrentUser(@Valid @RequestBody ProfileUpdateRequest request) {
        com.hotel.security.CustomUserDetails userDetails = (com.hotel.security.CustomUserDetails) org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(userService.updateProfile(
                userDetails.getUserId(),
                request.getFullName(),
                request.getPhone(),
                request.getAvatarUrl()
        ));
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody com.hotel.dtos.ChangePasswordRequest request) {
        com.hotel.security.CustomUserDetails userDetails = (com.hotel.security.CustomUserDetails) org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        userService.changePassword(userDetails.getUserId(), request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.noContent().build();
    }
}
