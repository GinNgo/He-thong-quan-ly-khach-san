# HỆ THỐNG BIỂU ĐỒ UML CHI TIẾT

Tài liệu này tập hợp toàn bộ các biểu đồ phân tích và thiết kế hệ thống chi tiết cho từng phân hệ nghiệp vụ, bao gồm: Use Case, Hoạt động (Activity), Tuần tự (Sequence) và Biểu đồ Lớp (Class). Toàn bộ các biểu đồ đều được đổ màu theo chuẩn nhận diện cấu trúc.

---

## 1. PHÂN HỆ XÁC THỰC VÀ PHÂN QUYỀN (AUTH & RBAC)

### 1.1. Biểu đồ Use Case
Mô tả các tính năng mà các tác nhân (Actors) có thể thực hiện liên quan đến tài khoản và quyền.

```plantuml
@startuml
left to right direction
skinparam packageStyle rectangle

actor "Khách hàng" as Guest
actor "Nhân viên" as Staff
actor "Quản trị viên" as Admin

rectangle "Phân hệ Xác thực (Auth)" {
  usecase "Đăng nhập" as UC1
  usecase "Đổi mật khẩu" as UC2
  usecase "Quản lý Vai trò" as UC3
  usecase "Phân quyền động" as UC4
}

Guest --> UC1
Staff --> UC1
Staff --> UC2
Admin --> UC1
Admin --> UC2
Admin --> UC3
Admin --> UC4
@enduml
```

### 1.2. Biểu đồ Hoạt động (Activity Diagram) - Đăng nhập
Mô tả luồng xử lý từ lúc người dùng nhập tài khoản đến khi hệ thống cấp phát Token.

```mermaid
stateDiagram-v2
    [*] --> NhapThongTin: Truy cập trang Đăng nhập
    NhapThongTin --> KiemTra: Bấm "Đăng nhập"
    
    state KiemTra {
        [*] --> ValidateDuLieu: Backend nhận Request
        ValidateDuLieu --> SoSanhDB: Check Username/Password
    }
    
    KiemTra --> SaiThongTin: Dữ liệu không khớp
    SaiThongTin --> NhapThongTin: Hiển thị lỗi (Sai mật khẩu)
    
    KiemTra --> TaoToken: Hợp lệ
    TaoToken --> LuuToken: Trình duyệt lưu JWT vào LocalStorage
    LuuToken --> ChuyenTrang: Điều hướng vào Dashboard
    ChuyenTrang --> [*]

    style KiemTra fill:#fdf2d0,stroke:#333
    style TaoToken fill:#d4e1f9,stroke:#333
    style SaiThongTin fill:#f9d0c4,stroke:#333
```

### 1.3. Biểu đồ Tuần tự (Sequence Diagram) - Luồng xác thực JWT

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
        UI-->>U: Chuyển hướng vào Admin Layout
    end
```

### 1.4. Biểu đồ Lớp (Class Diagram) - Security Phase

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

    class CustomUserDetailsService {
        +loadUserByUsername(String username) UserDetails
    }

    class JwtTokenProvider {
        +generateToken(Authentication auth) String
        +validateToken(String authToken) boolean
    }

    User "1" --> "*" Role : has
    Role "1" --> "*" Permission : has
    CustomUserDetailsService --> User : loads

    style User fill:#f9d0c4,stroke:#333,stroke-width:1px
    style Role fill:#d4e1f9,stroke:#333,stroke-width:1px
    style Permission fill:#ffe6cc,stroke:#333,stroke-width:1px
    style CustomUserDetailsService fill:#fdf2d0,stroke:#333,stroke-width:1px
    style JwtTokenProvider fill:#fdf2d0,stroke:#333,stroke-width:1px
```

---

## 2. PHÂN HỆ QUẢN LÝ PHÒNG & DỊCH VỤ (ROOM MANAGEMENT)

### 2.1. Biểu đồ Use Case

```plantuml
@startuml
left to right direction
skinparam packageStyle rectangle

actor "Lễ tân" as Staff
actor "Quản lý" as Manager

rectangle "Phân hệ Quản lý Phòng (Room Mgmt)" {
  usecase "Xem danh sách phòng" as UC1
  usecase "Cập nhật trạng thái dọn dẹp" as UC2
  usecase "Thêm/Sửa/Xóa Phòng" as UC3
  usecase "Quản lý Loại phòng & Giá" as UC4
}

Staff --> UC1
Staff --> UC2

Manager --> UC1
Manager --> UC3
Manager --> UC4
@enduml
```

### 2.2. Biểu đồ Hoạt động (Activity Diagram) - Cập nhật trạng thái phòng

```mermaid
stateDiagram-v2
    [*] --> XemDanhSach: Lễ tân mở màn hình Quản lý phòng
    XemDanhSach --> ChonPhong: Bấm vào phòng cần cập nhật
    ChonPhong --> DoiTrangThai: Chọn trạng thái "Đang dọn dẹp" / "Sẵn sàng"
    DoiTrangThai --> Luu: Bấm nút Lưu
    
    state Luu {
        [*] --> GoiAPI: PUT /api/rooms/{id}/status
        GoiAPI --> KiemTraLogic: Backend kiểm tra hợp lệ
    }
    
    Luu --> ThanhCong: Hợp lệ
    Luu --> ThatBai: Bị khóa hoặc đang có khách
    
    ThanhCong --> XemDanhSach: Báo thành công, load lại bảng
    ThatBai --> XemDanhSach: Báo lỗi "Không thể cập nhật"
    
    style Luu fill:#d4e1f9,stroke:#333
    style ThatBai fill:#f9d0c4,stroke:#333
    style ThanhCong fill:#e1f9d4,stroke:#333
```

### 2.3. Biểu đồ Lớp (Class Diagram) - Room & Service

```mermaid
classDiagram
    class RoomType {
        +Long id
        +String code
        +String nameVi
        +Integer maxGuest
        +BigDecimal basePrice
    }

    class Room {
        +Long id
        +String roomNumber
        +Integer floor
        +String status
    }

    class RoomImage {
        +Long id
        +String imageUrl
        +Boolean isPrimary
    }

    class HotelService {
        +Long id
        +String code
        +String nameVi
        +BigDecimal price
    }

    RoomType "1" --> "*" Room : includes
    Room "1" --> "*" RoomImage : has

    style RoomType fill:#f9d0c4,stroke:#333,stroke-width:1px
    style Room fill:#d4e1f9,stroke:#333,stroke-width:1px
    style RoomImage fill:#ffe6cc,stroke:#333,stroke-width:1px
    style HotelService fill:#fdf2d0,stroke:#333,stroke-width:1px
```

---

## 3. PHÂN HỆ ĐẶT PHÒNG & THANH TOÁN (RESERVATION & INVOICE)

### 3.1. Biểu đồ Use Case

```plantuml
@startuml
left to right direction
skinparam packageStyle rectangle

actor "Khách hàng" as Guest
actor "Lễ tân" as Staff

rectangle "Phân hệ Đặt phòng & Thanh toán" {
  usecase "Tạo Đặt phòng mới (Booking)" as UC1
  usecase "Hủy Đặt phòng" as UC2
  usecase "Nhận phòng (Check-in)" as UC3
  usecase "Trả phòng (Check-out)" as UC4
  usecase "Thanh toán & Xuất Hóa đơn" as UC5
}

Guest --> UC1
Guest --> UC2

Staff --> UC1
Staff --> UC2
Staff --> UC3
Staff --> UC4
Staff --> UC5
@enduml
```

### 3.2. Biểu đồ Tuần tự (Sequence Diagram) - Quy trình Check-in

```mermaid
sequenceDiagram
    participant Staff as Lễ tân
    participant UI as Frontend
    participant RC as ReservationController
    participant RS as ReservationService
    participant DB as Database

    Staff->>UI: Bấm nút [Check-in] cho mã đặt phòng RES-123
    UI->>RC: POST /api/reservations/RES-123/checkin
    RC->>RS: processCheckin(id)
    RS->>DB: Tìm Reservation ID
    DB-->>RS: Trả về dữ liệu
    
    alt Trạng thái không hợp lệ (Ví dụ: Đã hủy)
        RS-->>RC: Throw Exception
        RC-->>UI: 400 Bad Request
        UI-->>Staff: Báo lỗi "Booking không hợp lệ"
    else Hợp lệ
        RS->>RS: Cập nhật trạng thái Reservation = CHECKED_IN
        RS->>DB: Cập nhật trạng thái Room = OCCUPIED
        DB-->>RS: Lưu thành công
        RS-->>RC: Success
        RC-->>UI: 200 OK
        UI-->>Staff: Hiển thị thông báo "Check-in thành công"
        UI->>UI: Reload lại Data Table
    end
```

### 3.3. Biểu đồ Tuần tự (Sequence Diagram) - Xử lý Thanh toán & Xuất hóa đơn

```mermaid
sequenceDiagram
    participant Staff as Lễ tân
    participant UI as Frontend
    participant PC as PaymentController
    participant PS as PaymentService
    participant INV as InvoiceService
    participant DB as Database

    Staff->>UI: Nhập số tiền và bấm [Thanh toán]
    UI->>PC: POST /api/payments {amount, method, reservationId}
    PC->>PS: processPayment(dto)
    PS->>DB: Ghi nhận giao dịch vào bảng Payments
    DB-->>PS: Lưu thành công
    PS->>PS: Kiểm tra tổng tiền đã thanh toán >= tiền phòng
    
    alt Đã thanh toán đủ
        PS->>INV: generateInvoice(reservationId)
        INV->>DB: Truy xuất thông tin Phòng + Dịch vụ đã dùng
        DB-->>INV: Dữ liệu
        INV->>INV: Tính toán tổng tiền, VAT
        INV->>DB: Tạo bản ghi trong bảng Invoices
        INV-->>PS: Invoice ID
    end
    
    PS-->>PC: Kết quả thanh toán
    PC-->>UI: 200 OK
    UI-->>Staff: Báo thành công, hiển thị nút In hóa đơn (PDF)
```

### 3.4. Biểu đồ Lớp (Class Diagram) - Reservation & Payment

```mermaid
classDiagram
    class Reservation {
        +Long id
        +String bookingCode
        +LocalDateTime checkInExpected
        +LocalDateTime checkOutExpected
        +String status
        +BigDecimal totalAmount
    }

    class Payment {
        +Long id
        +BigDecimal amount
        +String paymentMethod
        +LocalDateTime paymentDate
        +String transactionRef
    }

    class Invoice {
        +Long id
        +String invoiceNumber
        +BigDecimal subTotal
        +BigDecimal taxAmount
        +BigDecimal grandTotal
        +LocalDateTime issueDate
    }

    class ReservationController {
        +checkin()
        +checkout()
    }

    class PaymentController {
        +processPayment()
    }

    Reservation "1" --> "*" Payment : has
    Reservation "1" --> "0..1" Invoice : generates
    ReservationController ..> Reservation : manages
    PaymentController ..> Payment : creates

    style Reservation fill:#f9d0c4,stroke:#333,stroke-width:1px
    style Payment fill:#d4e1f9,stroke:#333,stroke-width:1px
    style Invoice fill:#ffe6cc,stroke:#333,stroke-width:1px
    style ReservationController fill:#fdf2d0,stroke:#333,stroke-width:1px
    style PaymentController fill:#fdf2d0,stroke:#333,stroke-width:1px
```
