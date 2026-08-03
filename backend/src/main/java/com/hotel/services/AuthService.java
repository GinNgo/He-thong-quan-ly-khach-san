package com.hotel.services;

import com.hotel.dtos.AuthResponse;
import com.hotel.dtos.LoginRequest;
import com.hotel.dtos.RegisterRequest;
import com.hotel.dtos.SocialIdentityResponse;
import com.hotel.entities.SocialProvider;
import com.hotel.entities.Role;
import com.hotel.entities.User;
import com.hotel.exceptions.SocialAccountLinkException;
import com.hotel.repositories.RoleRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.security.AccountStatusPolicy;
import com.hotel.security.RefreshTokenException;
import com.hotel.security.JwtTokenProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hotel.services.social.ExternalIdentityProfile;
import com.hotel.services.social.FacebookIdentityVerifier;
import com.hotel.services.social.GoogleIdentityVerifier;

import java.time.LocalDateTime;
import java.util.Collections;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final com.hotel.repositories.AppModuleRepository appModuleRepository;
    private final com.hotel.repositories.AppFunctionRepository appFunctionRepository;
    private final GoogleIdentityVerifier googleIdentityVerifier;
    private final FacebookIdentityVerifier facebookIdentityVerifier;
    private final SocialAccountLinkService socialAccountLinkService;

    @Value("${google.client.id:YOUR_GOOGLE_CLIENT_ID_HERE}")
    private String googleClientId;

    public AuthService(AuthenticationManager authenticationManager, UserRepository userRepository,
                       RoleRepository roleRepository, PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       com.hotel.repositories.AppModuleRepository appModuleRepository,
                       com.hotel.repositories.AppFunctionRepository appFunctionRepository,
                       GoogleIdentityVerifier googleIdentityVerifier,
                       FacebookIdentityVerifier facebookIdentityVerifier,
                       SocialAccountLinkService socialAccountLinkService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.appModuleRepository = appModuleRepository;
        this.appFunctionRepository = appFunctionRepository;
        this.googleIdentityVerifier = googleIdentityVerifier;
        this.facebookIdentityVerifier = facebookIdentityVerifier;
        this.socialAccountLinkService = socialAccountLinkService;
    }

    public AuthResponse login(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()
                )
        );
        User authenticatedUser = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password."));
        AccountStatusPolicy.requireActive(authenticatedUser);


        SecurityContextHolder.getContext().setAuthentication(authentication);

        String token = jwtTokenProvider.generateToken(authentication);
        java.util.List<String> roles = authentication.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .collect(java.util.stream.Collectors.toList());

        java.util.List<com.hotel.dtos.PermissionDTO> permissions = new java.util.ArrayList<>();
        Long userId = null;
        if (authentication.getPrincipal() instanceof com.hotel.security.CustomUserDetails) {
            com.hotel.security.CustomUserDetails userDetails = (com.hotel.security.CustomUserDetails) authentication.getPrincipal();
            userId = userDetails.getUserId();
            userDetails.getPermissionMasks().forEach((func, mask) -> {
                permissions.add(new com.hotel.dtos.PermissionDTO(func.name(), mask));
            });
        }

        return new AuthResponse(token, authentication.getName(), userId, roles, permissions);
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

    public AuthResponse loginWithGoogle(String idTokenString) {
        return createSocialAuthResponse(socialAccountLinkService.resolveOrLink(
                googleIdentityVerifier.verify(idTokenString)));
    }

    public AuthResponse loginWithFacebook(String accessToken) {
        return createSocialAuthResponse(socialAccountLinkService.resolveOrLink(
                facebookIdentityVerifier.verify(accessToken)));
    }

    @Transactional(readOnly = true)
    public java.util.List<SocialIdentityResponse> listSocialIdentities(Long userId) {
        return socialAccountLinkService.list(userId);
    }

    public SocialIdentityResponse linkSocialIdentity(Long userId, String providerName, String credential) {
        SocialProvider provider = parseProvider(providerName);
        ExternalIdentityProfile profile = provider == SocialProvider.GOOGLE
                ? googleIdentityVerifier.verify(credential)
                : facebookIdentityVerifier.verify(credential);
        return socialAccountLinkService.link(userId, profile);
    }

    public boolean unlinkSocialIdentity(Long userId, String providerName, String currentPassword) {
        return socialAccountLinkService.unlink(userId, parseProvider(providerName), currentPassword);
    }

    private SocialProvider parseProvider(String providerName) {
        try {
            return SocialProvider.fromPath(providerName);
        } catch (IllegalArgumentException exception) {
            throw SocialAccountLinkException.unsupportedProvider();
        }
    }

    AuthResponse createSocialAuthResponse(User user) {
        AccountStatusPolicy.requireActive(user);
        java.util.List<org.springframework.security.core.authority.SimpleGrantedAuthority> authorities =
                user.getRoles().stream()
                        .map(role -> new org.springframework.security.core.authority.SimpleGrantedAuthority(role.getCode()))
                        .collect(java.util.stream.Collectors.toList());
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                user.getUsername(), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String token = jwtTokenProvider.generateToken(authentication);
        java.util.List<String> roles = user.getRoles().stream()
                .map(Role::getCode)
                .sorted()
                .collect(java.util.stream.Collectors.toList());
        java.util.Map<String, Integer> masks = new java.util.TreeMap<>();
        user.getRoles().forEach(role -> {
            if (role.getRolePermissions() == null) return;
            role.getRolePermissions().forEach(permission -> {
                if (permission.getFunction() == null) return;
                masks.merge(permission.getFunction().getCode(),
                        permission.getActionMask() == null ? 0 : permission.getActionMask(),
                        (left, right) -> left | right);
            });
        });
        java.util.List<com.hotel.dtos.PermissionDTO> permissions = masks.entrySet().stream()
                .map(entry -> new com.hotel.dtos.PermissionDTO(entry.getKey(), entry.getValue()))
                .collect(java.util.stream.Collectors.toList());
        return new AuthResponse(token, user.getUsername(), user.getId(), roles, permissions);
    }

    @Transactional(readOnly = true)
    public AuthResponse refreshAccessToken(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(RefreshTokenException::invalid);
        AccountStatusPolicy.requireActive(user);

        java.util.List<org.springframework.security.core.authority.SimpleGrantedAuthority> authorities =
                user.getRoles().stream()
                        .map(role -> new org.springframework.security.core.authority.SimpleGrantedAuthority(role.getCode()))
                        .collect(java.util.stream.Collectors.toList());
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                user.getUsername(), null, authorities);
        String token = jwtTokenProvider.generateToken(authentication);

        java.util.Map<String, Integer> permissionMasks = new java.util.TreeMap<>();
        user.getRoles().forEach(role -> {
            if (role.getRolePermissions() == null) return;
            role.getRolePermissions().forEach(rolePermission -> {
                if (rolePermission.getFunction() == null) return;
                int mask = rolePermission.getActionMask() == null ? 0 : rolePermission.getActionMask();
                permissionMasks.merge(rolePermission.getFunction().getCode(), mask, (left, right) -> left | right);
            });
        });
        java.util.List<com.hotel.dtos.PermissionDTO> permissions = permissionMasks.entrySet().stream()
                .map(entry -> new com.hotel.dtos.PermissionDTO(entry.getKey(), entry.getValue()))
                .collect(java.util.stream.Collectors.toList());
        java.util.List<String> roles = user.getRoles().stream()
                .map(Role::getCode)
                .sorted()
                .collect(java.util.stream.Collectors.toList());

        return new AuthResponse(token, user.getUsername(), user.getId(), roles, permissions);
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
        if (!isBypass) {
            userRepository.findByUsername(authentication.getName()).ifPresent(user -> {
                if (user.getRoles() != null) {
                    user.getRoles().forEach(role -> {
                        if (role.getRolePermissions() != null) {
                            role.getRolePermissions().forEach(rolePermission -> {
                                String functionCode = rolePermission.getFunction().getCode();
                                Integer actionMask = rolePermission.getActionMask();
                                userMasks.merge(functionCode, actionMask != null ? actionMask : 0, (left, right) -> left | right);
                            });
                        }
                    });
                }
            });
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
