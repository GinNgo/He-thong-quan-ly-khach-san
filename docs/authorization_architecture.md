# Kiến Trúc Phân Quyền (Authorization Architecture)

Hệ thống sử dụng kiến trúc phân quyền động dựa trên mô hình **Module → Function → Action** kết hợp với **Bitwise Mask**, tương tự như các hệ thống ERP/HIS doanh nghiệp.

## 1. Cơ sở dữ liệu (Database Design)

Cơ sở dữ liệu được thiết kế thành các bảng chính như sau:

- `app_module`: Quản lý các phân hệ lớn của hệ thống (Ví dụ: SYSTEM, HOTEL, FINANCE).
- `app_function`: Quản lý các chức năng cụ thể nằm trong từng Module (Ví dụ: USER, ROOM, RESERVATION). Mỗi Function đại diện cho một màn hình hoặc nghiệp vụ.
- `app_role`: Quản lý các vai trò người dùng (Ví dụ: SUPER_ADMIN, RECEPTIONIST).
- `app_user_role`: Bảng trung gian n-n kết nối `users` và `app_role`.
- `app_role_permission`: Bảng quy định quyền hạn. Mỗi Role sẽ được cấp quyền trên từng Function thông qua cột `action_mask` (sử dụng toán tử bit).

### Bitwise Action Mask
Quyền hạn (Actions) được định nghĩa dưới dạng số nguyên lũy thừa của 2:
- VIEW = 1
- CREATE = 2
- UPDATE = 4
- DELETE = 8
- EXPORT = 16
- APPROVE = 32

Nếu một Role có quyền VIEW và CREATE, `action_mask` sẽ là 1 + 2 = 3. 
Toán tử bit `AND (&)` được sử dụng để kiểm tra: `(userMask & requiredAction) == requiredAction`.

## 2. UML Class Diagram

```mermaid
classDiagram
    class User {
        +Long id
        +String username
        +Set~Role~ roles
    }
    class Role {
        +Long id
        +String code
        +String name
        +Set~RolePermission~ rolePermissions
    }
    class AppModule {
        +Long id
        +String code
        +String name
    }
    class AppFunction {
        +Long id
        +String code
        +String name
        +AppModule module
    }
    class RolePermission {
        +Long id
        +Integer actionMask
        +Role role
        +AppFunction function
    }

    User "1" *-- "*" Role : app_user_role
    Role "1" *-- "*" RolePermission : app_role_permission
    RolePermission "*" --> "1" AppFunction
    AppFunction "*" --> "1" AppModule
```

## 3. Luồng hoạt động (Sequence Diagram)

Quy trình kiểm tra phân quyền khi có một request tới API:

```mermaid
sequenceDiagram
    participant Client
    participant Controller
    participant PermissionInterceptor
    participant SecurityContext
    
    Client->>Controller: HTTP Request (kèm JWT Token)
    Note over Controller: Spring Security giải mã JWT<br/>lưu User vào SecurityContext
    Controller->>PermissionInterceptor: Chặn Request (preHandle)
    PermissionInterceptor->>Controller: Đọc Annotation @Permission(Function, Action)
    PermissionInterceptor->>SecurityContext: Lấy CustomUserDetails
    SecurityContext-->>PermissionInterceptor: Trả về Map<FunctionCode, Integer>
    Note over PermissionInterceptor: Tính toán Toán tử BIT:<br/>(userMask & requiredAction) == requiredAction
    alt Hợp lệ (Hợp lệ toán tử BIT)
        PermissionInterceptor-->>Controller: Cho phép tiếp tục (return true)
        Controller-->>Client: HTTP 200 OK + Data
    else Không hợp lệ
        PermissionInterceptor-->>Client: HTTP 403 Forbidden
    end
```
