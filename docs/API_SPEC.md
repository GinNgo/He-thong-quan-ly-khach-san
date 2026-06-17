# ĐẶC TẢ API (API SPECIFICATIONS)

## Phase 1: Authentication & User Management

### 1.1. Đăng nhập (Login)
* **Endpoint**: `/api/auth/login`
* **Method**: `POST`
* **Description**: Xác thực người dùng và trả về JWT Token.
* **Request Body**:
  ```json
  {
    "username": "admin",
    "password": "password123"
  }
  ```
* **Response (200 OK)**:
  ```json
  {
    "accessToken": "eyJhbGciOiJIUzI1NiIsIn...",
    "tokenType": "Bearer",
    "username": "admin",
    "roles": ["ADMIN"]
  }
  ```
* **Response (401 Unauthorized)**:
  ```json
  {
    "message": "Sai tài khoản hoặc mật khẩu"
  }
  ```

### 1.2. Lấy danh sách người dùng (Get All Users)
* **Endpoint**: `/api/users`
* **Method**: `GET`
* **Security**: `Bearer Token` (Role: ADMIN)
* **Description**: Trả về danh sách toàn bộ người dùng (có phân trang).

### 1.3. Lấy thông tin người dùng hiện tại (Get Current User Profile)
* **Endpoint**: `/api/users/me`
* **Method**: `GET`
* **Security**: `Bearer Token`
* **Description**: Trả về thông tin cá nhân của người dùng đang đăng nhập.

---

## Các module khác (Sẽ được đặc tả chi tiết ở các Phase sau)

### AUTH
POST /api/auth/register
POST /api/auth/refresh-token
POST /api/auth/logout

### USERS
GET /api/users/{id}
POST /api/users
PUT /api/users/{id}
DELETE /api/users/{id}

### ROOMS
GET /api/rooms
GET /api/rooms/{id}
POST /api/rooms
PUT /api/rooms/{id}
DELETE /api/rooms/{id}

### ROOM TYPES
GET /api/room-types
POST /api/room-types
PUT /api/room-types/{id}
DELETE /api/room-types/{id}

### RESERVATIONS
GET /api/reservations
GET /api/reservations/{id}
POST /api/reservations
PUT /api/reservations/{id}
POST /api/reservations/{id}/cancel
POST /api/reservations/{id}/checkin
POST /api/reservations/{id}/checkout

### SERVICES
GET /api/services
POST /api/services
PUT /api/services/{id}
DELETE /api/services/{id}

### PAYMENTS
POST /api/payments
GET /api/payments/{id}

### INVOICES
GET /api/invoices
GET /api/invoices/{id}
GET /api/invoices/{id}/pdf
POST /api/invoices/generate

### AI
POST /api/ai/chat
POST /api/ai/recommend-room
POST /api/ai/revenue-analysis

### REPORTS
GET /api/reports/revenue
GET /api/reports/occupancy
GET /api/reports/reservations

### SUBSCRIPTIONS
GET /api/subscriptions
POST /api/subscriptions
POST /api/subscriptions/upgrade
POST /api/subscriptions/cancel
