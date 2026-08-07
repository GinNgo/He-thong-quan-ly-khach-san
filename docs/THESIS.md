# CHƯƠNG 1
# TỔNG QUAN ĐỀ TÀI

## 1.1. LÝ DO CHỌN ĐỀ TÀI

Ngành du lịch và lưu trú ngày càng phụ thuộc vào khả năng cung cấp thông tin nhanh, quản lý tồn phòng chính xác và phục vụ khách hàng trên nhiều thiết bị. Tuy nhiên, nhiều cơ sở lưu trú quy mô vừa và nhỏ vẫn sử dụng bảng tính, sổ sách hoặc nhiều phần mềm rời rạc. Cách vận hành này dễ dẫn đến đặt phòng vượt mức, sai lệch trạng thái phòng, khó kiểm soát doanh thu và thiếu dữ liệu hỗ trợ quyết định.

Từ nhu cầu trên, đề tài **“Xây dựng hệ thống quản lý khách sạn và đặt phòng trực tuyến LuxeStay”** được thực hiện nhằm xây dựng một nền tảng thống nhất cho ba nhóm hoạt động: khách hàng tìm kiếm và đặt chỗ; chủ cơ sở quản lý tài nguyên lưu trú; quản trị viên vận hành nền tảng và phân quyền người dùng.

LuxeStay được định hướng theo mô hình phần mềm dịch vụ. Một tài khoản chủ cơ sở có thể quản lý nhiều cơ sở, trong khi giới hạn sử dụng được xác định bởi gói đăng ký. Hệ thống đồng thời giải quyết những vấn đề đặc thù của dữ liệu Việt Nam như tìm kiếm có dấu và không dấu, mô hình địa giới hai cấp và lưu trữ Unicode.

## 1.2. MỤC TIÊU NGHIÊN CỨU

### 1.2.1. Mục tiêu tổng quát

Xây dựng ứng dụng web full-stack hỗ trợ quản lý khách sạn và đặt phòng trực tuyến, bảo đảm tính đúng đắn của nghiệp vụ tồn phòng, phân quyền, thanh toán và vận hành lưu trú.

### 1.2.2. Mục tiêu cụ thể

- Xây dựng REST API bằng Java 21 và Spring Boot 3.
- Xây dựng giao diện responsive bằng Angular 22.
- Xác thực bằng JWT và kiểm soát quyền độc lập tại backend.
- Quản lý vai trò, chức năng và Action Mask.
- Hỗ trợ tìm kiếm cơ sở theo tỉnh, phường/xã, tên và địa chỉ tiếng Việt.
- Quản lý cơ sở, loại phòng, phòng vật lý, dịch vụ và ảnh.
- Thực hiện quy trình đặt phòng, gán phòng, check-in, sử dụng dịch vụ, check-out và dọn phòng.
- Ghi nhận thanh toán, xử lý callback idempotent, hoàn tiền khi hủy và lập hóa đơn.
- Quản lý nhiều cơ sở và giới hạn chức năng theo gói đăng ký.
- Xây dựng dữ liệu demo có kiểm soát, không sửa dữ liệu cơ sở thật.
- Kiểm thử các nghiệp vụ quan trọng ở tầng đơn vị, tích hợp và đầu cuối.
- Duy trì ma trận truy vết từ tác nhân, route, API, service và dữ liệu tới bằng chứng kiểm thử và nội dung báo cáo.

## 1.3. ĐỐI TƯỢNG VÀ PHẠM VI NGHIÊN CỨU

Đối tượng nghiên cứu gồm quy trình tìm kiếm và đặt phòng, quản lý tài nguyên lưu trú, xác thực và phân quyền, thanh toán, hóa đơn, đăng ký gói dịch vụ và quản trị cơ sở.

Các tác nhân chính gồm:

- **Khách chưa đăng nhập:** tìm kiếm, xem chi tiết cơ sở và xem loại phòng.
- **Khách hàng:** đặt phòng, thanh toán, theo dõi và hủy booking, xem hóa đơn.
- **Chủ cơ sở:** quản lý cơ sở được gán và theo dõi giới hạn gói.
- **Quản lý, lễ tân và nhân viên:** quản lý booking, phòng và quy trình lưu trú trong phạm vi được cấp.
- **Quản trị viên hệ thống:** quản lý người dùng, vai trò, quyền, cơ sở, dữ liệu nhập và subscription.

Phạm vi hiện tại chưa bao gồm nhiều loại phòng trong cùng một booking, đánh giá thực từ khách hàng, yêu thích, đối soát tài chính chuyên biệt và quy trình nâng/hạ/gia hạn gói đầy đủ. Dịch vụ do nhân viên thêm trong thời gian lưu trú không đồng nghĩa với việc khách hàng đã có luồng chọn dịch vụ tại checkout.

Trong báo cáo, mỗi capability được phân loại COMPLETE, PARTIAL, MISSING, BLOCKED hoặc DEFERRED. COMPLETE chỉ được sử dụng khi có source/contract và bằng chứng kiểm thử hiện hành. Kết quả kiểm thử cũ được giữ làm hồ sơ HISTORICAL; route, mockup hoặc test file chưa chạy không đủ để khẳng định chức năng đã hoàn thành.

## 1.4. PHƯƠNG PHÁP THỰC HIỆN

Đề tài được thực hiện theo các bước:

1. Khảo sát quy trình nghiệp vụ và xác định tác nhân.
2. Phân tích yêu cầu chức năng, dữ liệu và bảo mật.
3. Thiết kế kiến trúc nhiều tầng, REST API, cơ sở dữ liệu và giao diện.
4. Cài đặt theo từng phân hệ có thể kiểm thử độc lập.
5. Áp dụng migration để quản lý thay đổi cơ sở dữ liệu.
6. Kiểm thử đơn vị, tích hợp, giao diện và luồng đầu cuối.
7. Đối chiếu kết quả kiểm thử với yêu cầu trước khi tổng hợp báo cáo.
8. Cập nhật ERD, UML và API trước nội dung luận văn khi source thay đổi, sau đó kiểm tra lại caption, screenshot, rubric và định dạng đầu ra.

## 1.5. KẾT CẤU BÁO CÁO

Báo cáo gồm năm chương. Chương 1 trình bày bối cảnh, mục tiêu và phạm vi. Chương 2 giới thiệu cơ sở lý thuyết và công nghệ. Chương 3 phân tích yêu cầu và thiết kế hệ thống. Chương 4 mô tả cài đặt, giao diện và kết quả kiểm thử. Chương 5 tổng kết kết quả, hạn chế và hướng phát triển.

---

# CHƯƠNG 2
# CƠ SỞ LÝ THUYẾT

## 2.1. KIẾN TRÚC ỨNG DỤNG WEB NHIỀU TẦNG

Hệ thống được tổ chức thành frontend, backend và tầng dữ liệu. Frontend Angular chịu trách nhiệm trình bày và tương tác [1]. Backend Spring Boot cung cấp REST API, xác thực, phân quyền và xử lý nghiệp vụ [8]. Tầng dữ liệu lưu trữ trạng thái bền vững và thực thi các ràng buộc toàn vẹn.

Cách phân chia này giúp giảm phụ thuộc giữa giao diện và nghiệp vụ, hỗ trợ kiểm thử từng tầng, đồng thời cho phép thay đổi cách trình bày mà không làm thay đổi quy tắc nghiệp vụ cốt lõi.

## 2.2. REST API VÀ DTO

REST tổ chức tài nguyên qua URL và sử dụng phương thức HTTP để biểu diễn thao tác. LuxeStay dùng DTO cho dữ liệu trao đổi nhằm tránh lộ cấu trúc thực thể, hạn chế JSON đệ quy và kiểm soát dữ liệu đầu vào, đầu ra.

Các mã trạng thái chính gồm:

- `200 OK`: yêu cầu thành công.
- `201 Created`: tạo tài nguyên thành công.
- `400 Bad Request`: dữ liệu đầu vào không hợp lệ.
- `401 Unauthorized`: chưa xác thực hoặc token không hợp lệ.
- `403 Forbidden`: không đủ quyền hoặc vượt giới hạn gói.
- `404 Not Found`: không tìm thấy tài nguyên.
- `409 Conflict`: xung đột tồn phòng hoặc trạng thái nghiệp vụ.

## 2.3. XÁC THỰC JWT VÀ PHÂN QUYỀN

JWT là chuỗi token có chữ ký, chứa thông tin nhận dạng và thời hạn. Sau khi đăng nhập, client gửi token trong tiêu đề `Authorization`. Backend xác minh chữ ký, thời hạn và dựng ngữ cảnh bảo mật cho từng yêu cầu.

Hệ thống kết hợp ba lớp kiểm soát:

1. **Role:** xác định nhóm người dùng.
2. **Permission và Action Mask:** xác định hành động VIEW, CREATE, UPDATE, DELETE, EXPORT hoặc APPROVE trên chức năng.
3. **Feature Gate:** giới hạn tài nguyên theo gói đăng ký.

Backend là nơi quyết định quyền cuối cùng. Route Guard phía Angular chỉ hỗ trợ trải nghiệm và không thay thế kiểm tra tại máy chủ. Các cơ chế filter chain và method authorization được triển khai theo mô hình của Spring Security [9].

## 2.4. QUẢN LÝ TỒN PHÒNG VÀ GIAO DỊCH

Tồn phòng được xác định theo loại phòng, khoảng ngày và số lượng đã giữ bởi các booking có hiệu lực. Khi tạo booking, backend phải kiểm tra lại giá, sức chứa và số phòng còn lại thay vì tin dữ liệu từ client.

Giao dịch cơ sở dữ liệu bảo đảm chuỗi thao tác được hoàn thành toàn bộ hoặc hoàn tác. Khóa bản ghi khi xử lý thanh toán, hủy booking và cập nhật tài nguyên giúp giảm nguy cơ hai yêu cầu đồng thời tạo dữ liệu không nhất quán.

## 2.5. IDEMPOTENCY TRONG THANH TOÁN

Một callback thanh toán có thể được cổng thanh toán gửi nhiều lần. Idempotency bảo đảm cùng một giao dịch chỉ tạo một kết quả nghiệp vụ. LuxeStay dùng mã giao dịch duy nhất và ràng buộc cơ sở dữ liệu để chống ghi nhận trùng.

Hoàn tiền được lưu bằng giao dịch âm có mã xác định từ giao dịch gốc. Cách lưu này giữ được lịch sử thay vì sửa hoặc xóa giao dịch đã thành công.

## 2.6. UNICODE VÀ TÌM KIẾM TIẾNG VIỆT

Dữ liệu tiếng Việt cần được lưu bằng kiểu Unicode. Hệ thống sử dụng các cột `NVARCHAR`, đọc dữ liệu nhập bằng UTF-8 và xử lý BOM. Giá trị tìm kiếm được chuẩn hóa để người dùng có thể nhập có dấu hoặc không dấu.

Mô hình địa giới gồm tỉnh/thành phố và phường/xã, không dùng quận/huyện. Cấu trúc này phù hợp tập dữ liệu hiện hành của dự án và giảm số bước chọn địa điểm.

## 2.7. CÔNG NGHỆ SỬ DỤNG

**Bảng 2.1. Công nghệ chính của hệ thống**

| Tầng | Công nghệ | Vai trò |
|---|---|---|
| Backend | Java 21, Spring Boot 3.2.5 | REST API và nghiệp vụ [5], [8] |
| Bảo mật | Spring Security, JWT | Xác thực và phân quyền [9] |
| Dữ liệu | Spring Data JPA, Hibernate | Ánh xạ và truy cập dữ liệu |
| Migration | Flyway | Quản lý thay đổi schema [4] |
| API | springdoc-openapi | Tài liệu và kiểm tra endpoint |
| Frontend | Angular 22, TypeScript 6 | Giao diện ứng dụng [1] |
| UI | PrimeNG 21, Tailwind CSS 3 | Thành phần và định dạng [7] |
| Biểu đồ | Chart.js 4 | Trực quan hóa số liệu [2] |
| Kiểm thử | JUnit, Mockito, Spring Test, Playwright | Kiểm thử tự động [6], [8] |
| Triển khai | Docker Compose | Khởi tạo các dịch vụ [3] |

Bảng 2.1 cho thấy hệ thống sử dụng các công nghệ phổ biến, có hệ sinh thái kiểm thử và hỗ trợ tốt cho kiến trúc web nhiều tầng.

---

# CHƯƠNG 3
# PHÂN TÍCH VÀ THIẾT KẾ HỆ THỐNG

## 3.1. PHÂN TÍCH TÁC NHÂN VÀ CHỨC NĂNG

**Bảng 3.1. Tác nhân và nhóm chức năng**

| Tác nhân | Nhóm chức năng |
|---|---|
| Khách chưa đăng nhập | Tìm kiếm, xem cơ sở, xem phòng |
| Khách hàng | Đặt phòng, thanh toán, hủy booking, xem lịch sử và hóa đơn |
| Chủ cơ sở | Quản lý cơ sở, loại phòng, phòng và giới hạn gói |
| Quản lý/Lễ tân | Quản lý booking, gán phòng, check-in, check-out |
| Nhân viên | Thực hiện chức năng được cấp trong phạm vi cơ sở |
| Quản trị viên | Người dùng, role, permission, cơ sở, import, claim và subscription |

Bảng 3.1 thể hiện chức năng được tách theo trách nhiệm. Quyền thực tế còn phụ thuộc Action Mask, phạm vi cơ sở và trạng thái subscription.

### 3.1.1. Use Case tổng quát

```mermaid
flowchart LR
    Guest[Khách] --> Search[Tìm kiếm cơ sở]
    Guest --> Detail[Xem chi tiết]
    Customer[Khách hàng] --> Book[Đặt phòng]
    Customer --> Pay[Thanh toán]
    Customer --> Cancel[Hủy booking]
    Staff[Quản lý / Lễ tân] --> Assign[Gán phòng]
    Staff --> Checkin[Check-in]
    Staff --> Checkout[Check-out]
    Owner[Chủ cơ sở] --> Inventory[Quản lý loại phòng và phòng]
    Admin[Quản trị viên] --> Access[Quản lý role và permission]
    Admin --> Platform[Quản trị nền tảng]
```

Hình 3.1. Sơ đồ Use Case tổng quát

Mục đích của Hình 3.1 là xác định ranh giới chức năng theo tác nhân. Khách hàng tương tác với luồng thương mại; nhân viên xử lý lưu trú; chủ cơ sở quản lý tài nguyên; quản trị viên kiểm soát nền tảng. Kết quả phân tích cho thấy mọi thao tác ghi dữ liệu cần được kiểm tra cả quyền và phạm vi tài nguyên.

## 3.2. KIẾN TRÚC TỔNG THỂ

```mermaid
flowchart TB
    Browser[Trình duyệt] --> Angular[Angular 22]
    Angular -->|HTTPS / JSON| Security[Spring Security và JWT]
    Security --> Controller[REST Controller]
    Controller --> Service[Service nghiệp vụ]
    Service --> Repository[Spring Data Repository]
    Repository --> DB[(SQL Server / H2 test)]
    Service --> Payment[Cổng thanh toán / Simulator]
    DB --> Flyway[Flyway Migration]
```

Hình 3.2. Kiến trúc tổng thể của hệ thống

Hình 3.2 mô tả đường đi của yêu cầu từ giao diện đến dữ liệu. Controller tiếp nhận và chuẩn hóa yêu cầu; Service thực thi nghiệp vụ; Repository truy cập dữ liệu. Spring Security chặn yêu cầu trước Controller. Flyway quản lý phiên bản schema. Kiến trúc này giúp quy tắc nghiệp vụ không phụ thuộc giao diện.

## 3.3. THIẾT KẾ XÁC THỰC VÀ PHÂN QUYỀN

### 3.3.1. Biểu đồ lớp phân quyền

```mermaid
classDiagram
    class User {
      +Long id
      +String username
      +String email
      +String status
    }
    class Role {
      +Long id
      +String code
      +String name
    }
    class AppModule {
      +Long id
      +String code
      +String name
    }
    class AppFunction {
      +Long id
      +String code
      +String url
    }
    class RolePermission {
      +Long id
      +Integer actionMask
    }
    class JwtTokenProvider {
      +generateToken(authentication) String
      +validateToken(token) boolean
    }

    User "*" -- "*" Role
    AppModule "1" --> "*" AppFunction
    Role "1" --> "*" RolePermission
    AppFunction "1" --> "*" RolePermission
```

Hình 3.3. Biểu đồ lớp phân hệ xác thực và phân quyền

Mục đích của Hình 3.3 là mô tả cấu trúc RBAC động. `RolePermission` liên kết vai trò với chức năng và lưu `actionMask`. Bit mask cho phép kết hợp nhiều hành động trong một giá trị. `JwtTokenProvider` chịu trách nhiệm phát hành và xác minh token. Thiết kế hỗ trợ thay đổi menu và quyền từ dữ liệu mà không phải mã hóa cứng toàn bộ trong giao diện.

### 3.3.2. Trình tự xác thực yêu cầu

```mermaid
sequenceDiagram
    actor User
    participant UI as Angular
    participant Auth as AuthController
    participant JWT as JwtTokenProvider
    participant API as Protected API

    User->>UI: Nhập thông tin đăng nhập
    UI->>Auth: POST /login
    Auth->>JWT: Tạo token
    JWT-->>Auth: JWT
    Auth-->>UI: Token và thông tin người dùng
    UI->>API: Authorization: Bearer token
    API->>JWT: Xác minh token
    JWT-->>API: Danh tính và quyền
    API-->>UI: Dữ liệu hoặc HTTP 403
```

Hình 3.4. Biểu đồ tuần tự xác thực và gọi API

Hình 3.4 cho thấy JWT được kiểm tra trên từng yêu cầu. Token hợp lệ chỉ chứng minh danh tính; endpoint vẫn phải kiểm tra role, permission và phạm vi cơ sở. Kết luận, bảo vệ route phía client không phải lớp bảo mật cuối cùng.

### 3.3.3. Chat hỗ trợ khách hàng trung tâm

Chat hỗ trợ được thiết kế là một hàng đợi CSKH ở cấp nền tảng, thuộc module `SYSTEM` với function `AI_CHAT`. Hệ thống không gán hội thoại cho một cơ sở khi chưa có quan hệ conversation-property-reservation đầy đủ. Customer message được lưu với `receiver_id = 0` để biểu diễn hàng đợi trung tâm; reply vẫn lưu ID thật của nhân viên và khách hàng.

Kết nối chat dùng endpoint SockJS/STOMP riêng. HTTP handshake chỉ phục vụ quá trình nâng cấp kết nối, còn STOMP `CONNECT` phải mang JWT. Backend lấy sender từ principal đã xác thực, kiểm tra `AI_CHAT:VIEW` khi nhân viên đọc hàng đợi và `AI_CHAT:CREATE` khi reply. Customer chỉ đọc lịch sử của chính mình và nhận tin qua `/user/queue/messages`; do đó ID trong local storage hoặc payload giao diện không phải nguồn quyết định quyền.

Thiết kế này giữ kiến trúc nhỏ nhất phù hợp với phạm vi hiện có, loại bỏ phụ thuộc `adminId = 1` và tránh tạo mô hình chat theo tenant khi chưa có quy tắc assignment. Nếu sau này cần trao đổi theo cơ sở hoặc booking, hệ thống phải bổ sung conversation aggregate, property ownership và routing rule trong một feature riêng.

## 3.4. THIẾT KẾ DỮ LIỆU NGHIỆP VỤ

```mermaid
erDiagram
    USERS ||--o{ USER_ROLES : has
    ROLES ||--o{ USER_ROLES : assigned
    ROLES ||--o{ ROLE_PERMISSIONS : grants
    APP_FUNCTIONS ||--o{ ROLE_PERMISSIONS : controls

    USERS ||--o{ USER_PROPERTIES : assigned
    HOTELS ||--o{ USER_PROPERTIES : scoped
    HOTELS ||--o{ ROOM_TYPES : contains
    ROOM_TYPES ||--o{ ROOMS : defines

    USERS ||--o{ RESERVATIONS : books
    HOTELS ||--o{ RESERVATIONS : receives
    RESERVATIONS ||--|{ RESERVATION_DETAILS : includes
    ROOM_TYPES ||--o{ RESERVATION_DETAILS : selected
    RESERVATION_DETAILS ||--o{ RESERVATION_ROOMS : assigns
    ROOMS ||--o{ RESERVATION_ROOMS : occupied
    RESERVATIONS ||--o{ PAYMENTS : paid
    RESERVATIONS ||--o| INVOICES : billed

    USERS ||--o{ ACCOUNT_SUBSCRIPTIONS : owns
    SUBSCRIPTION_PLANS ||--o{ ACCOUNT_SUBSCRIPTIONS : configures
```

Hình 3.5. Sơ đồ ERD rút gọn của hệ thống

Mục đích của Hình 3.5 là thể hiện các quan hệ dữ liệu quan trọng. `ReservationDetail` giữ loại phòng và số lượng khi đặt. `ReservationRoom` chỉ được tạo khi nhân viên gán phòng vật lý. Sự tách biệt này cho phép bán theo loại phòng trước khi biết số phòng cụ thể.

`UserProperty` giới hạn phạm vi cơ sở mà chủ sở hữu hoặc nhân viên được thao tác. `AccountSubscription` tách trạng thái gói khỏi trạng thái tài khoản và cơ sở. `Payment` giữ lịch sử giao dịch, kể cả hoàn tiền. Thiết kế bảo đảm dữ liệu vận hành không bị xóa khi subscription hết hạn.

Bộ sơ đồ nguồn đã xác minh gồm UML-01 đến UML-19 trong `docs/UML.md` và ERD-01 đến ERD-05 trong `docs/ERD.md`. UML-01 đến UML-03 bao phủ use case; UML-04 đến UML-07 bao phủ class; UML-08 đến UML-15 bao phủ sequence; UML-16 đến UML-19 bao phủ activity. ERD-01 đến ERD-04 là schema hiện hành, còn ERD-05 chỉ là mô hình mục tiêu `DEFERRED`.

**Bảng 3.2. Danh mục sơ đồ thiết kế được tham chiếu**

| Mã hình | Nội dung | Mục sử dụng |
|---|---|---|
| UML-01 đến UML-03 | Use Case tổng quát, import/claim, chat/notification | 3.1, 3.3, 3.9 |
| UML-04 đến UML-07 | Class auth/RBAC, property/inventory, booking/payment, nền tảng mở rộng | 3.3, 3.4, 3.8, 3.9 |
| UML-08 đến UML-15 | Sequence auth, search, booking, refund, stay, import/claim, chat, notification | 3.3, 3.5-3.9 |
| UML-16 đến UML-19 | Activity booking, refund, stay và import/claim | 3.5, 3.6, 3.9 |
| ERD-01 đến ERD-04 | Schema hiện hành theo JPA/migration | 3.4, 3.8, 3.9 |
| ERD-05 | Mô hình mục tiêu cho capability DEFERRED | 5.3 |

## 3.5. THIẾT KẾ QUY TRÌNH ĐẶT VÀ HỦY PHÒNG

### 3.5.1. Activity Diagram đặt phòng

```mermaid
flowchart TD
    A[Bắt đầu] --> B[Chọn địa điểm, ngày và số khách]
    B --> C[Tìm cơ sở còn phòng]
    C --> D[Chọn RoomType và số lượng]
    D --> E{Dữ liệu hợp lệ?}
    E -- Không --> F[Hiển thị lỗi]
    F --> D
    E -- Có --> G[Backend kiểm tra giá, sức chứa và tồn phòng]
    G --> H{Còn đủ phòng?}
    H -- Không --> I[HTTP 409 Conflict]
    H -- Có --> J[Tạo Reservation và ReservationDetail]
    J --> K[Chọn phương thức thanh toán]
    K --> L[Kết thúc]
```

Hình 3.6. Biểu đồ hoạt động đặt phòng

Hình 3.6 mô tả hai lớp kiểm tra. Frontend phản hồi sớm cho lỗi nhập liệu; backend kiểm tra lại dữ liệu tại biên tin cậy. HTTP 409 được dùng khi tài nguyên đã thay đổi giữa lúc tìm kiếm và xác nhận.

### 3.5.2. Trình tự hủy và hoàn tiền

```mermaid
sequenceDiagram
    actor Customer
    participant UI as Angular
    participant RC as ReservationController
    participant RS as ReservationService
    participant PS as PaymentService
    participant DB as Database

    Customer->>UI: Chọn hủy booking
    UI->>RC: POST /reservations/{id}/cancel
    RC->>RS: Hủy reservation của khách hiện tại
    RS->>DB: Khóa và kiểm tra reservation
    RS->>PS: Hoàn các payment thành công
    PS->>DB: Tạo giao dịch âm REFUND-{paymentId}
    RS->>DB: Hủy gán phòng và cập nhật trạng thái
    RS-->>RC: Reservation đã hủy
    RC-->>UI: Kết quả thành công
```

Hình 3.7. Biểu đồ tuần tự hủy booking và hoàn tiền

Mục đích của Hình 3.7 là bảo đảm hủy booking thuộc đúng khách hàng và hoàn tiền không bị lặp. Mã `REFUND-{paymentId}` cùng kiểm tra giao dịch tồn tại giúp thao tác hoàn tiền idempotent. Lịch sử tài chính được bảo toàn bằng bản ghi âm thay vì sửa giao dịch gốc.

## 3.6. THIẾT KẾ QUY TRÌNH VẬN HÀNH LƯU TRÚ

```mermaid
stateDiagram-v2
    [*] --> RESERVED
    RESERVED --> ASSIGNED: Gán đủ phòng đúng loại
    ASSIGNED --> OCCUPIED: Check-in
    OCCUPIED --> DIRTY: Check-out
    DIRTY --> AVAILABLE: Hoàn tất housekeeping
    RESERVED --> CANCELLED: Khách hủy
    ASSIGNED --> CANCELLED: Khách hủy
```

Hình 3.8. Sơ đồ trạng thái phòng trong quy trình lưu trú

Hình 3.8 phân biệt booking với trạng thái phòng vật lý. Check-in chặn phòng sai loại, sai cơ sở, đang có khách hoặc bảo trì. Check-out tạo hóa đơn, chuyển phòng sang `DIRTY` và tạo tác vụ dọn phòng. Phòng chỉ trở lại `AVAILABLE` sau khi housekeeping hoàn tất.

## 3.7. THIẾT KẾ TÌM KIẾM VÀ DỮ LIỆU ĐỊA GIỚI

Tìm kiếm công khai sử dụng mô hình tỉnh và phường/xã. Autocomplete trả kết quả theo nhóm và hỗ trợ điều hướng bàn phím. Search State giữ địa điểm, ngày, số khách và số phòng khi chuyển từ trang chủ sang kết quả.

Hệ thống chuẩn hóa chuỗi tiếng Việt để so khớp có dấu và không dấu. Kết quả có thể lọc theo tỉnh, phường/xã, loại cơ sở, giá, hạng sao và điểm đánh giá; sắp xếp và phân trang được thực hiện phía server.

## 3.8. THIẾT KẾ MULTI-PROPERTY VÀ SUBSCRIPTION

`UserProperty` ánh xạ tài khoản với cơ sở và loại quan hệ. Mọi truy vấn quản trị theo cơ sở phải dùng Active Property Context hoặc phạm vi được gán. Chủ cơ sở không được truy cập tài nguyên của cơ sở khác bằng cách thay ID trên URL.

Feature Gate kiểm tra trạng thái gói và lượng tài nguyên đã sử dụng. Các trạng thái hiện có gồm `FREE`, `NO_PLAN`, `STANDARD`, `BUSINESS`, `LIFETIME` và `EXPIRED`. Khi vượt giới hạn, backend trả HTTP 403 cùng thông báo nâng cấp; dữ liệu hiện có không bị xóa.

Contract REST hiện hành chỉ gồm `GET /api/subscriptions/plans`, `GET /api/subscriptions/me` và `GET /api/subscriptions/me/features`. Các thao tác register, activate, renew, upgrade, downgrade, cancel, revoke và history chưa có controller mapping đầy đủ nên không được trình bày là đã hoàn tất.

## 3.9. THIẾT KẾ DỮ LIỆU DEMO VÀ IMPORT

Dữ liệu demo dùng địa giới đã nhập làm nguồn, được đánh dấu `is_demo`, `data_source=DEMO` và `seed_key` duy nhất. Seeder dùng cơ chế upsert, có thể chạy lại và không sửa cơ sở thật. Chế độ STANDARD tạo tập hữu hạn phục vụ local; không được hiểu là bao phủ toàn bộ phường/xã.

Quy trình import dữ liệu mở đưa kết quả vào vùng tạm, thực hiện chống trùng theo mã ngoài, tên và địa giới, điện thoại, website và khoảng cách. Quản trị viên xem xét trước khi nhập chính thức. Cơ sở nhập chưa mặc nhiên có phòng, giá hoặc chủ sở hữu; chủ cơ sở phải gửi yêu cầu claim và được duyệt.

Luồng import có controller/service và staging entity. Riêng claim hiện còn rủi ro: `PropertyClaimController` dùng requester/reviewer ID cố định thay vì lấy từ principal đã xác thực. Vì vậy phần claim chỉ được đánh giá `PARTIAL/BLOCKED` cho tới khi sửa identity mapping và có integration test.

## 3.10. THIẾT KẾ GIAO DIỆN

Giao diện public ưu tiên tìm kiếm, xem cơ sở và đặt phòng trên desktop lẫn mobile. Các trạng thái loading, empty, error và retry được thể hiện rõ. Ảnh local có fallback khi tài nguyên lỗi.

Khu vực quản trị dùng sidebar, bảng dữ liệu và form nhất quán. Menu được tạo từ dữ liệu quyền thay vì hiển thị cố định. Những thành phần dùng chung gồm bảng dữ liệu, thẻ thống kê và hộp thoại xác nhận nhằm giảm mã lặp và chuẩn hóa thao tác.

---

# CHƯƠNG 4
# CÀI ĐẶT VÀ KIỂM THỬ HỆ THỐNG

## 4.1. CẤU TRÚC CÀI ĐẶT

Backend được tổ chức theo các nhóm `controllers`, `services`, `repositories`, `entities`, `dtos` và `security`. Controller không chứa nghiệp vụ phức tạp; Service điều phối giao dịch và kiểm tra quy tắc; Repository đóng gói truy vấn dữ liệu.

Frontend tổ chức theo `core`, `shared` và `features`. Core chứa dịch vụ dùng toàn ứng dụng, interceptor và guard. Shared chứa thành phần trình bày dùng lại. Features chứa màn hình theo nghiệp vụ.

Flyway quản lý các thay đổi schema. Các migration hiện hành bao gồm chuẩn hóa Unicode, ràng buộc tồn phòng theo phạm vi, dữ liệu demo, chỉ mục tìm kiếm, dữ liệu role/menu và ràng buộc idempotency cho payment.

## 4.2. CÀI ĐẶT XÁC THỰC VÀ PHÂN QUYỀN

Endpoint đăng nhập phát hành JWT sau khi kiểm tra thông tin tài khoản. Bộ lọc bảo mật đọc token và tạo `Authentication`. Annotation `@Permission` kiểm tra chức năng và hành động. Các endpoint nhạy cảm còn dùng `@PreAuthorize` để giới hạn role.

Menu của người dùng được trả từ API `my-menu`. Frontend chỉ dựng route và mục điều hướng được cấp, trong khi backend tiếp tục kiểm tra độc lập. `SUPER_ADMIN` có quyền nền tảng; yêu cầu thiếu quyền nhận HTTP 403.

## 4.3. CÀI ĐẶT TÌM KIẾM VÀ ĐẶT PHÒNG

Trang chủ cung cấp autocomplete theo địa điểm và cơ sở. Trang kết quả nhận bộ lọc, sắp xếp và phân trang từ URL hoặc Search State. Giá được hiển thị theo số đêm và số lượng phòng.

Khi xác nhận booking, backend kiểm tra:

- Ngày nhận và trả phòng.
- Số người lớn, trẻ em và sức chứa.
- RoomType thuộc đúng cơ sở.
- Số lượng phòng còn lại.
- Giá hiện hành và tổng tiền.

Booking hiện hỗ trợ một RoomType với `quantity > 1`. Nếu không đủ tồn phòng, API trả HTTP 409.

## 4.4. CÀI ĐẶT THANH TOÁN, HỦY VÀ HOÀN TIỀN

Hệ thống hỗ trợ tạo payment, URL VNPay, callback VNPay và callback simulator. Callback chỉ ghi nhận thành công khi mã giao dịch chưa tồn tại. Migration `V10__payment_idempotency_constraint.sql` bổ sung ràng buộc dữ liệu chống giao dịch trùng.

Khách hàng hủy booking qua endpoint chuyên biệt. Service kiểm tra quyền sở hữu, trạng thái được phép hủy, khóa reservation và tạo giao dịch hoàn tiền âm cho các payment thành công. Việc gọi lại không tạo thêm refund cho cùng payment.

## 4.5. CÀI ĐẶT VẬN HÀNH LƯU TRÚ

Nhân viên có thể xem phòng còn trống, gán nhiều phòng vật lý và thực hiện check-in. Backend từ chối phòng không thuộc cơ sở, sai RoomType, `OCCUPIED` hoặc `MAINTENANCE`.

Dịch vụ phát sinh được thêm trong thời gian lưu trú và lưu snapshot đơn giá. Check-out tổng hợp chi phí, tạo hóa đơn, cập nhật phòng thành `DIRTY` và tạo housekeeping task. Khi tác vụ hoàn tất, phòng chuyển thành `AVAILABLE/CLEAN`.

## 4.6. CÀI ĐẶT MULTI-PROPERTY VÀ FEATURE GATE

Active Property Context xác định cơ sở đang được quản lý. Repository và Service lọc dữ liệu theo các quan hệ trong `user_properties`. Feature Gate kiểm tra `AccountSubscription` và giới hạn của gói trước thao tác tạo tài nguyên.

Thiết kế này ngăn hai lỗi độc lập: người dùng thao tác ngoài phạm vi cơ sở và người dùng tạo vượt hạn mức gói. Việc chỉ ẩn nút trên frontend không được xem là kiểm soát hợp lệ.

`SubscriptionController` hiện chỉ cung cấp danh sách plan, subscription ACTIVE của người dùng và feature map. `SubscriptionFeatureService`, `FeatureGateIntegrationTest` và `SubscriptionControllerIntegrationTest` đã nằm trong run backend CURRENT; vòng đời billing đầy đủ vẫn là giới hạn.

Import cơ sở dùng `PropertyImportController` với các permission VIEW/CREATE/EXECUTE và staging batch/item. Claim đã có request/list/approve/reject API nhưng chưa lấy requester/reviewer từ principal, nên báo cáo không xem đây là ownership workflow hoàn chỉnh. Chat dùng `/ws-chat`, `AI_CHAT:VIEW/CREATE`; notification dùng `/ws`, personal queue và admin topic có `REPORT:VIEW`. Backend verification cho chat/notification là CURRENT, còn authenticated frontend E2E bị BLOCKED.

## 4.7. GIAO DIỆN ĐÃ CÀI ĐẶT

**Bảng 4.1. Nhóm màn hình chính**

| Nhóm màn hình | Chức năng | Vai trò |
|---|---|---|
| Trang chủ và kết quả | Tìm kiếm, lọc, sắp xếp | Công khai |
| Chi tiết cơ sở | Xem ảnh, RoomType, giá và tồn phòng | Công khai |
| Checkout | Xác nhận khách, số phòng và chi phí | Khách hàng |
| Payment Simulator | Mô phỏng callback và kết quả thanh toán | Khách hàng/Test |
| Hồ sơ cá nhân | Thông tin, mật khẩu, booking và hóa đơn | Khách hàng |
| Dashboard quản trị | Chỉ số và điều hướng nghiệp vụ | Nhân viên quản trị |
| Role và Permission | Role, Action Mask và menu | Quản trị viên |
| RoomType và Room | Quản lý tài nguyên lưu trú | Chủ cơ sở/Nhân viên |
| Reservation | Gán phòng, check-in, dịch vụ, check-out | Lễ tân/Quản lý |
| Import và Claim | Staging, chống trùng, duyệt claim; claim còn identity gap | Quản trị viên/Người dùng |
| Subscription | Xem plan, subscription ACTIVE và feature limit | Chủ cơ sở/Quản trị viên |
| Support Chat | Hàng đợi hội thoại trung tâm qua STOMP | Khách hàng/CSKH |
| Notification | Nhận personal/admin destination và đánh dấu đã đọc | Người dùng có quyền |

Bảng 4.1 tổng hợp màn hình đã có trong mã nguồn. Mỗi màn hình chỉ hiển thị chức năng phù hợp vai trò, nhưng quyền cuối cùng vẫn do backend quyết định. Dòng claim, chat và notification phải được đọc cùng trạng thái `PARTIAL/BLOCKED`, không phải cam kết E2E hoàn tất.

Ảnh minh họa hiện có trong `docs/screenshots/` gồm trang tìm kiếm desktop/mobile, quản lý role và quản lý phòng. Khi xuất báo cáo Word hoặc PDF, ảnh phải được đặt dưới phần mô tả liên quan, có chú thích “Hình 4.x” và được tham chiếu trong nội dung.

### 4.7.1. Phạm vi xác minh khu vực Admin

Audit ngày 29/07/2026 đã inventory 29 route nghiệp vụ dưới `/admin`. Ma trận chi tiết tại `docs/audit/ADMIN_FUNCTIONAL_VERIFICATION_PLAN.md` ghi actor/role, permission, component, API, thao tác đọc, mutation, test và task hoàn thiện cho từng route. Đây là audit tĩnh kết hợp bằng chứng test hiện có, chưa phải kết luận E2E hiện hành cho toàn bộ Admin.

| Nhóm | Route/chức năng | Kết luận hiện tại |
|---|---|---|
| Core quản trị | dashboard, users, customers, room-types, rooms, services, reservations, invoices, roles, role-permissions, modules, chat | Cần chạy lại data-backed read/mutation/authorization; giữ `BLOCKED_RUNTIME` khi backend LuxeStay chưa cô lập |
| Property/import/claim | properties, property-imports, property-claims | Có source và một số unit; cần xác minh guard, tenant scope, import/approve/reject trên fixture |
| Partner overview | 10 route báo cáo chủ cơ sở, đăng ký, phê duyệt, nhân sự, phòng, subscription, hợp đồng | Component có chế độ đọc/approve nhưng 10 route chưa có guard tĩnh; cần policy/backend authorization review |
| Subscription | plans và purchase | Danh sách plan/subscription có API; `purchase()` hiện chỉ hiển thị thông báo chuyển hướng, nên trạng thái là `PARTIAL`, không mô tả thanh toán đã hoàn tất |
| Profile | profile admin | Có API đọc/sửa hồ sơ và đổi mật khẩu; chỉ kế thừa authGuard, cần chốt policy quyền |

Các test chỉ kiểm tra `body visible` hoặc route không crash được dùng như smoke evidence, không dùng làm bằng chứng `COMPLETE`. Cổng `8080` hiện thuộc dịch vụ Docker ngoài LuxeStay và credential `LUXESTAY_E2E_*` chưa có, nên kết quả Admin runtime phải giữ `BLOCKED` cho tới khi chạy môi trường cô lập.

## 4.8. CHIẾN LƯỢC KIỂM THỬ

Kiểm thử được chia thành:

- **Unit test:** kiểm tra Service với dependency giả lập.
- **Integration test:** khởi tạo Spring context, MockMvc và H2.
- **Frontend unit test:** kiểm tra component và service.
- **E2E test:** Playwright chạy các luồng public, customer, payment và admin.
- **Build verification:** biên dịch production để phát hiện lỗi kiểu và đóng gói.

Các trường hợp quan trọng gồm quyền truy cập, tìm kiếm Unicode, tồn phòng, booking nhiều phòng cùng loại, gán phòng đúng phạm vi, payment idempotency, hủy booking, hoàn tiền và giới hạn subscription.

Mỗi kết quả được gắn một trong ba nhãn: CURRENT khi vừa chạy trên worktree đã chốt; HISTORICAL khi lấy từ báo cáo có ngày trước đó; BLOCKED khi runner, môi trường hoặc workstream đang thay đổi khiến chưa thể xác minh. Với Admin, từng route còn có trạng thái `PASS/PARTIAL/FAIL/BLOCKED/NOT_APPLICABLE`; `PASS` bắt buộc có data load, thao tác chính và authorization evidence.

## 4.9. KẾT QUẢ KIỂM THỬ

**Bảng 4.2. Bằng chứng kiểm thử hiện có tại ngày 29/07/2026**

| Hạng mục | Kết quả đã ghi nhận | Nhãn |
|---|---:|---|
| Backend Maven test | 49/49 ngày 19/07/2026 | HISTORICAL |
| Backend Maven test | 60/60 ngày 20/07/2026 trong FEATURE_SUMMARY cũ | HISTORICAL |
| Backend Maven test | 86/86 ngày 27/07/2026 trong audit giao diện | HISTORICAL |
| Frontend unit | 20/20 ngày 15/07/2026 | HISTORICAL |
| Frontend unit | 32/32 ngày 27/07/2026 trong audit giao diện | HISTORICAL |
| Playwright public/customer/admin | Các mốc ngày 15/07/2026 | HISTORICAL |
| Angular production build | Các mốc cũ ghi pass | HISTORICAL |
| Backend Maven test | 122/122 ngày 28/07/2026 | HISTORICAL |
| Frontend unit | 66/66 ngày 28/07/2026 | HISTORICAL |
| Angular production build | Pass ngày 28/07/2026, còn cảnh báo | HISTORICAL |
| Backend Maven test | 123/123 ngày 29/07/2026 | CURRENT |
| Frontend unit | 73/73 ngày 29/07/2026 | CURRENT |
| Angular production build | Pass ngày 29/07/2026, còn cảnh báo | CURRENT |
| Backend notification/chat/security tests | Đã nằm trong 123 test CURRENT | CURRENT |
| Playwright E2E | Discovery 71 test/12 file; run timeout 184 giây, artifact lỗi redirect/search | BLOCKED |
| Playwright targeted smoke | 2 passed, 3 skipped, 0 failed trong 21,8 giây; thiếu `LUXESTAY_E2E_*` cho ca xác thực | CURRENT/PARTIAL |
| Admin shell smoke | 17 pass nhưng chủ yếu chỉ kiểm tra `body visible`; không chứng minh data/mutation/authorization | SMOKE_ONLY |
| Admin core data-backed E2E | 1 fail, 2 không chạy; `admin/admin` bị giữ tại `/admin/login` | BLOCKED |

Bảng 4.2 phân biệt các mốc lịch sử với lần chạy CURRENT ngày 29/07/2026. Backend đạt 123/123, frontend unit đạt 73/73 và production build thành công; các cảnh báo build/canvas được ghi riêng, không làm sai kết quả test. Playwright targeted smoke lịch sử đạt 2 passed, 3 skipped, 0 failed; các ca xác thực bị bỏ qua do chưa cấu hình `LUXESTAY_E2E_*`. Full suite vẫn BLOCKED sau 184 giây, với artifact lỗi tập trung ở redirect URL và Home Search; không dùng subset hoặc số liệu lịch sử làm kết luận cho toàn bộ E2E. Audit Admin ngày 29/07/2026 bổ sung ma trận 29 route nhưng chưa thay thế lần chạy E2E bị chặn.

## 4.10. ĐÁNH GIÁ KẾT QUẢ

Backend source và test inventory cho thấy hệ thống có các luồng cốt lõi từ tìm kiếm đến vận hành lưu trú; run CURRENT đạt 123/123 test với 0 failure, error hoặc skipped, gồm auth, payment, reservation, subscription, tenant isolation, Unicode/inventory, chat và notification security. Frontend unit đạt 73/73 trong 36 file và production build pass. Playwright full suite và Admin data-backed E2E vẫn BLOCKED do timeout, thiếu credential/fixture và lỗi redirect/search; 17 Admin shell smoke chỉ chứng minh route không crash ở mức tối thiểu.

Trước bản nộp cần chạy Playwright E2E trên backend LuxeStay cô lập; ghi lệnh, ngày, môi trường, role, fixture, pass/fail/error/skipped và log theo từng route Admin. Nếu runner còn lỗi, cổng bị chiếm, thiếu credential hoặc workstream notification/security chưa ổn định về release, kết quả E2E phải giữ BLOCKED và được nêu trong hạn chế.

---

# CHƯƠNG 5
# KẾT LUẬN VÀ HƯỚNG PHÁT TRIỂN

## 5.1. KẾT LUẬN

Đề tài đã xây dựng được hệ thống quản lý khách sạn và đặt phòng trực tuyến trên kiến trúc Angular và Spring Boot. Hệ thống hỗ trợ tìm kiếm tiếng Việt, đặt nhiều phòng cùng loại, quản lý tồn phòng, gán phòng vật lý, check-in, dịch vụ phát sinh, check-out, hóa đơn và housekeeping.

Phân hệ bảo mật kết hợp JWT, role, Action Mask, phạm vi cơ sở và Feature Gate. Mô hình này phù hợp nền tảng nhiều cơ sở vì quyền thao tác và giới hạn thương mại được kiểm tra độc lập.

Phân hệ thanh toán có source cho cơ chế chống callback trùng và hoàn tiền khi hủy. Ràng buộc cơ sở dữ liệu cùng kiểm tra tại Service được thiết kế để bảo vệ tính nhất quán khi yêu cầu lặp lại. Hiệu lực trên phiên bản nộp cuối phải được xác nhận bằng lần chạy test CURRENT, thay vì dựa vào các tổng số test lịch sử khác nhau.

Dữ liệu địa giới, tìm kiếm Unicode và seeder demo giúp hệ thống có dữ liệu trình diễn có thể lặp lại mà không sao chép từ OTA hoặc sửa cơ sở thật. Các báo cáo và mã nguồn cũng phân biệt rõ dữ liệu STANDARD với phạm vi bao phủ toàn bộ.

## 5.2. HẠN CHẾ

- Một booking mới hỗ trợ một RoomType với số lượng nhiều phòng.
- Khách chưa chọn dịch vụ bổ sung ngay tại checkout.
- Favorites và Customer Reviews chưa hoàn thiện.
- Điểm đánh giá chưa được tổng hợp từ quy trình review thật.
- Subscription chưa có đầy đủ lịch sử activate, renew, upgrade, downgrade và revoke.
- Đối soát thanh toán và báo cáo tài chính chuyên sâu chưa hoàn chỉnh.
- Giao diện Owner chưa bao phủ toàn bộ ảnh, nhân viên, dịch vụ và vận hành.
- 29 route Admin đã được inventory nhưng chưa thể tuyên bố toàn bộ hoạt động vì data-backed E2E/mutation/authorization bị BLOCKED bởi môi trường; 10 route partner-overview và `/admin/properties`/`profile` cần rà soát guard.
- `/admin/plans` hiện có luồng đọc plan/subscription nhưng thao tác purchase chỉ là thông báo mô phỏng; không coi là thanh toán hoàn chỉnh.
- Báo cáo doanh thu và công suất chưa đầy đủ theo cơ sở và khoảng ngày.
- Backend đã chạy CURRENT 123/123; frontend unit 73/73 và production build đã pass; Playwright hiện BLOCKED và cần xử lý redirect/fixture/search trước khi chốt.
- Notification và support chat có backend verification CURRENT nhưng vẫn PARTIAL cho tới khi frontend delivery/E2E và release workstream được chốt.
- Một số cảnh báo build và cấu hình JPA cần được xử lý trước triển khai production.

## 5.3. HƯỚNG PHÁT TRIỂN

1. Hỗ trợ nhiều RoomType trong cùng một booking bằng cấu trúc giỏ phòng.
2. Bổ sung dịch vụ tùy chọn tại checkout và chính sách giá theo ngày.
3. Xây dựng Favorites, Review, duyệt nội dung và điểm đánh giá xác thực.
4. Hoàn thiện vòng đời subscription và lịch sử thay đổi gói.
5. Bổ sung đối soát payment, webhook có chữ ký và nhật ký kiểm toán.
6. Hoàn thiện Owner Portal cho ảnh, nhân viên, dịch vụ và báo cáo.
7. Xây dựng báo cáo doanh thu, công suất, tỷ lệ hủy theo cơ sở và thời gian.
8. Bổ sung lịch sử trạng thái phòng và tiện nghi theo RoomType.
9. Tăng kiểm thử đồng thời cho tồn phòng, callback và hoàn tiền.
10. Chuẩn hóa pipeline CI để chạy backend, frontend, build và Playwright trên mỗi thay đổi.
11. Hoàn thiện ma trận Admin theo `docs/audit/ADMIN_FUNCTIONAL_VERIFICATION_PLAN.md`, ưu tiên cô lập môi trường E2E, guard/authorization và mutation test trước khi phát hành.

## 5.4. KẾT LUẬN CHUNG

Mã nguồn LuxeStay thể hiện nền tảng quản lý và đặt phòng với các nghiệp vụ cốt lõi, nhiều lớp kiểm soát quyền và khả năng quản lý nhiều cơ sở. Mức độ hoàn thành cuối cùng của từng capability được xác định bằng ma trận truy vết và test CURRENT. Các giới hạn còn lại được tách khỏi phần chức năng đã cài đặt và được trình bày như hướng phát triển.

## 5.5. HỒ SƠ KIỂM TRA GIAO DIỆN NGÀY 27/07/2026 (HISTORICAL)

Đợt audit ngày 27/07/2026 ghi nhận frontend unit 32/32, production build thành công và backend 86/86 test. Các số liệu này được giữ làm evidence HISTORICAL; cần chạy lại trước khi dùng làm kết luận cho bản nộp cuối.

Kết quả chính:

- Public search, property detail, single-RoomType selection, customer login, booking history và invoice hiển thị dữ liệu thật. UI hiện tự giới hạn lựa chọn một RoomType nên claim “mixed RoomType bị mất dữ liệu” của audit cũ không còn đúng với implementation hiện tại.
- Admin route chính tải được, nhưng nhận diện bị trộn LuxeStay/Aurora/Hotel System/Lumina; dynamic menu và quick search có URL không tồn tại.
- Generic partner administration và Management Portal có request/API nhưng view mắc kẹt loading do callback bất đồng bộ chưa kích hoạt cập nhật view trong Angular zoneless.
- Subscription billing vẫn chứa hành động mô phỏng bằng `confirm()`/`alert()` và chưa được coi là chức năng thanh toán hoàn thiện.
- Các form/controls được sửa phải có label liên kết, focus rõ, responsive 375/768/1024/1440 và state loading/error/retry.

Đợt cải tiến ưu tiên sửa state dùng chung, navigation canonical và brand shell trước khi mở rộng domain mới như reviews, customer add-on services hoặc multi-RoomType cart.

---

# TÀI LIỆU THAM KHẢO

1. Angular, “Angular Documentation,” https://angular.dev/.
2. Chart.js, “Chart.js Documentation,” https://www.chartjs.org/docs/.
3. Docker, “Docker Documentation,” https://docs.docker.com/.
4. Flyway, “Flyway Documentation,” https://documentation.red-gate.com/flyway/.
5. Oracle, “Java Platform, Standard Edition Documentation,” https://docs.oracle.com/en/java/javase/21/.
6. Playwright, “Playwright Documentation,” https://playwright.dev/docs/intro.
7. PrimeTek, “PrimeNG Documentation,” https://primeng.org/.
8. Spring, “Spring Boot Reference Documentation,” https://docs.spring.io/spring-boot/docs/3.2.5/reference/html/.
9. Spring, “Spring Security Reference,” https://docs.spring.io/spring-security/reference/.
### Authorization regression note (2026-08-03)

The receptionist role is intentionally limited to the admin dashboard,
customer records, room types, rooms, reservations, and invoices. Its frontend
contract now maps each screen to the matching backend permission instead of
reusing the staff-user or global-property endpoint. This keeps a missing
optional permission from causing a false redirect to the forbidden page.

## 5.6. Kế hoạch Home Discovery và Merchandising (PLANNED)

Ngày 03/08/2026, Feature 006 bổ sung thiết kế cho hai chức năng trang chủ lấy cảm hứng từ cấu trúc khám phá của các OTA nhưng không sao chép asset, thương hiệu hoặc giao diện của Agoda. Phạm vi này đang ở trạng thái `PLANNED`; chưa được ghi vào nhóm chức năng đã triển khai hoặc số liệu kiểm thử CURRENT.

Chức năng thứ nhất là danh sách chỗ nghỉ nổi bật theo tab địa điểm. Tab được lấy từ dữ liệu tỉnh/thành hiện hành và nguồn cung cơ sở đã duyệt, không hard-code một danh sách thành phố cố định. Khi khách đã chọn địa điểm trên Home, hệ thống ưu tiên context đó; nếu không có nguồn cung thì chọn địa điểm phổ biến hợp lệ. Kết quả là xếp hạng organic, chỉ dùng property `APPROVED/ACTIVE`, availability, review aggregate và giá VND do backend cung cấp. MVP dùng thứ tự xác định theo điểm đánh giá, số lượt đánh giá và ID ổn định; không tuyên bố là AI personalization khi chưa có mô hình, dữ liệu huấn luyện, consent và đánh giá chất lượng.

Chức năng thứ hai là carousel partner/editorial spotlight. Nội dung được tách thành `EDITORIAL` hoặc `SPONSORED`; placement tài trợ phải có nhãn tiếng Việt/tiếng Anh, lịch hiệu lực, trạng thái, quota, asset hợp lệ và phạm vi tenant khi gắn với cơ sở lưu trú. Nội dung hết hạn, bị tạm dừng, vượt quota hoặc trỏ tới property chưa duyệt không được xuất hiện. Organic ranking và sponsored placement nằm ở hai endpoint/section khác nhau để tránh quảng cáo ẩn và để mỗi section có thể loading, retry hoặc fail độc lập.

Thiết kế frontend tiếp tục dùng Angular standalone component, signal cho state cục bộ và HttpClient/RxJS tại service boundary. Trên mobile, tab và card có vùng scroll/snap nội bộ nhưng trang không được overflow ngang; control tối thiểu 44px, focus rõ, ảnh có kích thước ổn định và motion tuân thủ `prefers-reduced-motion`. Existing editorial slideshow vẫn được giữ vì có mục đích kể chuyện điểm đến khác với quảng cáo đối tác.

Thiết kế backend bổ sung `HomeRecommendationService`, `HomeSpotlightService` và ba public contract: recommendation destinations, organic recommendations và spotlights. Bảng `SPONSORED_PLACEMENTS` là mô hình mục tiêu, chưa phải schema hiện hành; migration/entity chỉ được thực hiện tại T111 sau khi policy T025-T027, T032 và T110 được duyệt. Các price gạch ngang, member price hoặc promotion badge tiếp tục bị cấm cho đến khi canonical promotion quote T028-T031 hoàn thành và được chứng minh nhất quán giữa Home, Search, Detail và Checkout.

Tiêu chí xác minh dự kiến gồm: chuyển tab và giữ location/date/guest context; không xuất hiện sponsored marker trong organic response; 100% sponsored card có disclosure; không render placement hết hạn/disabled/unapproved; không overflow ở 375/768/1024/1440px; không thiếu key VI/EN; không có lỗi console; CLS nhỏ hơn 0,1. Tài liệu thiết kế chi tiết nằm trong `specs/006-booking-marketplace-roadmap/home-discovery-merchandising/`.
