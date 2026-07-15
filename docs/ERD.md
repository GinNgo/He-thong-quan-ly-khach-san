# BẢN ĐỒ THỰC THỂ KẾT HỢP (ERD)

## Hệ thống Quản lý Đa Cơ sở & Gói Dịch vụ

```mermaid
erDiagram
    users {
        bigint id PK
        varchar username UK
        varchar email UK
        varchar password_hash
        varchar full_name
        varchar phone
        varchar avatar_url
        varchar status
        bigint hotel_id FK "Legacy/Default property"
        datetime created_at
        datetime updated_at
    }

    app_role {
        bigint id PK
        varchar code UK
        varchar name
        varchar description
    }

    app_user_role {
        bigint user_id FK
        bigint role_id FK
    }

    app_module {
        bigint id PK
        varchar code UK
        varchar name
    }

    app_function {
        bigint id PK
        bigint module_id FK
        varchar code UK
        varchar name
    }

    app_role_permission {
        bigint id PK
        bigint role_id FK
        bigint function_id FK
        int action_mask
    }

    locations {
        bigint id PK
        bigint parent_id FK
        varchar code UK
        varchar source_code
        varchar name_vi
        varchar name_en
        varchar normalized_name
        varchar location_type "PROVINCE/WARD/LANDMARK"
        varchar full_path
        varchar legacy_parent_name
        float latitude
        float longitude
        varchar status
        int sort_order
        varchar slug
        datetime created_at
        datetime updated_at
        varchar created_by
        varchar updated_by
    }

    hotels {
        bigint id PK
        varchar name
        text description
        varchar address
        varchar city
        varchar country
        int star_rating
        varchar main_image
        varchar status "ACTIVE/INACTIVE/DRAFT/PENDING"
        bigint province_id FK
        bigint ward_id FK
        varchar address_line
        float latitude
        float longitude
        datetime created_at
        datetime updated_at
        varchar approval_status
        varchar external_provider
        varchar external_id
        varchar property_type
        varchar phone
        varchar website
        float average_rating
        int review_count
    }

    property_import_batches {
        bigint id PK
        varchar provider
        bigint province_id
        bigint ward_id
        varchar search_keyword
        float radius_km
        varchar status
        int total_found
        int total_new
        int total_duplicate
        int total_selected
        int total_imported
        int total_failed
        datetime started_at
        datetime completed_at
    }

    property_import_items {
        bigint id PK
        bigint batch_id FK
        varchar external_provider
        varchar external_id
        varchar raw_name
        varchar normalized_name
        varchar raw_address
        bigint province_id
        bigint ward_id
        float latitude
        float longitude
        varchar duplicate_status
        bigint duplicate_property_id
        varchar import_status
    }

    property_external_photos {
        bigint id PK
        bigint property_id FK
        varchar provider
        varchar external_photo_id
        varchar photo_reference
        varchar display_url
    }

    property_claim_requests {
        bigint id PK
        bigint property_id FK
        bigint requester_user_id FK
        varchar verification_method
        varchar status
        datetime created_at
    }

    property_images {
        bigint id PK
        bigint hotel_id FK
        varchar image_url
        bit is_primary
    }

    user_properties {
        bigint id PK
        bigint user_id FK
        bigint hotel_id FK
        varchar relationship_type "OWNER/STAFF"
    }

    room_types {
        bigint id PK
        bigint hotel_id FK
        varchar code
        varchar name_vi
        varchar name_en
        int max_guest
        decimal base_price
    }

    rooms {
        bigint id PK
        bigint room_type_id FK
        varchar room_number
        int floor
        varchar status
    }

    room_images {
        bigint id PK
        bigint room_id FK
        varchar image_url
        bit is_primary
    }

    services {
        bigint id PK
        varchar code UK
        varchar name_vi
        varchar name_en
        decimal price
        varchar status
    }

    reservations {
        bigint id PK
        bigint user_id FK
        bigint room_id FK
        bigint hotel_id FK
        date check_in_date
        date check_out_date
        int guests
        decimal total_amount
        varchar status
    }

    reservation_details {
        bigint id PK
        bigint reservation_id FK
        bigint room_id FK
        decimal price
    }

    invoices {
        bigint id PK
        varchar invoice_code UK
        bigint reservation_id FK
        date issue_date
        decimal total_amount
        varchar status
    }

    payments {
        bigint id PK
        bigint reservation_id FK
        decimal amount
        varchar payment_method
        varchar status
    }

    subscription_plans {
        bigint id PK
        varchar code UK
        varchar name_vi
        varchar name_en
        varchar billing_type
        decimal price
        bit is_lifetime
        varchar status
    }

    plan_features {
        bigint id PK
        bigint plan_id FK
        varchar feature_code
        int limit_value
    }

    account_subscriptions {
        bigint id PK
        bigint user_id FK
        bigint plan_id FK
        datetime start_at
        datetime end_at
        bit is_lifetime
        varchar status
    }

    users ||--o{ app_user_role : has
    app_role ||--o{ app_user_role : belongs_to
    app_module ||--o{ app_function : contains
    app_role ||--o{ app_role_permission : has
    app_function ||--o{ app_role_permission : belongs_to
    
    locations ||--o{ locations : "parent-child"
    hotels ||--o{ property_images : has
    hotels ||--o{ user_properties : managed_by
    users ||--o{ user_properties : manages
    
    property_import_batches ||--o{ property_import_items : contains
    hotels ||--o{ property_external_photos : has
    hotels ||--o{ property_claim_requests : receives
    users ||--o{ property_claim_requests : requests
    
    hotels ||--o{ room_types : has
    room_types ||--o{ rooms : contains
    rooms ||--o{ room_images : has
    
    users ||--o{ reservations : makes
    rooms ||--o{ reservations : booked_in
    hotels ||--o{ reservations : belongs_to
    reservations ||--o{ reservation_details : contains
    rooms ||--o{ reservation_details : included_in
    
    reservations ||--o| invoices : generates
    reservations ||--o{ payments : has
    
    subscription_plans ||--o{ plan_features : provides
    users ||--o{ account_subscriptions : subscribes_to
    subscription_plans ||--o{ account_subscriptions : active_for
```

## Giải thích mở rộng (Multi-Property & Subscriptions)

### 1. Quản lý Địa điểm & Cơ sở
- **`locations`**: Quản lý cây địa điểm hành chính (Tỉnh/Thành -> Quận/Huyện -> Phường/Xã).
- **`hotels` (đóng vai trò Property)**: Entity lõi quản lý thông tin cơ sở lưu trú. Đã bổ sung liên kết đến `locations`.
- **`user_properties`**: Mapping giữa người dùng và cơ sở lưu trú, xác định ai là chủ (OWNER), ai là nhân viên (STAFF).

### 2. Gói dịch vụ (Subscription Feature Gate)
- **`subscription_plans`**: Các gói dịch vụ cung cấp (Free, Standard, Premium, Lifetime).
- **`plan_features`**: Cấu hình các tính năng và giới hạn (ví dụ: tối đa 10 phòng, tối đa 5 ảnh).
- **`account_subscriptions`**: Gói dịch vụ hiện tại mà một người dùng (Owner) đang kích hoạt. Hệ thống sẽ kết hợp giữa RBAC (app_role_permission) và bảng này để quyết định có cho phép thực hiện thao tác hay không.

### 3. Tự động Nhập dữ liệu (Automated Property Import)
- **`property_import_batches`**: Các phiên tìm kiếm và thu thập dữ liệu khách sạn từ API ngoài.
- **`property_import_items`**: Dữ liệu thô từ API ngoài trước khi duyệt, được kiểm tra deduplication.
- **`property_external_photos`**: Lưu trữ URL hình ảnh của API ngoài.
- **`property_claim_requests`**: Yêu cầu xác nhận chủ sở hữu từ phía người dùng cho cơ sở đã được hệ thống nhập tự động.
# Bổ sung ERD: Unicode, tìm kiếm và tồn phòng (2026-07-15)

## Nguyên tắc migration

- SQL Server là nguồn dữ liệu chính; cột chứa tiếng Việt dùng `NVARCHAR`, mô tả dài dùng `NVARCHAR(MAX)`.
- Thứ tự bắt buộc: backup, chuyển kiểu/thêm cột nullable, backfill, reimport UTF-8, tạo index, kiểm tra, rồi mới siết `NOT NULL` khi an toàn.
- Không xóa/truncate `locations`. Import upsert theo `(location_type, source_code)`; `code` dùng namespace `P-{sourceCode}` và `W-{sourceCode}` để mã tỉnh không đụng mã phường.
- Địa giới chỉ có `PROVINCE -> WARD`; quận/huyện chỉ là `legacy_parent_name`.

## Mô hình mục tiêu

```mermaid
erDiagram
    LOCATIONS ||--o{ LOCATIONS : "province has wards"
    LOCATIONS ||--o{ HOTELS : "province or ward"
    HOTELS ||--o{ ROOM_TYPES : has
    HOTELS ||--o{ ROOMS : owns
    ROOM_TYPES ||--o{ ROOMS : classifies
    HOTELS ||--o{ RESERVATIONS : receives
    RESERVATIONS ||--|{ RESERVATION_DETAILS : contains
    ROOM_TYPES ||--o{ RESERVATION_DETAILS : reserves
    RESERVATION_DETAILS ||--o{ RESERVATION_ROOMS : assigns
    ROOMS ||--o{ RESERVATION_ROOMS : allocated
    RESERVATIONS ||--o{ RESERVATION_SERVICE_ITEMS : consumes

    LOCATIONS {
        bigint id PK
        bigint parent_id FK
        varchar code UK
        varchar source_code
        nvarchar name_vi
        nvarchar name_en
        nvarchar normalized_name
        varchar location_type
        nvarchar full_path
        nvarchar legacy_parent_name
    }
    HOTELS {
        bigint id PK
        nvarchar name
        nvarchar name_vi
        nvarchar name_en
        nvarchar normalized_name
        nvarchar address
        nvarchar normalized_address
        bigint province_id
        bigint ward_id
        varchar approval_status
        varchar operation_status
    }
    ROOM_TYPES {
        bigint id PK
        bigint hotel_id FK
        varchar code
        nvarchar name_vi
        nvarchar description_vi
        varchar bed_type
        int bed_count
        int max_adults
        int max_children
        int max_guests
        decimal base_price
        decimal hourly_price
        varchar status
    }
    ROOMS {
        bigint id PK
        bigint hotel_id FK
        bigint room_type_id FK
        nvarchar room_number
        int floor
        varchar status
        varchar maintenance_status
        int max_guests
    }
    RESERVATION_DETAILS {
        bigint id PK
        bigint reservation_id FK
        bigint room_type_id FK
        int quantity
        int adults
        int children
        decimal unit_price
        decimal subtotal
    }
    RESERVATION_ROOMS {
        bigint id PK
        bigint reservation_detail_id FK
        bigint room_id FK
        datetime assigned_at
        datetime released_at
        varchar status
    }
```

## Ràng buộc và index

- Unique: `locations(location_type, source_code)`, `room_types(hotel_id, code)`, `rooms(hotel_id, room_number)`.
- `rooms.hotel_id` phải khớp hotel của `room_type_id`; số lượng phòng lấy từ `COUNT(rooms)` vật lý.
- Index: `locations(location_type,parent_id,status)`, `locations(normalized_name)`, `hotels(province_id,ward_id,approval_status,operation_status)`, `hotels(normalized_name)`, `hotels(normalized_address)` với độ dài indexable.

# Bổ sung ERD: dữ liệu demo toàn quốc và vận hành Owner (2026-07-15)

## Baseline và phạm vi migration

- Baseline SQL Server trước phase: 34 Province, 6.283 Ward, 11 Hotel, 33 RoomType, 69 Room và 0 AccountSubscription đang lưu.
- Không đổi tên bảng `hotels`; trong nghiệp vụ bảng này tiếp tục đóng vai trò Property.
- `STANDARD` tạo 3-5 cơ sở theo mỗi tỉnh, không được mô tả là bao phủ toàn bộ Ward. `FULL_COVERAGE` mới tạo tối thiểu một cơ sở theo Ward, chạy theo batch và bị chặn bởi `max-total-properties`.
- Bản ghi thật không được update hoặc delete bởi seeder. Seeder chỉ upsert bản ghi có `is_demo=1`, `data_source='DEMO'` và `seed_key` do hệ thống tạo.

```mermaid
erDiagram
    USERS ||--o{ USER_PROPERTIES : manages
    HOTELS ||--o{ USER_PROPERTIES : assigned_to
    USERS ||--o{ ACCOUNT_SUBSCRIPTIONS : owns
    SUBSCRIPTION_PLANS ||--o{ ACCOUNT_SUBSCRIPTIONS : activates
    SUBSCRIPTION_PLANS ||--o{ PLAN_FEATURES : limits
    HOTELS ||--o{ PROPERTY_IMAGES : presents
    HOTELS ||--o{ ROOM_TYPES : defines
    ROOM_TYPES ||--o{ ROOM_TYPE_IMAGES : presents
    ROOM_TYPES ||--o{ ROOMS : contains
    RESERVATION_DETAILS ||--o{ RESERVATION_ROOMS : assigns
    ROOMS ||--o{ RESERVATION_ROOMS : allocated
    RESERVATIONS ||--o{ RESERVATION_SERVICES : consumes
    RESERVATIONS ||--o| INVOICES : invoices
    ROOMS ||--o{ HOUSEKEEPING_TASKS : requires
    DEMO_SEED_PROGRESS }o--|| LOCATIONS : tracks

    HOTELS {
        bit is_demo
        nvarchar data_source
        nvarchar seed_key UK
        nvarchar normalized_name
        nvarchar normalized_address
    }
    PROPERTY_IMAGES {
        bigint hotel_id FK
        nvarchar image_url
        nvarchar alt_text_vi
        nvarchar alt_text_en
        bit is_primary
        int sort_order
        bit is_demo
    }
    ROOM_TYPES {
        bigint hotel_id FK
        nvarchar normalized_name
        decimal area
        bit is_demo
    }
    ROOM_TYPE_IMAGES {
        bigint room_type_id FK
        nvarchar image_url
        bit is_primary
        int sort_order
        nvarchar alt_text_vi
        bit is_demo
    }
    ROOMS {
        bigint hotel_id FK
        bigint room_type_id FK
        nvarchar room_number
        varchar status
        varchar housekeeping_status
        varchar maintenance_status
        bit is_demo
    }
    HOUSEKEEPING_TASKS {
        bigint room_id FK
        bigint reservation_id FK
        varchar status
        datetime assigned_at
        datetime completed_at
    }
    DEMO_SEED_PROGRESS {
        nvarchar seed_key PK
        varchar coverage_mode
        bigint location_id FK
        varchar status
        nvarchar error_message
    }
```

`Room.status` biểu diễn khả năng vận hành (`AVAILABLE`, `RESERVED`, `OCCUPIED`, `MAINTENANCE`, `OUT_OF_SERVICE`); `housekeeping_status` biểu diễn `CLEAN`, `DIRTY`, `CLEANING`; `maintenance_status` được giữ riêng. Check-out chuyển phòng sang `DIRTY`, tạo `housekeeping_tasks`; chỉ khi hoàn tất dọn phòng mới chuyển về `AVAILABLE/CLEAN`.
