# BIỂU ĐỒ UML (UML DIAGRAMS)

## 1. Biểu đồ Lớp (Class Diagram) - Phase 1: Security

```mermaid
classDiagram
    class User {
        +Long id
        +String username
        +String email
        +String passwordHash
        +String status
        +Set~Role~ roles
    }

    class Role {
        +Long id
        +String code
        +String name
        +Set~Permission~ permissions
    }

    class Permission {
        +Long id
        +String code
        +String name
    }

    class CustomUserDetails {
        -User user
        +getAuthorities() Collection~GrantedAuthority~
        +getPassword() String
        +getUsername() String
        +isAccountNonExpired() boolean
        +isAccountNonLocked() boolean
        +isCredentialsNonExpired() boolean
        +isEnabled() boolean
    }

    class JwtTokenProvider {
        -String jwtSecret
        -int jwtExpirationMs
        +generateToken(Authentication auth) String
        +getUserUsernameFromJWT(String token) String
        +validateToken(String authToken) boolean
    }

    class JwtAuthenticationFilter {
        +doFilterInternal(HttpServletRequest, HttpServletResponse, FilterChain) void
    }

    class AuthController {
        -AuthenticationManager authenticationManager
        -JwtTokenProvider tokenProvider
        +authenticateUser(LoginRequest) ResponseEntity
    }

    class CustomUserDetailsService {
        -UserRepository userRepository
        +loadUserByUsername(String username) UserDetails
    }

    User "1" --> "*" Role : has
    Role "1" --> "*" Permission : has
    CustomUserDetailsService ..> CustomUserDetails : creates
    CustomUserDetailsService --> User : loads
    JwtAuthenticationFilter --> JwtTokenProvider : uses
    JwtAuthenticationFilter --> CustomUserDetailsService : uses
    AuthController --> JwtTokenProvider : uses
```

### Mục đích
Mô tả cấu trúc các lớp Java trong module xác thực và phân quyền (Security) ở Backend.

### Mô tả
Sơ đồ bao gồm các Entity (`User`, `Role`, `Permission`) và các thành phần cấu hình bảo mật của Spring Security (`JwtTokenProvider`, `JwtAuthenticationFilter`, `CustomUserDetailsService`, `CustomUserDetails`, `AuthController`).

### Phân tích
Lớp `CustomUserDetailsService` đóng vai trò là cầu nối giữa CSDL (`UserRepository`) và Spring Security (`UserDetails`). `JwtAuthenticationFilter` hoạt động như một màng lọc để kiểm tra Token JWT trước khi cho phép truy cập vào các Controller.

### Kết luận
Cấu trúc lớp được thiết kế chặt chẽ, tuân thủ đúng chuẩn của Spring Security để hỗ trợ Stateless Authentication qua JWT.
