package com.hotel.services;

import com.hotel.dtos.AuthResponse;
import com.hotel.dtos.LoginRequest;
import com.hotel.dtos.RegisterRequest;
import com.hotel.dtos.RegistrationResponse;
import com.hotel.dtos.SocialIdentityResponse;
import com.hotel.entities.Role;
import com.hotel.entities.SocialProvider;
import com.hotel.entities.User;
import com.hotel.exceptions.RegistrationConflictException;
import com.hotel.exceptions.SocialAccountLinkException;
import com.hotel.repositories.RoleRepository;
import com.hotel.repositories.UserRepository;
import com.hotel.security.JwtTokenProvider;
import com.hotel.security.AccountStatusPolicy;
import com.hotel.services.social.FacebookIdentityVerifier;
import com.hotel.services.social.GoogleIdentityVerifier;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Locale;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final com.hotel.repositories.AppModuleRepository appModuleRepository;
    private final com.hotel.repositories.AppFunctionRepository appFunctionRepository;
    private final EmailService emailService;
    private final GoogleIdentityVerifier googleIdentityVerifier;
    private final FacebookIdentityVerifier facebookIdentityVerifier;
    private final SocialAccountLinkService socialAccountLinkService;

    public AuthService(AuthenticationManager authenticationManager, UserRepository userRepository,
                       RoleRepository roleRepository, PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       com.hotel.repositories.AppModuleRepository appModuleRepository,
                       com.hotel.repositories.AppFunctionRepository appFunctionRepository,
                       EmailService emailService,
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
        this.emailService = emailService;
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
        AccountStatusPolicy.requireLoginAllowed(authenticatedUser);

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

    public RegistrationResponse register(RegisterRequest registerRequest) {
        String username = normalizeIdentifier(registerRequest.getUsername());
        if (userRepository.existsByUsernameIgnoreCase(username) || userRepository.existsByUsername(username)) {
            throw RegistrationConflictException.username();
        }
        String email = normalizeIdentifier(registerRequest.getEmail());
        String fullName = normalizeDisplayText(registerRequest.getFullName());
        String phone = normalizeOptionalText(registerRequest.getPhone());

        if (userRepository.existsByEmailIgnoreCase(email) || userRepository.existsByEmail(email)) {
            throw RegistrationConflictException.email();
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(registerRequest.getPassword()));
        user.setFullName(fullName);
        user.setPhone(phone);
        user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.now());

        Role customerRole = roleRepository.findByCode("CUSTOMER")
                .orElseThrow(() -> new RuntimeException("Error: Role CUSTOMER is not found."));
        user.setRoles(Collections.singleton(customerRole));

        User savedUser;
        try {
            savedUser = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw resolveRegistrationConflict(username, email, exception);
        }
        boolean welcomeEmailSent = emailService.sendRegistrationSuccess(savedUser.getEmail(), savedUser.getFullName());

        return new RegistrationResponse("User registered successfully!", welcomeEmailSent);
    }

    private RuntimeException resolveRegistrationConflict(
            String username,
            String email,
            DataIntegrityViolationException cause) {
        if (userRepository.existsByUsernameIgnoreCase(username) || userRepository.existsByUsername(username)) {
            return RegistrationConflictException.username();
        }
        if (userRepository.existsByEmailIgnoreCase(email) || userRepository.existsByEmail(email)) {
            return RegistrationConflictException.email();
        }
        return cause;
    }

    private String normalizeIdentifier(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private String normalizeDisplayText(String value) {
        if (value == null) {
            return null;
        }
        return value.strip().replaceAll("\\s+", " ");
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip().replaceAll("\\s+", " ");
    }

    public AuthResponse loginWithGoogle(String idTokenString) {
        User user = socialAccountLinkService.resolveOrLink(googleIdentityVerifier.verify(idTokenString));
        return createSocialAuthResponse(user);
    }

    public AuthResponse loginWithFacebook(String accessToken) {
        User user = socialAccountLinkService.resolveOrLink(facebookIdentityVerifier.verify(accessToken));
        return createSocialAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public java.util.List<SocialIdentityResponse> listSocialIdentities(Long userId) {
        return socialAccountLinkService.list(userId);
    }

    public SocialIdentityResponse linkSocialIdentity(Long userId, String providerName, String credential) {
        SocialProvider provider = parseSocialProvider(providerName);
        com.hotel.services.social.ExternalIdentityProfile profile = provider == SocialProvider.GOOGLE
                ? googleIdentityVerifier.verify(credential)
                : facebookIdentityVerifier.verify(credential);
        return socialAccountLinkService.link(userId, profile);
    }

    public boolean unlinkSocialIdentity(Long userId, String providerName, String currentPassword) {
        return socialAccountLinkService.unlink(userId, parseSocialProvider(providerName), currentPassword);
    }

    private SocialProvider parseSocialProvider(String providerName) {
        try {
            return SocialProvider.fromPath(providerName);
        } catch (IllegalArgumentException exception) {
            throw SocialAccountLinkException.unsupportedProvider();
        }
    }

    AuthResponse createSocialAuthResponse(User user) {
        AccountStatusPolicy.requireLoginAllowed(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                user.getUsername(),
                null,
                user.getRoles().stream()
                        .map(role -> new org.springframework.security.core.authority.SimpleGrantedAuthority(role.getCode()))
                        .collect(java.util.stream.Collectors.toList()));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String token = jwtTokenProvider.generateToken(authentication);
        java.util.List<String> roles = user.getRoles().stream()
                .map(Role::getCode)
                .sorted()
                .collect(java.util.stream.Collectors.toList());
        java.util.Map<String, Integer> permissionMasks = new java.util.TreeMap<>();
        user.getRoles().forEach(role -> {
            if (role.getRolePermissions() == null) return;
            role.getRolePermissions().forEach(rolePermission -> {
                if (rolePermission.getFunction() == null) return;
                permissionMasks.merge(
                        rolePermission.getFunction().getCode(),
                        rolePermission.getActionMask() == null ? 0 : rolePermission.getActionMask(),
                        (left, right) -> left | right);
            });
        });
        java.util.List<com.hotel.dtos.PermissionDTO> permissions = permissionMasks.entrySet().stream()
                .map(entry -> new com.hotel.dtos.PermissionDTO(entry.getKey(), entry.getValue()))
                .collect(java.util.stream.Collectors.toList());
        return new AuthResponse(token, user.getUsername(), user.getId(), roles, permissions);
    }

    @Transactional(readOnly = true)
    public AuthResponse refreshAccessToken(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(com.hotel.security.RefreshTokenException::invalid);
        AccountStatusPolicy.requireLoginAllowed(user);

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
