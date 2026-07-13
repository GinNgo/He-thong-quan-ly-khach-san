package com.hotel.controllers;

import com.hotel.entities.User;
import com.hotel.dtos.UserDto;
import com.hotel.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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

    @GetMapping("/{id}")
    @Permission(function = FunctionCode.USER, action = ActionCode.VIEW)
    public ResponseEntity<UserDto> getUserById(@PathVariable Long id) {
        Optional<UserDto> user = userService.getUserById(id);
        return user.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    @Permission(function = FunctionCode.USER, action = ActionCode.CREATE)
    public ResponseEntity<UserDto> createUser(@RequestBody com.hotel.dtos.UserRequest request) {
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(request.getPassword());
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        user.setStatus(request.getStatus() != null ? request.getStatus() : "ACTIVE");
        
        return ResponseEntity.ok(userService.createUser(user, request.getRoleIds(), request.getHotelId()));
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

    @DeleteMapping("/{id}")
    @Permission(function = FunctionCode.USER, action = ActionCode.DELETE)
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser() {
        com.hotel.security.CustomUserDetails userDetails = (com.hotel.security.CustomUserDetails) org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Optional<UserDto> user = userService.getUserWithSaaSContext(userDetails.getUserId());
        return user.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/me")
    public ResponseEntity<UserDto> updateCurrentUser(@RequestBody com.hotel.dtos.UserRequest request) {
        com.hotel.security.CustomUserDetails userDetails = (com.hotel.security.CustomUserDetails) org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(userService.updateProfile(
                userDetails.getUserId(),
                request.getFullName(),
                request.getEmail(),
                request.getPhone(),
                request.getAvatarUrl()
        ));
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(@RequestBody com.hotel.dtos.ChangePasswordRequest request) {
        com.hotel.security.CustomUserDetails userDetails = (com.hotel.security.CustomUserDetails) org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        userService.changePassword(userDetails.getUserId(), request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.ok().build();
    }
}
