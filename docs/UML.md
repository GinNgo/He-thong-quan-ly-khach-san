# HỆ THỐNG BIỂU ĐỒ UML CHI TIẾT

Tài liệu này tập hợp toàn bộ các biểu đồ phân tích và thiết kế hệ thống chi tiết cho từng phân hệ nghiệp vụ, bao gồm: Use Case, Hoạt động (Activity), Tuần tự (Sequence) và Biểu đồ Lớp (Class). Toàn bộ các biểu đồ đều được đổ màu theo chuẩn nhận diện cấu trúc.

---

## 1. PHÂN HỆ QUẢN LÝ ĐA CƠ SỞ VÀ GÓI DỊCH VỤ (MULTI-PROPERTY & SUBSCRIPTION)

### 1.1. Biểu đồ Use Case (Tổng quan)
Mô tả các quyền hạn và chức năng chính của các nhóm người dùng trong nền tảng.

```plantuml
@startuml
left to right direction
skinparam packageStyle rectangle

actor "Khách hàng" as Guest
actor "Chủ cơ sở (Owner)" as Owner
actor "Quản trị viên (Super Admin)" as Admin

rectangle "Phân hệ Đa cơ sở & Gói dịch vụ" {
  usecase "Tìm kiếm cơ sở lưu trú theo địa điểm" as UC1
  usecase "Đăng ký tài khoản và Đăng cơ sở mới" as UC2
  usecase "Quản lý cơ sở và Phòng của mình" as UC3
  usecase "Mua/Gia hạn Gói dịch vụ (Subscription)" as UC4
  usecase "Duyệt Cơ sở và Bài đăng" as UC5
  usecase "Quản lý toàn bộ Gói và Cơ sở" as UC6
}

Guest --> UC1
Guest --> UC2

Owner --> UC3
Owner --> UC4

Admin --> UC5
Admin --> UC6
@enduml
```

### 1.2. Biểu đồ Hoạt động (Activity Diagram) - Luồng đăng cơ sở và duyệt
Mô tả luồng từ khi người dùng tạo cơ sở đến khi hiển thị công khai.

```mermaid
stateDiagram-v2
    [*] --> NhapThongTin: Owner tạo Cơ sở mới
    NhapThongTin --> LuuNhaps: Bấm "Lưu Nháp"
    LuuNhaps --> NhapThongTin: Chỉnh sửa thêm
    NhapThongTin --> GuiDuyet: Bấm "Gửi Duyệt"
    GuiDuyet --> PENDING_APPROVAL: Trạng thái chờ duyệt
    
    state PENDING_APPROVAL {
        [*] --> AdminKiemTra: Super Admin xem thông tin
    }
    
    PENDING_APPROVAL --> REJECTED: Admin từ chối
    REJECTED --> NhapThongTin: Yêu cầu sửa lại
    
    PENDING_APPROVAL --> APPROVED: Admin phê duyệt
    APPROVED --> ACTIVE: Cơ sở được kích hoạt
    ACTIVE --> [*]: Hiển thị trên Public Search
    
    style PENDING_APPROVAL fill:#fdf2d0,stroke:#333
    style APPROVED fill:#d4e1f9,stroke:#333
    style REJECTED fill:#f9d0c4,stroke:#333
    style ACTIVE fill:#e1f9d4,stroke:#333
```

### 1.3. Biểu đồ Hoạt động (Activity Diagram) - Kích hoạt và Hết hạn gói
Mô tả luồng vòng đời của một gói Subscription.

```mermaid
stateDiagram-v2
    [*] --> ThanhToan: Owner mua gói
    ThanhToan --> ACTIVE_SUB: Thanh toán thành công
    
    state ACTIVE_SUB {
        [*] --> CapNhatQuyen: Mở khóa Feature (Limit tăng)
    }
    
    ACTIVE_SUB --> QuetHan: Job chạy hàng ngày
    QuetHan --> ACTIVE_SUB: Chưa hết hạn
    QuetHan --> EXPIRED: Đã qua ngày hết hạn
    
    EXPIRED --> ThuHoiQuyen: Khoá Feature nâng cao
    ThuHoiQuyen --> ACTIVE_SUB: Owner gia hạn thành công
    
    style ACTIVE_SUB fill:#d4e1f9,stroke:#333
    style EXPIRED fill:#f9d0c4,stroke:#333
```

### 1.4. Biểu đồ Tuần tự (Sequence Diagram) - Load User Context (RBAC + Feature)
Mô tả luồng khi người dùng tải lại trang và hệ thống trả về thông tin quyền hạn tổng hợp.

```mermaid
sequenceDiagram
    participant UI as Frontend
    participant Auth as AuthController
    participant Svc as UserService
    participant SubSvc as SubscriptionService
    participant DB as SQL Server
    
    UI->>Auth: GET /api/users/me
    Auth->>Svc: getMyProfile()
    Svc->>DB: Lấy User & Role
    DB-->>Svc: UserInfo + Permissions
    Svc->>SubSvc: getActiveSubscription(userId)
    SubSvc->>DB: Tìm AccountSubscription đang ACTIVE
    DB-->>SubSvc: Dữ liệu Gói
    SubSvc->>DB: Lấy PlanFeatures của Gói
    DB-->>SubSvc: Danh sách Feature & Limits
    SubSvc-->>Svc: Trả về Subscription Context
    Svc-->>Auth: Tổng hợp Context (Roles + Features)
    Auth-->>UI: 200 OK (Full Context)
    UI->>UI: Sinh Menu động & Khóa nút chức năng
```

### 1.5. Biểu đồ Tuần tự (Sequence Diagram) - Kiểm tra Feature Authorization
Mô tả cơ chế chặn API nếu vượt quá giới hạn của gói.

```mermaid
sequenceDiagram
    participant UI as Frontend
    participant Room as RoomController
    participant Intercept as FeatureInterceptor
    participant FeatSvc as SubscriptionFeatureService
    participant DB as SQL Server
    
    UI->>Room: POST /api/rooms (Thêm phòng)
    Room->>Intercept: @RequireFeature("ROOM_COUNT_LIMIT")
    Intercept->>FeatSvc: checkLimit("ROOM_COUNT_LIMIT")
    FeatSvc->>DB: COUNT(rooms) WHERE hotel_id = X
    DB-->>FeatSvc: result = 10
    FeatSvc->>DB: Lấy cấu hình Limit từ Gói của Owner
    DB-->>FeatSvc: limit = 10
    
    alt Số phòng đã đạt giới hạn
        FeatSvc-->>Intercept: False
        Intercept-->>UI: 403 Forbidden (Upgrade Required)
    else Còn hạn mức
        FeatSvc-->>Intercept: True
        Intercept->>Room: Cho phép thực thi API
        Room->>DB: Lưu phòng mới
        DB-->>Room: OK
        Room-->>UI: 201 Created
    end
```

### 1.6. Biểu đồ Lớp (Class Diagram) - Property Management
```mermaid
classDiagram
    class Location {
        +Long id
        +Long parentId
        +String code
        +String nameVi
        +String type
    }
    
    class Hotel {
        +Long id
        +String name
        +String status
        +Long provinceId
        +Long districtId
        +Long wardId
    }
    
    class UserProperty {
        +Long id
        +Long userId
        +Long hotelId
        +String relationshipType
    }
    
    class PropertyImage {
        +Long id
        +Long hotelId
        +String imageUrl
        +Boolean isPrimary
    }
    
    Hotel "1" --> "1" Location : belongs to
    Hotel "1" --> "*" PropertyImage : has
    UserProperty "*" --> "1" Hotel : maps to
    UserProperty "*" --> "1" User : maps to
    
    style Hotel fill:#d4e1f9,stroke:#333
    style Location fill:#f9d0c4,stroke:#333
```

### 1.7. Biểu đồ Lớp (Class Diagram) - Subscription Management
```mermaid
classDiagram
    class SubscriptionPlan {
        +Long id
        +String code
        +String nameVi
        +String billingType
        +BigDecimal price
        +Boolean isLifetime
    }
    
    class PlanFeature {
        +Long id
        +Long planId
        +String featureCode
        +Integer limitValue
    }
    
    class AccountSubscription {
        +Long id
        +Long userId
        +Long planId
        +LocalDateTime startAt
        +LocalDateTime endAt
        +String status
    }
    
    SubscriptionPlan "1" --> "*" PlanFeature : contains
    AccountSubscription "*" --> "1" SubscriptionPlan : references
    User "1" --> "*" AccountSubscription : subscribes
    
    style SubscriptionPlan fill:#ffe6cc,stroke:#333
    style AccountSubscription fill:#e1f9d4,stroke:#333
```

---

## 2. PHÂN HỆ XÁC THỰC VÀ PHÂN QUYỀN (AUTH & RBAC CƠ BẢN)

### 2.1. Biểu đồ Tuần tự (Sequence Diagram) - Luồng xác thực JWT

```mermaid
sequenceDiagram
    participant U as Người dùng
    participant UI as Angular Frontend
    participant C as AuthController
    participant S as CustomUserDetailsService
    participant JWT as JwtTokenProvider
    participant DB as SQL Server

    U->>UI: Điền Username & Password
    UI->>C: POST /api/auth/login
    C->>S: loadUserByUsername()
    S->>DB: SELECT * FROM users
    DB-->>S: Trả về UserDetails
    S-->>C: Trả về đối tượng xác thực
    C->>C: Kiểm tra Password Hash
    alt Sai mật khẩu
        C-->>UI: 401 Unauthorized
        UI-->>U: Báo lỗi đăng nhập
    else Đúng mật khẩu
        C->>JWT: generateToken()
        JWT-->>C: Trả về chuỗi JWT Token
        C-->>UI: 200 OK + JWT + User Info
        UI->>UI: Lưu Token vào Storage
        UI-->>U: Chuyển hướng
    end
```

### 2.2. Biểu đồ Lớp (Class Diagram) - Security Phase

```mermaid
classDiagram
    class User {
        +Long id
        +String username
        +String passwordHash
        +Set~Role~ roles
    }

    class Role {
        +Long id
        +String code
        +Set~RolePermission~ rolePermissions
    }

    class AppFunction {
        +Long id
        +String code
        +Integer sortOrder
    }

    class RolePermission {
        +Long id
        +Integer actionMask
    }

    User "1" --> "*" Role : has
    Role "1" --> "*" RolePermission : has
    AppFunction "1" <-- "*" RolePermission : applies to
```

---

## 3. PHÂN HỆ ĐẶT PHÒNG & THANH TOÁN (RESERVATION & INVOICE)

### 3.1. Biểu đồ Tuần tự (Sequence Diagram) - Tìm kiếm phòng trống (Search)

```mermaid
sequenceDiagram
    participant Guest as Khách hàng
    participant UI as Frontend
    participant Svc as HotelController
    participant Repo as RoomRepository
    participant DB as SQL Server
    
    Guest->>UI: Nhập Địa điểm, Ngày, Số người
    UI->>Svc: GET /api/v1/hotels/public/search
    Svc->>Repo: findAvailableRoomsByRoomTypeAndDate()
    Note right of Repo: Thực hiện query phức tạp<br/>Check khoảng trùng ngày
    Repo->>DB: SELECT ... WHERE check_in...
    DB-->>Repo: Danh sách phòng trống
    Repo-->>Svc: Map to DTO
    Svc-->>UI: Danh sách Cơ sở và Phòng
    UI-->>Guest: Hiển thị kết quả tìm kiếm
```

---

## 4. PHÂN HỆ TÌM KIẾM ĐỊA LÝ (GEOLOCATION SEARCH)

### 4.1. Biểu đồ Tuần tự (Sequence Diagram) - Autocomplete & Search theo Tỉnh/Phường

```mermaid
sequenceDiagram
    participant Guest as Khách hàng
    participant UI as Frontend
    participant LocCtrl as LocationController
    participant Svc as PropertySearchController
    participant DB as SQL Server
    
    Guest->>UI: Gõ từ khóa địa điểm (debounce 300ms)
    UI->>LocCtrl: GET /api/public/locations/search?keyword=...
    LocCtrl->>DB: Truy vấn locations (Tỉnh, Phường) matching keyword
    DB-->>LocCtrl: Danh sách gợi ý Location
    LocCtrl-->>UI: Hiển thị dropdown
    Guest->>UI: Chọn một Phường (wardId=X, provinceId=Y)
    Guest->>UI: Bấm Tìm kiếm (Kèm latitude/longitude nếu có)
    UI->>Svc: GET /api/public/properties/search
    Svc->>DB: Query Hotels WHERE ward_id=X AND status=ACTIVE
    Note right of DB: Tính Haversine distance nếu có lat/lng
    DB-->>Svc: Page<PropertySearchResponseDTO>
    Svc-->>UI: Danh sách khách sạn (có distance)
    UI-->>Guest: Hiển thị kết quả kèm khoảng cách
```

### 4.2. Biểu đồ Tuần tự (Sequence Diagram) - Import Data Tỉnh/Phường

```mermaid
sequenceDiagram
    participant Admin as Hệ thống (Startup)
    participant ImportSvc as LocationImportService
    participant FS as File System
    participant DB as SQL Server
    
    Admin->>ImportSvc: Chạy @PostConstruct (nếu enabled)
    ImportSvc->>FS: Đọc JSON (LOCATION_JSON_PATH)
    FS-->>ImportSvc: Dữ liệu JSON (Tỉnh -> Huyện -> Xã)
    ImportSvc->>ImportSvc: Bỏ qua Huyện, gán Xã trực tiếp vào Tỉnh (Flatten)
    ImportSvc->>DB: @Transactional saveAll(locations)
    Note right of DB: Lưu Tỉnh và Phường/Xã với parent_id
    DB-->>ImportSvc: Hoàn tất
    ImportSvc-->>Admin: Ghi Log Report (tổng số import, số lỗi)
```

## 6. PHÂN HỆ IMPORT DỮ LIỆU TỰ ĐỘNG & CLAIM CƠ SỞ

### 6.1. Biểu đồ Use Case (Import & Claim)
```plantuml
@startuml
left to right direction
actor "Khách hàng" as Guest
actor "Quản trị viên (Super Admin)" as Admin

rectangle "Phân hệ Import & Claim" {
  usecase "Tìm kiếm & Lọc dữ liệu từ API" as UC1
  usecase "Xem Preview và Sàng lọc Trùng lặp" as UC2
  usecase "Import dữ liệu vào hệ thống" as UC3
  usecase "Gửi yêu cầu nhận quyền (Claim)" as UC4
  usecase "Duyệt yêu cầu Claim" as UC5
}

Admin --> UC1
Admin --> UC2
Admin --> UC3
Admin --> UC5
Guest --> UC4
@enduml
```

### 6.2. Biểu đồ Tuần tự (Sequence Diagram) - Luồng Deduplicate và Import
```plantuml
@startuml
actor Admin
participant "PropertyImportController" as Controller
participant "PropertyImportService" as ImportService
participant "AccommodationDataProvider" as Provider
database "Database (Staging & Property)" as DB

Admin -> Controller: POST /api/admin/property-imports/search
activate Controller
Controller -> ImportService: searchAndStageProperties()
activate ImportService

ImportService -> Provider: search(request)
activate Provider
Provider --> ImportService: List<ProviderSearchResult>
deactivate Provider

ImportService -> DB: Create PropertyImportBatch
loop Cho mỗi kết quả
    ImportService -> DB: Check Duplicate (Mức 1-5: ID, Tên, Tọa độ, SĐT)
    alt Exact Duplicate
        ImportService -> DB: Lưu Staging (Status=EXACT_DUPLICATE)
    else Possible Duplicate
        ImportService -> DB: Lưu Staging (Status=POSSIBLE_DUPLICATE)
    else New
        ImportService -> DB: Lưu Staging (Status=NEW)
    end
end
ImportService --> Controller: BatchPreviewResult
deactivate ImportService
Controller --> Admin: JSON Preview
deactivate Controller

Admin -> Controller: POST /api/admin/property-imports/{batchId}/import
activate Controller
Controller -> ImportService: importValidItems(batchId)
activate ImportService
ImportService -> DB: Select valid NEW items
loop
    ImportService -> DB: Insert to hotels (approval_status=IMPORTED_PENDING_REVIEW)
end
ImportService --> Controller: ImportResult
deactivate ImportService
Controller --> Admin: Success Response
deactivate Controller
@enduml
```

### 6.3. Biểu đồ Hoạt động (Activity Diagram) - Nhận quyền cơ sở
```plantuml
@startuml
start
:User xem trang chi tiết khách sạn;
if (Khách sạn ở trạng thái IMPORTED_PENDING_REVIEW?) then (Có)
  :Hiển thị nút "Xác nhận quyền quản lý";
  :User click và điền form xác minh (giấy phép kinh doanh...);
  :Lưu PropertyClaimRequest (PENDING);
  :Admin nhận thông báo;
  :Admin kiểm tra giấy tờ;
  if (Hợp lệ?) then (Đồng ý)
    :Chuyển trạng thái Khách sạn -> ACTIVE;
    :Tạo record user_properties với Role=OWNER;
    :Gửi email thông báo cấp quyền thành công;
  else (Từ chối)
    :Cập nhật PropertyClaimRequest (REJECTED);
    :Gửi email yêu cầu bổ sung thông tin;
  end if
else (Không)
  :Chỉ xem thông tin bình thường;
end if
stop
@enduml
```
# Bổ sung UML: import, search và booking (2026-07-15)

## Import địa giới UTF-8 idempotent

```mermaid
sequenceDiagram
    participant Boot as ApplicationReady
    participant Importer as LocationImportService
    participant Json as 34_tinh_huyen_xa.json
    participant DB as SQL Server
    Boot->>Importer: chạy khi cấu hình bật
    Importer->>Json: đọc InputStream UTF-8, xử lý BOM
    Importer->>DB: upsert PROVINCE theo type + sourceCode
    loop district metadata và ward
        Importer->>DB: upsert WARD theo type + sourceCode, parent=province
    end
    Importer->>DB: kiểm tra DISTRICT, parent, null và ký tự ?
    Importer-->>Boot: added/updated/skipped/errors
```

## Search không dấu và tồn phòng

```mermaid
sequenceDiagram
    participant UI as Angular
    participant API as Public API
    participant N as VietnameseTextNormalizer
    participant Inventory as RoomAvailabilityService
    participant DB as SQL Server
    UI->>API: keyword/location/dates/guests/roomCount
    API->>N: normalize một lần
    API->>DB: truy vấn normalized columns + code + slug
    DB-->>API: PROVINCE, WARD, PROPERTY hợp lệ
    API->>Inventory: kiểm tra sức chứa và tồn nếu có ngày
    Inventory->>DB: active - maintenance - overlapping reservations
    API-->>UI: kết quả thật, phân nhóm và click được
```

## Booking và gán phòng

```mermaid
sequenceDiagram
    participant Guest as Khách
    participant Booking as ReservationService
    participant Inventory as RoomAvailabilityService
    participant DB as SQL Server
    Guest->>Booking: roomType + quantity + adults + children + dates
    Booking->>Inventory: khóa và kiểm tra tồn/sức chứa
    Booking->>DB: lưu reservation_details, chưa cần gán room
    Note over Inventory,DB: overlap: existing.checkIn < requestedCheckOut AND existing.checkOut > requestedCheckIn
    Booking->>DB: khi check-in gán đủ quantity vào reservation_rooms
```

Owner lấy `activeProperty` từ user context phía server và không xem/sửa dữ liệu cơ sở khác. Super Admin được chọn property nhưng vẫn phải qua kiểm tra quyền. API không tin `hotelId` do frontend tự gửi.

# Bổ sung UML: seeder, subscription và vòng đời lưu trú (2026-07-15)

```mermaid
sequenceDiagram
    participant Boot as ApplicationReady
    participant Seed as NationwideDemoSeedService
    participant Batch as Transaction batch
    participant DB as SQL Server
    Boot->>Seed: enabled + nationwide-property-seed + profile hợp lệ
    Seed->>DB: đọc Province/Ward thật
    loop theo Province hoặc Ward và giới hạn cấu hình
        Seed->>Batch: upsert theo seed_key
        Batch->>DB: Hotel + images + RoomType + Room + service
        Batch->>DB: Owner + UserProperty + Subscription
        Batch->>DB: lưu progress COMMITTED/FAILED
    end
    Seed-->>Boot: SeedReport không chứa mật khẩu
```

```mermaid
stateDiagram-v2
    [*] --> NO_PLAN
    NO_PLAN --> FREE: cấp gói Basic/Free
    NO_PLAN --> STANDARD: thanh toán thành công
    FREE --> STANDARD: nâng cấp
    STANDARD --> BUSINESS: nâng cấp
    STANDARD --> EXPIRED: hết hạn
    BUSINESS --> EXPIRED: hết hạn
    NO_PLAN --> LIFETIME: mua vĩnh viễn
    FREE --> REVOKED: Admin thu hồi có lịch sử
    STANDARD --> REVOKED: Admin thu hồi có lịch sử
    LIFETIME --> REVOKED: Admin thu hồi có lịch sử
```

Account status, subscription status và property approval status là ba trạng thái độc lập. Quyền truy cập dữ liệu lấy từ `user_properties`; giới hạn chức năng lấy từ `plan_features`; không nhận `hotelId` frontend nếu hotel không thuộc context đăng nhập.

```mermaid
sequenceDiagram
    participant Staff as Lễ tân
    participant API as Reservation API
    participant Inventory as Availability Service
    participant DB as SQL Server
    Staff->>API: check-in reservation
    API->>Inventory: lấy phòng trống đúng Hotel + RoomType
    Staff->>API: assign đủ quantity
    API->>DB: ReservationRoom + Room=OCCUPIED + Reservation=CHECKED_IN
    Staff->>API: thêm dịch vụ theo giá snapshot
    API->>DB: ReservationService(quantity, unitPrice, amount)
    Staff->>API: check-out và payment
    API->>DB: tạo/cập nhật Invoice
    API->>DB: Reservation=CHECKED_OUT, Room=DIRTY
    API->>DB: tạo HousekeepingTask=PENDING
    Staff->>API: hoàn tất dọn phòng
    API->>DB: Room=AVAILABLE, housekeeping=CLEAN
```
