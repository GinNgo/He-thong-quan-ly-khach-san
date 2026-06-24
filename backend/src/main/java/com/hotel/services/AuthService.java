package com.hotel.services;

import com.hotel.dtos.AuthResponse;
import com.hotel.dtos.LoginRequest;
import com.hotel.dtos.RegisterRequest;
import com.hotel.entities.Role;
import com.hotel.entities.User;
import com.hotel.repositories.RoleRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.security.JwtTokenProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final com.hotel.repositories.AppModuleRepository appModuleRepository;
    private final com.hotel.repositories.AppFunctionRepository appFunctionRepository;

    public AuthService(AuthenticationManager authenticationManager, UserRepository userRepository,
                       RoleRepository roleRepository, PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       com.hotel.repositories.AppModuleRepository appModuleRepository,
                       com.hotel.repositories.AppFunctionRepository appFunctionRepository) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.appModuleRepository = appModuleRepository;
        this.appFunctionRepository = appFunctionRepository;
    }

    public AuthResponse login(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        String token = jwtTokenProvider.generateToken(authentication);
        java.util.List<String> roles = authentication.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .collect(java.util.stream.Collectors.toList());

        java.util.List<com.hotel.dtos.PermissionDTO> permissions = new java.util.ArrayList<>();
        if (authentication.getPrincipal() instanceof com.hotel.security.CustomUserDetails) {
            com.hotel.security.CustomUserDetails userDetails = (com.hotel.security.CustomUserDetails) authentication.getPrincipal();
            userDetails.getPermissionMasks().forEach((func, mask) -> {
                permissions.add(new com.hotel.dtos.PermissionDTO(func.name(), mask));
            });
        }

        return new AuthResponse(token, authentication.getName(), roles, permissions);
    }

    public String register(RegisterRequest registerRequest) {
        if (userRepository.existsByUsername(registerRequest.getUsername())) {
            throw new RuntimeException("Username is already taken!");
        }

        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new RuntimeException("Email is already taken!");
        }

        User user = new User();
        user.setUsername(registerRequest.getUsername());
        user.setEmail(registerRequest.getEmail());
        user.setPasswordHash(passwordEncoder.encode(registerRequest.getPassword()));
        user.setFullName(registerRequest.getFullName());
        user.setPhone(registerRequest.getPhone());
        user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.now());

        Role customerRole = roleRepository.findByCode("CUSTOMER")
                .orElseThrow(() -> new RuntimeException("Error: Role CUSTOMER is not found."));
        user.setRoles(Collections.singleton(customerRole));

        userRepository.save(user);

        return "User registered successfully!";
    }

    @Transactional(readOnly = true)
    public java.util.List<com.hotel.dtos.AppModuleDto> getMyMenu() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Collections.emptyList();
        }

        boolean isAdminRole = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("SUPER_ADMIN") || a.getAuthority().equals("ADMIN"));
        boolean isAdminUser = authentication.getName().equals("admin");
        boolean isBypass = isAdminRole || isAdminUser;

        java.util.Map<String, Integer> userMasks = new java.util.HashMap<>();
        if (!isBypass && authentication.getPrincipal() instanceof com.hotel.security.CustomUserDetails) {
            com.hotel.security.CustomUserDetails userDetails = (com.hotel.security.CustomUserDetails) authentication.getPrincipal();
            userDetails.getPermissionMasks().forEach((k, v) -> userMasks.put(k.name(), v));
        }

        java.util.List<com.hotel.entities.AppModule> allModules = appModuleRepository.findAll();
        java.util.List<com.hotel.entities.AppFunction> allFunctions = appFunctionRepository.findAll();

        // Sort modules and functions safely
        allModules.sort(java.util.Comparator.comparing(m -> m.getId()));
        allFunctions.sort(java.util.Comparator.comparing(f -> f.getSortOrder() != null ? f.getSortOrder() : 999));

        java.util.List<com.hotel.dtos.AppModuleDto> result = new java.util.ArrayList<>();
        for (com.hotel.entities.AppModule module : allModules) {
            com.hotel.dtos.AppModuleDto moduleDto = new com.hotel.dtos.AppModuleDto();
            moduleDto.setId(module.getId());
            moduleDto.setCode(module.getCode());
            moduleDto.setName(module.getName());
            
            java.util.List<com.hotel.dtos.AppFunctionDto> funcDtos = new java.util.ArrayList<>();
            for (com.hotel.entities.AppFunction func : allFunctions) {
                if (func.getModule().getId().equals(module.getId())) {
                    if (isBypass || (userMasks.containsKey(func.getCode()) && (userMasks.get(func.getCode()) & 1) == 1)) {
                        com.hotel.dtos.AppFunctionDto dto = new com.hotel.dtos.AppFunctionDto();
                        dto.setId(func.getId());
                        dto.setModuleId(module.getId());
                        dto.setCode(func.getCode());
                        dto.setName(func.getName());
                        dto.setUrl(func.getUrl());
                        dto.setIcon(func.getIcon());
                        dto.setSortOrder(func.getSortOrder());
                        funcDtos.add(dto);
                    }
                }
            }
            if (!funcDtos.isEmpty()) {
                moduleDto.setFunctions(funcDtos);
                result.add(moduleDto);
            }
        }

        return result;
    }
}
