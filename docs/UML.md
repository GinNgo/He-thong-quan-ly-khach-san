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

## 2. Biểu đồ Lớp (Class Diagram) - Phase 2: Shared Components & Dashboard

```mermaid
classDiagram
    class Dashboard {
        +totalWorkOrders: number
        +loadingWorkOrders: boolean
        +ngOnInit() void
        +loadWorkOrders() void
        +onPageChange(event: PageRequest) void
    }

    class DataTable {
        +columns: ColumnDefinition[]
        +data: any[]
        +totalRecords: number
        +loading: boolean
        +onLazyLoad(event) void
        +onGlobalSearch(event) void
    }

    class StatCard {
        +title: string
        +value: string
        +icon: string
        +status: string
    }

    class AnalyticsService {
        +getDashboardData() Observable
        +getWorkOrders() Observable
    }

    Dashboard *-- DataTable : uses
    Dashboard *-- StatCard : uses
    Dashboard ..> AnalyticsService : injects

    style Dashboard fill:#f9d0c4,stroke:#333,stroke-width:1px
    style DataTable fill:#d4e1f9,stroke:#333,stroke-width:1px
    style StatCard fill:#ffe6cc,stroke:#333,stroke-width:1px
    style AnalyticsService fill:#fdf2d0,stroke:#333,stroke-width:1px
```

## 3. Biểu đồ Tuần tự (Sequence Diagram) - Lazy Loading Data Table

```mermaid
sequenceDiagram
    participant User as Quản trị viên
    participant UI as Bảng điều khiển (Dashboard)
    participant Table as Component Bảng dữ liệu (DataTable)
    participant API as Backend API (AnalyticsService)

    User->>UI: Truy cập trang Dashboard
    UI->>API: getDashboardData()
    API-->>UI: Trả về số liệu tổng quan
    UI->>Table: Khởi tạo với [lazy]=true
    Table->>Table: Kích hoạt sự kiện onLazyLoad lần đầu
    Table->>UI: emit pageChange(page=1, size=10)
    UI->>UI: Hiển thị vòng xoay loading = true
    UI->>API: loadWorkOrders(page=1, size=10)
    API-->>UI: Trả về danh sách rỗng (Total = 0)
    UI->>Table: Cập nhật [data] = [], [loading] = false
    Table->>User: Hiển thị Giao diện chưa có dữ liệu (Empty State)
```
