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
import com.hotel.security.ActionCode;
import com.hotel.security.CustomUserDetails;
import com.hotel.security.CustomUserDetailsService;
import com.hotel.security.FunctionCode;
import com.hotel.security.RefreshTokenException;
import com.hotel.security.JwtTokenProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hotel.services.social.ExternalIdentityProfile;
import com.hotel.services.social.FacebookIdentityVerifier;
import com.hotel.services.social.GoogleIdentityVerifier;

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
    private final GoogleIdentityVerifier googleIdentityVerifier;
    private final FacebookIdentityVerifier facebookIdentityVerifier;
    private final SocialAccountLinkService socialAccountLinkService;
    private final CustomUserDetailsService customUserDetailsService;

    public AuthService(AuthenticationManager authenticationManager, UserRepository userRepository,
                       RoleRepository roleRepository, PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       com.hotel.repositories.AppModuleRepository appModuleRepository,
                       com.hotel.repositories.AppFunctionRepository appFunctionRepository,
                       GoogleIdentityVerifier googleIdentityVerifier,
                       FacebookIdentityVerifier facebookIdentityVerifier,
                       SocialAccountLinkService socialAccountLinkService,
                       CustomUserDetailsService customUserDetailsService) {
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
        this.customUserDetailsService = customUserDetailsService;
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


        return activateAndBuildResponse(authoritativeAuthentication(authentication));
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
        return activateAndBuildResponse(loadAuthoritativeAuthentication(user.getUsername()));
    }

    @Transactional(readOnly = true)
    public AuthResponse refreshAccessToken(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(RefreshTokenException::invalid);
        AccountStatusPolicy.requireActive(user);

        return activateAndBuildResponse(loadAuthoritativeAuthentication(user.getUsername()));
    }

    @Transactional(readOnly = true)
    public java.util.List<com.hotel.dtos.AppModuleDto> getMyMenu() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required for the menu context.");
        }

        boolean isBypass = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("SUPER_ADMIN")
                        || a.getAuthority().equals("ROLE_SUPER_ADMIN"));
        java.util.Map<FunctionCode, Integer> userMasks = java.util.Map.of();
        if (!isBypass) {
            if (!(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Authoritative server permission context is required.");
            }
            userMasks = userDetails.getPermissionMasks();
        }

        java.util.List<com.hotel.entities.AppModule> allModules =
                new java.util.ArrayList<>(appModuleRepository.findAll());
        java.util.List<com.hotel.entities.AppFunction> allFunctions =
                new java.util.ArrayList<>(appFunctionRepository.findAll());

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
                    if (isBypass || canView(userMasks, func.getCode())) {
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

    private Authentication authoritativeAuthentication(Authentication authentication) {
        if (authentication.getPrincipal() instanceof CustomUserDetails) {
            return authentication;
        }
        return loadAuthoritativeAuthentication(authentication.getName());
    }

    private Authentication loadAuthoritativeAuthentication(String username) {
        UserDetails details = customUserDetailsService.loadUserByUsername(username);
        if (!(details instanceof CustomUserDetails)) {
            throw new IllegalStateException("Authoritative authentication principal is unavailable.");
        }
        return UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities());
    }

    private AuthResponse activateAndBuildResponse(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new IllegalStateException("Authoritative authentication principal is unavailable.");
        }
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String token = jwtTokenProvider.generateToken(authentication);
        java.util.List<String> roles = authentication.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .distinct()
                .sorted()
                .toList();
        java.util.List<com.hotel.dtos.PermissionDTO> permissions = userDetails.getPermissionMasks().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(entry -> new com.hotel.dtos.PermissionDTO(entry.getKey().name(), entry.getValue()))
                .toList();
        return new AuthResponse(token, userDetails.getUsername(), userDetails.getUserId(), roles, permissions);
    }

    private boolean canView(java.util.Map<FunctionCode, Integer> masks, String functionCode) {
        try {
            Integer mask = masks.get(FunctionCode.valueOf(functionCode));
            return mask != null && (mask & ActionCode.VIEW) == ActionCode.VIEW;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
