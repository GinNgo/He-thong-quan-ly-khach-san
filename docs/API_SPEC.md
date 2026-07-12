# ĐẶC TẢ API (API SPECIFICATIONS)

## Hệ thống Quản lý Đa Cơ sở & Gói Dịch vụ

### 1. User Context & Auth
GET `/api/users/me`
- Trả về thông tin cá nhân, Roles, Permissions, Gói dịch vụ hiện tại (Subscription), Danh sách cơ sở đang quản lý.

### 2. Location (Public)
GET `/api/public/locations/provinces` - Lấy danh sách Tỉnh/Thành phố nổi bật hoặc toàn bộ.
GET `/api/public/locations/provinces/{provinceId}/wards` - Lấy danh sách Phường/Xã của Tỉnh.
GET `/api/public/locations/search?keyword=...&provinceId=...` - Autocomplete địa điểm.

### 3. Property / Hotel (Public)
GET `/api/public/properties/search` - Tìm kiếm cơ sở lưu trú (phân trang, lọc theo keyword, province, ward, ngày, latitude, longitude, radiusKm, sortBy).
GET `/api/public/properties/{slug}` - Chi tiết cơ sở lưu trú.
GET `/api/public/properties/{propertyId}/room-types` - Lấy danh sách loại phòng và giá.
GET `/api/public/properties/{propertyId}/availability` - Lấy thông tin tồn phòng theo ngày.

### 4. Property Management (Owner / Admin)
GET `/api/properties` - Lấy danh sách cơ sở lưu trú (Của User nếu là Owner, Toàn bộ nếu là Admin).
GET `/api/properties/{id}` - Lấy chi tiết cơ sở.
POST `/api/properties` - Tạo mới cơ sở lưu trú.
PUT `/api/properties/{id}` - Cập nhật thông tin cơ sở.
DELETE `/api/properties/{id}` - Xóa (ẩn) cơ sở.
POST `/api/properties/{id}/submit` - Gửi duyệt cơ sở.
POST `/api/properties/{id}/approve` - Admin duyệt cơ sở.
POST `/api/properties/{id}/reject` - Admin từ chối cơ sở.
POST `/api/properties/{id}/activate` - Kích hoạt cơ sở.
POST `/api/properties/{id}/suspend` - Tạm ngưng cơ sở.

### 5. Property Accounts (Phân quyền Nhân viên)
GET `/api/properties/{id}/users` - Lấy danh sách nhân viên của cơ sở.
POST `/api/properties/{id}/users` - Gán/Tạo nhân viên cho cơ sở.
DELETE `/api/properties/{id}/users/{userId}` - Xóa nhân viên khỏi cơ sở.
PUT `/api/properties/{id}/users/{userId}/role` - Cập nhật vai trò nhân viên.

### 6. Subscriptions & Plans
GET `/api/subscription-plans` - Danh sách gói dịch vụ có sẵn.
GET `/api/subscriptions/me` - Lấy thông tin gói đang sử dụng.
POST `/api/subscriptions/register` - Đăng ký mua gói.
POST `/api/subscriptions/{id}/activate` - Kích hoạt gói.
POST `/api/subscriptions/{id}/renew` - Gia hạn gói.
POST `/api/subscriptions/{id}/upgrade` - Nâng cấp gói.
POST `/api/subscriptions/{id}/cancel` - Hủy gói.
POST `/api/subscriptions/{id}/revoke` - Admin thu hồi gói.
GET `/api/subscriptions/{id}/history` - Xem lịch sử thay đổi gói.
GET `/api/subscriptions/me/features` - Lấy danh sách tính năng đang mở.
GET `/api/subscriptions/me/usage` - Lấy thông tin mức độ sử dụng (Usage Limit).

### 7. File Upload (Property Images / Room Images)
POST `/api/uploads/image` - Upload ảnh (yêu cầu MultipartFile), trả về URL file.
DELETE `/api/uploads/image` - Xóa ảnh.

---

## Các Module Kế Thừa (Được bổ sung property_id)

### ROOMS
GET `/api/rooms`
GET `/api/rooms/{id}`
POST `/api/rooms`
PUT `/api/rooms/{id}`
DELETE `/api/rooms/{id}`

### ROOM TYPES
GET `/api/room-types`
POST `/api/room-types`
PUT `/api/room-types/{id}`
DELETE `/api/room-types/{id}`

### RESERVATIONS
GET `/api/reservations`
GET `/api/reservations/{id}`
POST `/api/reservations`
PUT `/api/reservations/{id}`
POST `/api/reservations/{id}/cancel`
POST `/api/reservations/{id}/checkin`
POST `/api/reservations/{id}/checkout`

### SERVICES
GET `/api/services`
POST `/api/services`
PUT `/api/services/{id}`
DELETE `/api/services/{id}`

### INVOICES
GET `/api/invoices`
GET `/api/invoices/{id}`
POST `/api/invoices/generate`

## 7. PROPERTY IMPORT & CLAIM API

### 7.1. Property Import Management

#### 7.1.1. Search and Stage Properties (Admin)
- **Endpoint**: /api/admin/property-imports/search
- **Method**: POST
- **Role**: SUPER_ADMIN
- **Permission**: PROPERTY_IMPORT:CREATE
- **Request Body**:
`json
{
  "provider": "NOMINATIM",
  "provinceId": 1,
  "wardId": null,
  "propertyTypes": ["HOTEL", "HOMESTAY", "RESORT"],
  "radiusKm": 20,
  "maxResults": 100
}
`
- **Response** (200 OK):
`json
{
  "batchId": 10,
  "status": "PREVIEW_READY",
  "totalFound": 90,
  "totalNew": 70,
  "totalDuplicate": 20
}
`

#### 7.1.2. Get Batch Items
- **Endpoint**: /api/admin/property-imports/{batchId}/items
- **Method**: GET
- **Role**: SUPER_ADMIN

#### 7.1.3. Import Valid Items
- **Endpoint**: /api/admin/property-imports/{batchId}/import
- **Method**: POST
- **Role**: SUPER_ADMIN
- **Response** (200 OK):
`json
{
  "message": "Imported 70 properties successfully."
}
`

### 7.2. Property Claim

#### 7.2.1. Request Claim (User)
- **Endpoint**: /api/properties/{propertyId}/claim
- **Method**: POST
- **Role**: USER
- **Request Body**:
`json
{
  "verificationMethod": "BUSINESS_LICENSE",
  "verificationData": "URL to document or text note"
}
`

#### 7.2.2. Get Claim Requests (Admin)
- **Endpoint**: /api/admin/property-claims
- **Method**: GET
- **Role**: SUPER_ADMIN

#### 7.2.3. Approve Claim
- **Endpoint**: /api/admin/property-claims/{id}/approve
- **Method**: POST
- **Role**: SUPER_ADMIN
- **Response** (200 OK): Grants the user OWNER role for the property.
