# Sơ đồ hệ thống và nghiệp vụ

Ngày tạo: 2026-08-05<br>
Định dạng: Mermaid. Source độc lập nằm trong `docs/hotel-report/diagrams/`.

> Chưa xuất PNG/SVG vì Mermaid CLI (`mmdc`) không được cài trong workspace tại thời điểm audit. Mỗi sơ đồ vẫn render trực tiếp trong Markdown; cột “File ảnh” được ghi là “chưa xuất”.

## D01 - Use Case tổng quan

**Mục đích:** thể hiện actor và nhóm chức năng thực tế của ba khu vực public/customer, property management và system administration.<br>
**Actor:** Guest, Customer, Staff, Owner/Partner, System Admin, Payment Provider.<br>
**Phạm vi:** toàn hệ thống ở mức capability; không thể hiện chi tiết endpoint.<br>
**Source:** `diagrams/D01-use-case-overview.mmd`.<br>
**File ảnh xuất:** chưa xuất.

```mermaid
flowchart LR
    Guest["Khách vãng lai"]
    Customer["Khách hàng"]
    Staff["Nhân viên khách sạn"]
    Owner["Chủ/đối tác khách sạn"]
    Admin["Quản trị hệ thống"]
    Provider["Cổng thanh toán"]
    subgraph Public["Public & Customer"]
      UC1["Đăng ký / đăng nhập"]
      UC2["Tìm kiếm khách sạn"]
      UC3["Xem chi tiết / phòng trống"]
      UC4["Đặt phòng / checkout"]
      UC5["Thanh toán"]
      UC6["Booking / hóa đơn / hoàn tiền"]
      UC7["Hồ sơ cá nhân"]
    end
    subgraph Property["Property Management"]
      UC8["Quản lý property / loại phòng / phòng"]
      UC9["Booking / check-in / check-out"]
      UC10["Housekeeping / dịch vụ"]
      UC11["Doanh thu / subscription / payment config"]
    end
    subgraph Platform["System Administration"]
      UC12["User / role / permission"]
      UC13["Phê duyệt property / plan"]
      UC14["Payment / doanh thu / audit / chat"]
    end
    Guest --> UC1
    Guest --> UC2
    Guest --> UC3
    Customer --> UC2
    Customer --> UC3
    Customer --> UC4
    Customer --> UC5
    Customer --> UC6
    Customer --> UC7
    Staff --> UC9
    Staff --> UC10
    Owner --> UC8
    Owner --> UC9
    Owner --> UC11
    Admin --> UC12
    Admin --> UC13
    Admin --> UC14
    UC5 <--> Provider
    UC6 <--> Provider
```

## D02 - Sequence đăng ký, đăng nhập và phiên

**Mục đích:** mô tả credential registration/login, refresh rotation và logout/revocation.<br>
**Actor:** Người dùng, Angular Auth UI, Auth API, token services, database, mail outbox/SMTP.<br>
**Phạm vi:** auth session; OAuth provider là nhánh ngoài chưa đưa vào để sơ đồ dễ đọc.<br>
**Source:** `diagrams/D02-auth-sequence.mmd`.<br>
**File ảnh xuất:** chưa xuất.

```mermaid
sequenceDiagram
    actor User as Người dùng
    participant UI as Angular Auth UI
    participant API as AuthController
    participant Auth as Auth/Token Services
    participant DB as SQL Server
    participant Mail as Mail Outbox/SMTP
    alt Đăng ký
      User->>UI: Nhập thông tin đăng ký
      UI->>API: POST /api/auth/register
      API->>Auth: Chuẩn hóa + validate + kiểm tra trùng
      Auth->>DB: Tạo user/role/token xác minh
      Auth->>Mail: Ghi yêu cầu gửi mail
      API-->>UI: Kết quả không lộ thông tin nhạy cảm
    else Đăng nhập
      User->>UI: Username/email + password
      UI->>API: POST /api/auth/login
      API->>Auth: Xác thực + account status gate
      Auth->>DB: Đọc user/role/permission
      Auth-->>API: Access token + refresh family
      API-->>UI: Access token + HttpOnly refresh cookie
    end
    UI->>API: Request bảo vệ + bearer token
    API->>Auth: Validate signature/expiry/revocation
    alt Access token hết hạn, refresh hợp lệ
      UI->>API: POST /api/auth/refresh
      API->>Auth: Rotate refresh token, chống replay
      Auth->>DB: Lock/update token family
      API-->>UI: Access token mới + cookie mới
    else Refresh replay/invalid
      API-->>UI: 401, thu hồi family
    end
    User->>UI: Logout
    UI->>API: POST /api/auth/logout
    API->>Auth: Revoke refresh family/access cutoff
    Auth->>DB: Cập nhật trạng thái thu hồi
    API-->>UI: Thành công
    UI->>UI: Xóa session và ngắt realtime
```

## D03 - Activity tìm kiếm, chi tiết và phòng trống

**Mục đích:** mô tả luồng public từ search criteria đến checkout/hold.<br>
**Actor:** Guest/Customer, Angular search/detail UI, public API, availability/pricing services.<br>
**Phạm vi:** search, hotel detail, room selection; chưa gồm thanh toán.<br>
**Source:** `diagrams/D03-search-availability-activity.mmd`.<br>
**File ảnh xuất:** chưa xuất.

```mermaid
flowchart TD
    A([Bắt đầu]) --> B[Nhập địa điểm, ngày, khách, số phòng]
    B --> C{Input hợp lệ?}
    C -- Không --> D[Hiển thị validation]
    D --> B
    C -- Có --> E[Gọi public search API]
    E --> F[Backend lọc property publish + địa điểm]
    F --> G[Kiểm tra capacity/availability/pricing]
    G --> H{Có kết quả?}
    H -- Không --> I[Empty state và gợi ý đổi tiêu chí]
    H -- Có --> J[Hiển thị danh sách]
    J --> K[Người dùng mở chi tiết khách sạn]
    K --> L[Tải ảnh, tiện ích, dịch vụ, loại phòng]
    L --> M[Chọn ngày/loại phòng/số lượng]
    M --> N[Backend tái kiểm tra availability]
    N --> O{Còn phòng?}
    O -- Không --> P[Thông báo hết phòng / chọn lại]
    P --> M
    O -- Có --> Q[Chuyển checkout / tạo hold]
    I --> R([Kết thúc])
    Q --> R
```

## D04 - Sequence booking và payment callback

**Mục đích:** thể hiện nguồn sự thật backend, hold/inventory, payment attempt và callback/IPN.<br>
**Actor:** Customer, checkout UI, booking service, payment orchestrator, database, provider.<br>
**Phạm vi:** tạo booking đến confirmed/failed/pending recovery.<br>
**Source:** `diagrams/D04-booking-payment-sequence.mmd`.<br>
**File ảnh xuất:** chưa xuất.

```mermaid
sequenceDiagram
    actor Customer as Khách hàng
    participant UI as Angular Checkout
    participant Booking as Booking API/Service
    participant DB as SQL Server
    participant Pay as Payment Orchestrator
    participant Provider as VNPay/MoMo/ZaloPay/Simulator
    Customer->>UI: Chọn phòng và xác nhận checkout
    UI->>Booking: Tạo booking/hold với dữ liệu khách
    Booking->>DB: Lock/kiểm tra inventory + lưu hold
    DB-->>Booking: Hold + price snapshot
    Booking-->>UI: Booking pending payment
    Customer->>UI: Chọn phương thức thanh toán
    UI->>Pay: Tạo payment attempt
    Pay->>DB: Lưu transaction/idempotency context
    Pay->>Provider: Create payment đã ký
    Provider-->>UI: Redirect/payment page
    Customer->>Provider: Xác nhận hoặc hủy
    Provider-->>UI: Redirect payment result
    Provider->>Pay: Callback/IPN server-to-server
    Pay->>Pay: Xác minh chữ ký, merchant, amount, replay
    alt Callback hợp lệ và thành công
      Pay->>DB: Cập nhật payment + booking atomically
      Pay-->>Provider: Acknowledge
      UI->>Booking: Query trạng thái authoritative
      Booking-->>UI: Booking confirmed
    else Callback lỗi/không chắc chắn
      Pay->>DB: Ghi failed/pending + audit
      Pay-->>Provider: Reject/ack theo contract
      Pay->>Provider: Recovery query theo lịch
    end
```

## D05 - Activity hủy booking và hoàn tiền

**Mục đích:** mô tả permission, cancellation policy, refund idempotency và recovery.<br>
**Actor:** Customer, staff/admin, cancellation/refund services, provider.<br>
**Phạm vi:** booking đã tồn tại; gồm nhánh không cần refund, success/fail/pending.<br>
**Source:** `diagrams/D05-cancellation-refund-activity.mmd`.<br>
**File ảnh xuất:** chưa xuất.

```mermaid
flowchart TD
    A([Yêu cầu hủy]) --> B[Load booking, payment và policy]
    B --> C{Actor có quyền?}
    C -- Không --> D[403 + audit]
    C -- Có --> E{Booking cho phép hủy?}
    E -- Không --> F[Trả lý do policy/status]
    E -- Có --> G[Tính phí hủy và số tiền có thể hoàn]
    G --> H{Có khoản đã thu cần hoàn?}
    H -- Không --> I[Cập nhật booking canceled]
    H -- Có --> J[Tạo refund idempotency key]
    J --> K[Gọi provider refund]
    K --> L{Kết quả chắc chắn?}
    L -- Thành công --> M[Cập nhật refund/payment/invoice/revenue]
    L -- Thất bại --> N[Ghi failed và lý do]
    L -- Pending/timeout --> O[Ghi pending và recovery query]
    O --> P{Recovery result}
    P -- Success --> M
    P -- Fail --> N
    I --> Q[Ghi audit và thông báo]
    M --> Q
    N --> Q
    D --> R([Kết thúc])
    F --> R
    Q --> R
```

## D06 - Booking/stay/check-in/check-out lifecycle

**Mục đích:** thể hiện các trạng thái chính và quan hệ với housekeeping/refund.<br>
**Actor:** Customer, reception/staff, scheduler, provider, housekeeping.<br>
**Phạm vi:** state machine logic ở mức nghiệp vụ; tên enum thực tế có thể chi tiết hơn theo entity.<br>
**Source:** `diagrams/D06-stay-lifecycle.mmd`.<br>
**File ảnh xuất:** chưa xuất.

```mermaid
stateDiagram-v2
    [*] --> Hold: Chọn phòng / giữ chỗ
    Hold --> PendingPayment: Tạo booking
    Hold --> Expired: Hết TTL
    PendingPayment --> Confirmed: Thanh toán/xác nhận hợp lệ
    PendingPayment --> Cancelled: Hủy hoặc thanh toán thất bại
    Confirmed --> Cancelled: Hủy theo policy
    Confirmed --> CheckedIn: Nhân viên check-in + gán phòng
    CheckedIn --> InHouse: Stay đang hoạt động
    InHouse --> CheckedOut: Tổng hợp charge + check-out
    CheckedOut --> Cleaning: Phòng cần dọn
    Cleaning --> Available: Housekeeping hoàn tất
    Cancelled --> RefundPending: Có tiền cần hoàn
    RefundPending --> Refunded: Provider xác nhận
    RefundPending --> RefundFailed: Provider từ chối/recovery thất bại
    Expired --> [*]
    Available --> [*]
    Refunded --> [*]
    RefundFailed --> [*]
```

## D07 - Activity quản lý property, loại phòng và phòng

**Mục đích:** thể hiện tenant/permission gate và chuỗi catalog đến public publish.<br>
**Actor:** Owner, property manager/staff, system admin khi hỗ trợ.<br>
**Phạm vi:** property, ảnh/tiện ích/dịch vụ, room type, room, pricing/inventory.<br>
**Source:** `diagrams/D07-property-room-management.mmd`.<br>
**File ảnh xuất:** chưa xuất.

```mermaid
flowchart TD
    A([Owner/Staff mở Management]) --> B[Backend xác định user + property context]
    B --> C{Có permission và tenant scope?}
    C -- Không --> D[403 / route denied]
    C -- Có --> E[Chọn property]
    E --> F[Quản lý thông tin, ảnh, tiện ích, dịch vụ]
    F --> G[Quản lý loại phòng và capacity]
    G --> H[Quản lý phòng vật lý và trạng thái]
    H --> I[Quản lý giá, tồn và policy theo ngày]
    I --> J{Validation/overlap hợp lệ?}
    J -- Không --> K[Hiển thị lỗi, không lưu một phần]
    K --> F
    J -- Có --> L[Lưu transaction + audit]
    L --> M{Property/catalog được publish?}
    M -- Không --> N[Chỉ thấy trong management]
    M -- Có --> O[Public search/detail nhận dữ liệu]
    D --> P([Kết thúc])
    N --> P
    O --> P
```

## D08 - Sequence role và permission

**Mục đích:** trace quyền từ cấu hình role đến menu/route và API/tenant gate.<br>
**Actor:** System Admin, Owner/Staff, Angular guard, Spring Security, RBAC service, database, audit log.<br>
**Phạm vi:** CRUD role/permission và authorization request.<br>
**Source:** `diagrams/D08-role-permission-sequence.mmd`.<br>
**File ảnh xuất:** chưa xuất.

```mermaid
sequenceDiagram
    actor Admin as System Admin
    actor User as Owner/Staff
    participant UI as Angular Menu/Guard
    participant API as Spring Security/API
    participant RBAC as Role/Permission Service
    participant DB as SQL Server
    participant Audit as Audit Log
    Admin->>API: Tạo/sửa role và permission
    API->>RBAC: Validate protected permission + scope
    RBAC->>DB: Lưu role/permission mapping
    RBAC->>Audit: Ghi actor/action/target
    API-->>Admin: Kết quả
    User->>UI: Đăng nhập / tải context
    UI->>API: GET profile/permissions
    API->>RBAC: Resolve role + property scope
    RBAC->>DB: Đọc mapping authoritative
    API-->>UI: Permission codes + tenant context
    UI->>UI: Hiển thị menu và guard route
    User->>API: Gọi endpoint nghiệp vụ
    API->>RBAC: Kiểm tra permission + tenant ownership
    alt Được phép
      API-->>User: 2xx dữ liệu trong scope
    else Bị từ chối
      API->>Audit: Ghi denial có correlation
      API-->>User: 403 ổn định
    end
```

## D09 - Sequence dashboard và reporting

**Mục đích:** mô tả filter/scope, aggregation, reconciliation và export.<br>
**Actor:** Admin/Owner/Manager, dashboard UI, reporting API/service, database, export client.<br>
**Phạm vi:** KPI, chart/table, revenue/payment/refund/invoice; không thay thế đặc tả công thức từng báo cáo.<br>
**Source:** `diagrams/D09-dashboard-reporting-sequence.mmd`.<br>
**File ảnh xuất:** chưa xuất.

```mermaid
sequenceDiagram
    actor Viewer as Admin/Owner/Manager
    participant UI as Dashboard/Report UI
    participant API as Reporting API
    participant Auth as Permission/Tenant Policy
    participant Query as Reporting Service
    participant DB as SQL Server
    participant Export as PDF/CSV Export
    Viewer->>UI: Chọn time range/property/chỉ số
    UI->>API: GET report với filter
    API->>Auth: Kiểm tra role + tenant scope
    Auth-->>API: Scope đã giới hạn
    API->>Query: Chuẩn hóa timezone/filter/status
    Query->>DB: Aggregate booking/payment/refund/invoice
    DB-->>Query: Kết quả tổng hợp
    Query->>Query: Reconcile và tính KPI
    Query-->>API: DTO có nguồn và timestamp
    API-->>UI: KPI/chart/table
    opt Người dùng xuất báo cáo
      UI->>Export: Tạo PDF/CSV từ dữ liệu đã lọc
      Export-->>Viewer: File tải xuống
    end
```

## Lưu ý kiểm chứng

- D04/D05 mô tả contract an toàn bắt buộc; provider sandbox E2E chưa được chứng minh nên không được hiểu là luồng đã PASS.
- D06 là mô hình nghiệp vụ tổng hợp từ booking/stay/housekeeping/refund; khi dùng làm đặc tả triển khai phải ánh xạ lại tên enum/entity chính xác.
- D08 phản ánh yêu cầu least privilege; bốn route management bị 403 là finding cần sửa/test, không phải hành vi được xác nhận là đúng.
