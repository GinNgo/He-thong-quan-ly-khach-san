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
# Bổ sung API: Unicode, autocomplete và inventory (2026-07-15)

## `GET /api/public/locations/search`

Query gồm `keyword`, `provinceId`, `page`, `size`. Response gộp `PROVINCE`, `WARD`, `PROPERTY`, `LANDMARK`; PROPERTY chỉ gồm hotel `APPROVED + ACTIVE`. `displayName` ưu tiên `name_vi`, `name`, `name_en`; không hard-code dữ liệu ở frontend.

```json
{
  "type": "PROPERTY",
  "id": 100,
  "displayName": "Khách sạn Ánh Dương",
  "secondaryText": "Phường Mỹ Tho, Tiền Giang",
  "address": "123 Lê Lợi",
  "provinceId": 1,
  "provinceName": "Tiền Giang",
  "wardId": 100,
  "wardName": "Phường Mỹ Tho",
  "propertyCount": null
}
```

## `GET /api/public/locations/provinces/popular`

Nhận `size` từ 1 đến 12. Chỉ trả tỉnh/thành phố đang có cơ sở `APPROVED + ACTIVE`, sắp xếp theo `propertyCount` thực tế; frontend không tạo số ngẫu nhiên hoặc danh sách fallback.

## `GET /api/public/properties/search`

Hỗ trợ `keyword`, `provinceId`, `wardId`, `checkInDate`, `checkOutDate`, `adultCount`, `childCount`, `roomCount`, `propertyTypes`, `pageNumber`, `pageSize`, `sortBy`. Keyword tìm normalized name/address, province/ward normalized name, code và slug. Join optional không được làm mất hotel. Khi có ngày/sức chứa, kết quả phải có RoomType đủ sức chứa và đủ tồn.

## `GET /api/public/properties/{propertyId}/availability`

Nhận ngày, adults, children, roomCount. Mỗi RoomType trả `totalActiveRooms`, `maintenanceRooms`, `overlappingReservedRooms`, `availableRooms`, `maxAdults`, `maxChildren`, `maxGuests`. Các trạng thái giải phóng tồn được xác định từ enum/business rule hiện tại.

## Booking, phòng và dịch vụ

- `POST /api/reservations` hoặc `/api/reservations/public/book`: nhận RoomType, quantity, adults, children, ngày ở; kiểm tra tồn bằng transaction/lock và trả `ReservationDTO`.
- `PUT /api/reservations/{id}/rooms`: gán đúng quantity, đúng hotel/RoomType và không trùng phòng đang ở.
- `PUT /api/reservations/{id}/status?status=CHECKED_IN|CHECKED_OUT|CANCELLED`: kiểm tra đủ phòng trước check-in và giải phóng phòng khi kết thúc.
- `POST /api/reservations/{id}/services`: kiểm tra dịch vụ thuộc hotel; lưu snapshot quantity, unitPrice, amount, usedAt.
- `/api/room-types` và `/api/rooms`: Owner được lọc theo property assignment/default property; Super Admin có thể chọn property đã kiểm tra quyền.

# API phase Demo Property và Owner Portal (2026-07-15)

## Seed configuration

Seeder không mở endpoint public và chỉ chạy khi profile là `development|demo|test`, đồng thời `app.demo-data.enabled=true` và `app.demo-data.nationwide-property-seed=true`. Các tham số gồm `coverage-mode`, `properties-per-province`, `properties-per-ward`, `max-total-properties`, `batch-size`. Password tài khoản demo chỉ đọc từ `DEMO_ACCOUNT_PASSWORD` và không xuất trong log/report.

## Admin

- `GET /api/admin/properties`: lọc/phân trang tất cả cơ sở, gồm nguồn demo/thật.
- `GET /api/admin/property-owners`: tài khoản owner, số cơ sở/phòng, plan, subscription/account/payment status.
- `GET /api/admin/property-registrations`: tài khoản đã đăng cơ sở.
- `GET /api/admin/property-owners/unsubscribed`: owner không có subscription hoạt động.
- `GET /api/admin/property-room-types`: RoomType toàn hệ thống có filter property.
- `GET /api/admin/property-rooms`: phòng vật lý toàn hệ thống có filter property/status.

Các thao tác kích hoạt, gia hạn, nâng/hạ cấp và thu hồi phải đi qua service tạo `subscription_history`; không update feature trực tiếp từ UI.

## Management

- `GET /api/management/context`: user, danh sách property được map, active subscription, usage/limit và `upgradeRequired`.
- `GET /api/management/properties`: chỉ các cơ sở trong `user_properties` của tài khoản.
- `POST /api/management/properties`: tạo DRAFT trong giới hạn plan.
- `GET|POST|PUT /api/management/room-types[/{id}]`: CRUD mềm, giá, sức chứa, giường và trạng thái theo activeProperty.
- `GET|POST|PUT /api/management/rooms[/{id}]`: CRUD phòng theo activeProperty.
- `POST /api/management/rooms/bulk`: tạo dải số phòng, kiểm tra trùng và `MAX_ROOMS`.

## Operation

- `GET /api/reservations/{id}/available-rooms`: phòng trống đúng property và RoomType đã đặt.
- `POST /api/reservations/{id}/assign-rooms`: gán đúng số lượng phòng vật lý.
- `POST /api/reservations/{id}/check-in`: yêu cầu đã gán đủ phòng, chuyển `OCCUPIED`.
- `POST /api/reservations/{id}/services`: lưu quantity, unit price snapshot, amount, usedAt, addedBy.
- `POST /api/reservations/{id}/check-out`: tạo/cập nhật invoice, ghi payment nếu có, chuyển reservation `CHECKED_OUT`, phòng `DIRTY`, tạo housekeeping task.
- `POST /api/management/housekeeping/{taskId}/complete`: hoàn tất dọn phòng, chuyển phòng `AVAILABLE/CLEAN` nếu không bảo trì.

Các endpoint cũ được giữ để tương thích. API mới gọi chung service nghiệp vụ, không nhân đôi logic.

# API phase LuxeStay Home Search (2026-07-15)

## `GET /api/public/search/suggestions`

Autocomplete mới dùng cho Home Search và Sticky Search. Endpoint cũ
`GET /api/public/locations/search` vẫn được giữ để tương thích với client hiện có.

Query:

- `keyword`: bắt buộc, sau khi trim phải có ít nhất 2 ký tự.
- `limit`: giới hạn Property, mặc định 10 và tối đa 10.
- `latitude`, `longitude`: tùy chọn; chỉ trả `distanceKm` khi có đủ tọa độ.

Response chia nhóm, không trộn loại kết quả:

```json
{
  "provinces": [{
    "type": "PROVINCE",
    "id": 1,
    "displayName": "Tiền Giang",
    "propertyCount": 5
  }],
  "wards": [{
    "type": "WARD",
    "id": 101,
    "displayName": "Phường Mỹ Tho, Tiền Giang",
    "provinceId": 1,
    "provinceName": "Tiền Giang",
    "propertyCount": 1
  }],
  "properties": [{
    "type": "PROPERTY",
    "id": 500,
    "slug": "luxestay-riverside-my-tho",
    "displayName": "LuxeStay Riverside Mỹ Tho",
    "propertyType": "HOTEL",
    "address": "21 Đường Vườn Xanh",
    "thumbnailUrl": "/assets/demo/hotel-demo-1.png",
    "reviewScore": 8.5
  }],
  "landmarks": []
}
```

Giới hạn: tối đa 5 Province, 8 Ward, 10 Property và 5 Landmark. Property chỉ
gồm cơ sở `APPROVED`, `ACTIVE`, chưa xóa mềm; profile production loại dữ liệu
demo trừ khi cấu hình cho phép. Search dùng các cột normalized đã backfill,
không normalize toàn bảng trong query.

## `GET /api/public/popular-destinations`

Query `limit` mặc định 8, tối đa 12. Trả Province đang hoạt động, sắp xếp theo
số Property `APPROVED + ACTIVE` thực tế. `propertyCount` không được hard-code.
`imageUrl` chỉ trỏ đến asset local được phép sử dụng và có thể null.

## Public demo visibility

Search chỉ trả `APPROVED + ACTIVE`, RoomType hoạt động và có phòng phù hợp. Production mặc định thêm điều kiện `is_demo=0`; chỉ hiển thị demo khi cấu hình cho phép. Search tiếp tục hỗ trợ tỉnh, Ward, tên/địa chỉ có dấu và không dấu, property type, giá, sức chứa, số lượng phòng và availability.
# Public/Customer context and media quality (2026-07-15)

- `GET /api/users/me` returns the authenticated user's roles, assigned properties,
  subscription fields, partner registration status, unread message count, and
  pending booking count. No guest data is returned without a token.
- `GET /api/invoices/my` returns only invoices whose reservation belongs to the
  authenticated user.
- `POST /api/partner/register` accepts a new anonymous account or the currently
  authenticated account. An existing email cannot be claimed anonymously.
- `GET /api/partner/registration-status` returns `NONE`, `PENDING`, or `APPROVED`
  for the authenticated account.
- Public property search returns per-property `mainImageUrl`, `thumbnailUrl`,
  `galleryUrls`, `imageCount`, `imageAltText`, availability, review data, and a
  pricing summary derived from the lowest available active room type.

# Search Result and booking flow (2026-07-15)

## `GET /api/public/properties/search`

The result page and Home Search use the same query-state serializer. Supported
server-side filters are `keyword`, `provinceId`, `wardId`, `checkInDate`,
`checkOutDate`, `adultCount`, `childCount`, `roomCount`, `minPrice`, `maxPrice`,
`propertyTypes`, `starRatings`, `minReviewScore`, `radiusKm`, `sortBy`,
`pageNumber`, and `pageSize`. Unsupported policy/amenity filters are not exposed
in the UI until their property relations exist in production data.

Each result contains real property media, review data, the lowest available
RoomType, total availability, and pricing calculated for the requested stay:

```json
{
  "availableRoomCount": 3,
  "lowestRoomType": { "id": 10, "name": "Phòng đôi", "maxGuests": 3 },
  "pricing": {
    "nightlyPrice": 550000,
    "discountedNightlyPrice": 550000,
    "numberOfNights": 2,
    "roomQuantity": 2,
    "subtotal": 2200000,
    "taxAmount": 0,
    "feeAmount": 0,
    "totalAmount": 2200000,
    "currency": "VND"
  }
}
```

Sorting is performed in SQL with `POPULAR`, `PRICE_ASC`, `PRICE_DESC`,
`RATING`, and `NEAREST` (the latter requires coordinates). Pagination is
one-based at the API boundary.

## Room selection and reservation

- `GET /api/room-types/public/hotel/{hotelId}` accepts `checkIn`, `checkOut`, and
  `guests`; it returns only RoomTypes with real availability for the requested
  period, including `availableRooms`, `nights`, `totalPrice`, and local image URLs.
- The current booking contract supports one RoomType with `quantity >= 1`.
  `POST /api/reservations/book` accepts `roomTypeId`, `quantity`, `adults`,
  `children`, dates, customer fields, special requests, and payment method.
- The backend locks the RoomType row, validates property state, capacity and
  overlapping reservations, recalculates the price, and returns HTTP 409 when
  the requested inventory is no longer available. Client totals are never used.
# Admin Roles and Inventory Contract (2026-07-15)

- `GET/POST /api/roles`, `GET/PUT/DELETE /api/roles/{id}` manage roles. Delete
  deactivates non-system roles; system roles are protected.
- `GET /api/role-permissions/tree/{roleId}` loads one action-mask row per
  function. `POST /api/role-permissions/{roleId}` atomically replaces masks.
- `GET/POST /api/room-types`, `GET/PUT/DELETE /api/room-types/{id}` manage room
  types within the caller's property scope. Delete changes status to `INACTIVE`.
- `GET/POST /api/rooms`, `GET/PUT/DELETE /api/rooms/{id}` manage physical rooms.
  Delete changes the room to `OUT_OF_SERVICE` and rejects occupied rooms.
- `POST /api/rooms/bulk` accepts `hotelId`, `roomTypeId`, `floor`, `fromNumber`,
  `toNumber`, optional `prefix` and initial `status`. The operation validates the
  property/type relationship and reports created and duplicate room numbers.

Action masks remain `VIEW=1`, `CREATE=2`, `UPDATE=4`, `DELETE=8`, `EXPORT=16`,
`APPROVE=32`.
