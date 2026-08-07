# ĐẶC TẢ API (API SPECIFICATIONS)

## Home Discovery và Merchandising (PLANNED - Feature 006 T102)

Các endpoint dưới đây là contract mục tiêu cho hai section Home mới. Chúng chưa được coi là runtime API cho đến khi T103-T115 hoàn thành. Organic recommendation và partner spotlight dùng endpoint riêng để có loading/error/cache độc lập.

### `GET /api/public/home/recommendation-destinations`

Trả tối đa các địa điểm hiện hành có cơ sở lưu trú `APPROVED/ACTIVE`.

**Query parameters**

| Tên | Bắt buộc | Quy tắc |
|---|---|---|
| `limit` | Không | Mặc định `5`, khoảng `1..8` |
| `preferredProvinceId` | Không | ID tỉnh hiện hành từ Home search state |
| `locale` | Không | `vi` hoặc `en`; có thể lấy từ locale hiện tại |

**Response `200`**

```json
[
  {
    "id": 10146,
    "name": "An Giang",
    "displayName": "Tỉnh An Giang",
    "propertyCount": 18,
    "selectedByDefault": true
  }
]
```

**Business rules**

- Không trả địa điểm có `propertyCount=0`.
- Tối đa một item có `selectedByDefault=true`.
- Tỉnh hiện hành phải tổng hợp property đang lưu dưới các tỉnh legacy đã ánh xạ.

### `GET /api/public/home/recommendations`

Trả danh sách organic recommendation theo một địa điểm và search context.

**Query parameters**

| Tên | Bắt buộc | Quy tắc |
|---|---|---|
| `provinceId` | Có | ID tỉnh hiện hành hợp lệ |
| `checkInDate`, `checkOutDate` | Không | ISO local date; nếu có phải tạo khoảng hợp lệ |
| `stayType` | Không | Dùng enum search hiện hành |
| `adultCount`, `childCount`, `roomCount` | Không | Dùng giới hạn của Home search contract |
| `limit` | Không | Mặc định `8`, khoảng `1..12` |

**Response `200`**

```json
{
  "destination": {
    "id": 10146,
    "displayName": "Tỉnh An Giang"
  },
  "items": [
    {
      "propertyId": 501,
      "name": "LuxeStay Riverside",
      "propertyType": "HOTEL",
      "provinceId": 10146,
      "provinceName": "An Giang",
      "wardName": "Phú Quốc",
      "imageUrl": "/media/properties/501/home.webp",
      "imageAlt": "LuxeStay Riverside",
      "starRating": 4,
      "reviewScore": 8.7,
      "reviewCount": 126,
      "availableRoomCount": 3,
      "pricing": {
        "nightlyPrice": 500000,
        "currency": "VND"
      },
      "recommendationReason": "TOP_RATED",
      "sponsored": false
    }
  ],
  "totalAvailable": 18
}
```

**Business rules**

- Chỉ trả property `APPROVED/ACTIVE`; khi có ngày thì MVP loại property hết phòng.
- Thứ tự ban đầu: `reviewScore DESC`, `reviewCount DESC`, `propertyId DESC` để ổn định.
- `sponsored` luôn là `false`; paid placement không được chen ẩn vào organic endpoint.
- Price/availability do backend cung cấp. Không trả giá gạch ngang/member discount trước canonical quote T028-T031.
- Điều hướng canonical là `/hotel/:id` hoặc `/search` với location/date/guest context được giữ nguyên.

### `GET /api/public/home/spotlights`

Trả partner/editorial placements đủ điều kiện cho section Home.

**Query parameters**

| Tên | Bắt buộc | Quy tắc |
|---|---|---|
| `limit` | Không | Mặc định `6`, khoảng `1..10` |
| `locale` | Không | `vi` hoặc `en` |

**Response `200`**

```json
[
  {
    "id": 7001,
    "kind": "SPONSORED",
    "title": "Khám phá kỳ nghỉ bên biển",
    "description": "Ưu đãi do đối tác cung cấp",
    "imageUrl": "/media/placements/7001.webp",
    "imageAlt": "Khu nghỉ dưỡng nhìn ra biển",
    "disclosure": "Được tài trợ",
    "target": {
      "type": "PROPERTY",
      "propertyId": 501,
      "route": "/hotel/501"
    },
    "startsAt": "2026-08-01T00:00:00Z",
    "endsAt": "2026-08-31T23:59:59Z"
  }
]
```

**Business rules**

- Chỉ trả placement `ACTIVE`, đúng lịch, còn quota, asset hợp lệ và property mục tiêu được duyệt/đang hoạt động.
- `SPONSORED` bắt buộc có disclosure VI/EN; `EDITORIAL` dùng nhãn biên tập riêng.
- Target chỉ dùng route nội bộ và allowlist query parameters; không trả arbitrary external URL ở release đầu.
- Không có placement hợp lệ thì trả `[]`; frontend không thay bằng campaign giả.

### Error, authorization và cache

- Ba endpoint public chỉ đọc; validation error dùng error envelope hiện hành.
- Destination/recommendation dùng giới hạn request chặt và stable ordering; spotlight cache không vượt qua mốc schedule/quota gần nhất.
- API quản trị placement chưa được chốt trong T102. Role, permission, tenant management và mutation contract chỉ được công bố sau T110/OQ-005; mọi property placement phải lấy `hotel_id` hợp lệ từ principal/property access thay vì tin client.

## Chuẩn hóa lỗi xác thực và phân quyền

Các lỗi auth/authz thuộc P0-B trả JSON thống nhất, không trả trang HTML:

```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Unauthorized",
  "path": "/api/endpoint"
}
```

- `401 Unauthorized`: không có, sai hoặc hết hạn access token.
- `403 Forbidden`: danh tính hợp lệ nhưng thiếu role hoặc permission bitmask.
- `403 Forbidden`: tính năng bị subscription restriction. P0-B chỉ chuẩn hóa response; không đổi điều kiện gói hoặc luồng gia hạn.
- Tenant restriction khác subscription restriction. Tenant IDOR và chính sách `403`/`404` chống lộ tài nguyên thuộc P0-D, ngoài phạm vi P0-B.

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

### 5. Property Accounts (phạm vi source hiện hành)
Các màn hình partner/owner và `AdminPartnerController` cung cấp các endpoint quản trị dạng `/api/admin/property-staff`, `/api/admin/property-owners` và `/api/admin/property-registrations`. Các endpoint CRUD trực tiếp `/api/properties/{id}/users...` dưới đây chưa có mapping trong controller inventory hiện hành; không coi chúng là contract đã triển khai.

### 6. Subscriptions & Plans (source-verified 2026-07-28)
GET `/api/subscriptions/plans` - Danh sách gói đang có trong `SubscriptionPlanRepository`.
GET `/api/subscriptions/me` - Các `AccountSubscription` ACTIVE của người dùng hiện tại (yêu cầu đăng nhập).
GET `/api/subscriptions/me/features` - Mã tính năng và giới hạn được tính từ gói ACTIVE (yêu cầu đăng nhập).

`POST /register`, `activate`, `renew`, `upgrade`, `cancel`, `revoke`, `/history` và `/me/usage` chưa có mapping trong `SubscriptionController`; không được mô tả là endpoint đã triển khai. Các entity `SubscriptionOrder`, `SubscriptionPayment` và `SubscriptionHistory` là nguồn dữ liệu/roadmap cho vòng đời billing nhưng chưa tạo thành contract REST hoàn chỉnh.

### 7. File Upload (Property Images / Room Images)
POST `/api/uploads/image` - Upload ảnh (yêu cầu MultipartFile), trả về URL file.
GET `/api/public/uploads/{filename}` - Phục vụ ảnh public đã upload.
`DELETE /api/uploads/image` chưa có mapping trong controller hiện tại.

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
PUT `/api/reservations/{id}/status?status=CHECKED_IN|CHECKED_OUT|CANCELLED`
PUT `/api/reservations/{id}/rooms`
GET `/api/reservations/{id}/available-rooms`
POST `/api/reservations/{id}/assign-rooms`
POST `/api/reservations/{id}/check-in`
POST `/api/reservations/{id}/check-out`
POST `/api/reservations/{id}/services`

### SERVICES
GET `/api/services`
POST `/api/services`
PUT `/api/services/{id}`
DELETE `/api/services/{id}`

### INVOICES
GET `/api/invoices`
GET `/api/invoices/{id}`
POST `/api/invoices/reservation/{reservationId}`
GET `/api/invoices/reservation/{reservationId}`

## Payment and Billing Financial API (FR-001, FR-046)

This section is the source-verified REST contract for the two independent financial bounded contexts. It supersedes older planned billing notes elsewhere in this file. The implementation and traceability sources are [the financial contract](../specs/007-payment-billing-completion/contracts/financial-api-contract.md), [Property Commerce controllers](../backend/src/main/java/com/hotel/propertycommerce), [Platform Billing controllers](../backend/src/main/java/com/hotel/platformbilling), [stable error handling](../backend/src/main/java/com/hotel/controllers/GlobalExceptionHandler.java), and [FinancialErrorCode](../backend/src/main/java/com/hotel/paymentprovider/error/FinancialErrorCode.java).

### Context boundaries and shared rules

| Context | Money direction | Ledger ownership | Scope boundary |
|---|---|---|---|
| Property Commerce | Guest or staff pays an accommodation property | `PropertyFinancialTransaction` and finalized property invoices | Authenticated property access; every ledger read is tenant-filtered by `hotel_id` |
| Platform Billing | Property owner pays LuxeStay for a subscription | `PlatformFinancialTransaction`, subscription orders/contracts and platform entitlement | System billing scope; property ID identifies the target property but never changes ledger ownership |

The contexts must not share transaction IDs, merchant configuration, revenue totals, refund balances or entitlement effects. Both contexts use the following rules:

- Money is an integer-valued JSON number in `VND`; clients cannot supply authoritative settled totals, prices, durations or feature snapshots.
- Mutations require `Idempotency-Key`; equivalent replays return the original result, while a different payload with the same key returns `409 IDEMPOTENCY_KEY_REUSED`.
- `X-Correlation-ID` is optional on mutations and is returned in the error envelope when a request is rejected.
- Provider callbacks do not use customer JWT. They require provider signature, merchant, reference, currency and amount verification and are replay-safe.
- Production payment remains fail-closed. Missing or disabled configuration returns a truthful availability error and never falls back to a simulator.
- Finalized financial evidence is append-only. Report and export totals exclude non-settled attempts (`CREATED`, `PENDING`, `PENDING_VERIFICATION`, `PROCESSING`, `FAILED`, `CANCELLED`, `EXPIRED`).

### Shared financial states

Payment attempts use `CREATED`, `PENDING`, `PENDING_VERIFICATION`, `PROCESSING`, `SUCCESS`, `FAILED`, `CANCELLED`, `PARTIALLY_REFUNDED`, `REFUNDED` and `EXPIRED`. Property booking summaries additionally use `UNPAID`, `PARTIALLY_PAID`, `DEPOSIT_PAID`, `PAID`, `OVERPAID`, `PARTIALLY_REFUNDED` and `REFUNDED`. Refund requests use `REQUESTED`, `POLICY_BLOCKED`, `PENDING_APPROVAL`, `PENDING_PROVIDER`, `SUCCEEDED`, `FAILED` and `CANCELLED`. Platform subscription orders use `CREATED`, `PENDING_PAYMENT`, `PAID`, `APPLIED`, `FAILED`, `CANCELLED`, `EXPIRED` and `REFUNDED`.

### Stable error envelope

All financial failures return JSON with this shape; `fieldErrors` is an empty object when no field is implicated and `currentState` is omitted when not applicable:

```json
{
  "status": 409,
  "code": "INVALID_STATE_TRANSITION",
  "message": "The financial state transition is not allowed.",
  "correlationId": "corr-123",
  "fieldErrors": {},
  "retryable": false,
  "currentState": "SUCCESS",
  "path": "/api/payment-attempts/attempt-123/cancel"
}
```

The `code` values below are stable client identifiers. The HTTP status and retryability are part of the contract:

| HTTP | Code | Retryable |
|---:|---|:---:|
| 400 | `INVALID_AMOUNT`, `INVALID_CURRENCY`, `CALLBACK_MERCHANT_MISMATCH`, `CALLBACK_AMOUNT_MISMATCH`, `CALLBACK_REFERENCE_MISMATCH` | No |
| 401 | `CALLBACK_SIGNATURE_INVALID` | No |
| 403 | `TENANT_ACCESS_DENIED` | No |
| 404 | `RESOURCE_NOT_FOUND` | No |
| 409 | `INVALID_STATE_TRANSITION`, `OUTSTANDING_BALANCE`, `OVERPAYMENT_REQUIRES_RESOLUTION`, `IDEMPOTENCY_KEY_REUSED`, `ATTEMPT_EXPIRED`, `REFUND_EXCEEDS_BALANCE`, `POLICY_NOT_CONFIGURED`, `CONCURRENT_MODIFICATION` | Only `CONCURRENT_MODIFICATION` |
| 422 | `EXPORT_RECONCILIATION_MISMATCH` | No |
| 503 | `PAYMENT_ENVIRONMENT_DISABLED`, `PROVIDER_UNAVAILABLE` | Yes |
| 503 | `PRODUCTION_NOT_APPROVED` | No |

Authentication, authorization and request-shape failures use the same envelope with the stable generic codes `UNAUTHORIZED`, `ACCESS_DENIED`, `VALIDATION_FAILED`, `MALFORMED_REQUEST`, `MISSING_PARAMETER`, `MISSING_HEADER`, `INVALID_PARAMETER`, `INVALID_REQUEST`, `CONFLICT`, `DATA_CONFLICT`, `NOT_FOUND`, `METHOD_NOT_ALLOWED`, `UNSUPPORTED_MEDIA_TYPE` and `INTERNAL_ERROR`.

### Property Commerce API

Property Commerce is the guest-to-property money boundary. Property IDs in routes are navigation hints only; server-side property access resolves the authorized `hotel_id`.

#### Payment configuration and attempts

| Method | Path | Required permission or authentication | Contract |
|---|---|---|---|
| GET | `/api/management/properties/{propertyId}/payment-configuration` | `PROPERTY_PAYMENT_CONFIG:VIEW` | Return enabled methods, environment, deposit policy, expiry and masked readiness data; never return secrets |
| PUT | `/api/management/properties/{propertyId}/payment-configuration` | `PROPERTY_PAYMENT_CONFIG:UPDATE` | Validate property access, provider readiness, bank fields and environment gate; secrets are write-only references |
| POST | `/api/management/properties/{propertyId}/payment-configuration/validate` | `PROPERTY_PAYMENT_CONFIG:UPDATE` | Validate readiness without sending money; return blockers per method |
| GET | `/api/reservations/{reservationId}/financial-summary` | Reservation owner or authorized property role | Return server-derived gross charges, deposit, successful payments/refunds, remaining balance, `VND` and booking financial state |
| POST | `/api/reservations/{reservationId}/payment-attempts` | Reservation owner or authorized property role; `Idempotency-Key` | Request body selects `purpose` (`DEPOSIT`, `BALANCE`, `SERVICE`, `SURCHARGE`, `OTHER`) and method; amount is server-owned |
| GET | `/api/payment-attempts/{attemptId}` | Authorized resource owner | Return safe status, expiry, environment, expected amount, method, provider and masked receiver/transfer data |
| POST | `/api/payment-attempts/{attemptId}/cancel` | Authorized resource owner; `Idempotency-Key` | Cancel only an allowed non-terminal attempt; equivalent replay is safe |
| POST | `/api/management/payment-attempts/{attemptId}/confirm-manual` | `PROPERTY_PAYMENT_CONFIRM_MANUAL`; `Idempotency-Key` | Confirm authentic transfer with reason and evidence reference; body cannot select property scope or settled amount |

The create-attempt response includes `attemptId`, `reservationId`, `purpose`, `status`, `environment`, `expectedAmount`, `currency`, `expiresAt`, `method`, `provider`, masked receiver data, unique transfer content and `replayed`. QR and redirect fields are nullable for providers that do not use them.

#### Provider callback

| Method | Path | Authentication | Contract |
|---|---|---|---|
| POST | `/api/payment-providers/property/{provider}/callback` | Provider signature/merchant verification; no customer JWT | Verify provider identity, expected amount/currency/reference and replay identity; apply at most one property ledger effect |

The callback response contains `accepted`, `replayed`, `errorCode`, `attemptId`, `status` and `transactionId`. An equivalent replay acknowledges the prior effect without creating another transaction or audit mutation.

#### Charges, checkout, invoices and refunds

| Method | Path | Required permission or authentication | Contract |
|---|---|---|---|
| POST | `/api/management/reservations/{reservationId}/charges/services` | `RESERVATION_SERVICE:CREATE` | Add a positive-quantity, server-priced service snapshot |
| POST | `/api/management/reservations/{reservationId}/charges/surcharges` | `RESERVATION_SURCHARGE:CREATE` | Add a typed/reasoned surcharge; negative adjustments follow their dedicated policy |
| POST | `/api/management/reservations/{reservationId}/checkout-preview` | `CHECKOUT:VIEW` | Recompute the authoritative folio without mutation |
| POST | `/api/management/reservations/{reservationId}/checkout-override` | `RESERVATION_DEBT_OVERRIDE:APPROVE` | Authorize a debt/overpayment exception with an audit reason |
| POST | `/api/management/reservations/{reservationId}/checkout` | `CHECKOUT:CREATE` | Atomically finalize invoice, financial state, room and housekeeping state; client totals/payment references are rejected |
| GET | `/api/invoices/{invoiceId}` | Invoice owner or authorized property role | Return immutable invoice snapshot and allocations |
| GET | `/api/invoices/{invoiceId}/pdf` | Same as invoice view | Render the finalized snapshot only |
| POST | `/api/invoices/{invoiceId}/email` | Invoice view plus recipient policy | Queue/send finalized invoice and record notification evidence |
| POST | `/api/management/invoices/{invoiceId}/credit-notes` | `INVOICE_ADJUST` | Append an authorized post-finalization correction |
| GET | `/api/invoices/finalized/my` | Authenticated customer | Return only finalized invoices owned by the current customer |
| POST | `/api/property-payments/{transactionId}/refunds` | Authorized transaction owner; `Idempotency-Key` | Request full/partial refund against remaining refundable balance |
| POST | `/api/property-refunds/{refundId}/approve` | `PROPERTY_REFUND:APPROVE` | Approve according to refund policy |
| POST | `/api/property-refunds/{refundId}/attempts` | `PROPERTY_REFUND:APPROVE` | Create a provider refund attempt from server-side provider configuration |
| GET | `/api/property-refunds/{refundId}` | Authorized refund owner | Return refund state, amount, remaining balance and provider attempt status |
| POST | `/api/payment-providers/property/{provider}/refund-callback` | Provider signature/merchant verification; no customer JWT | Verify and apply one refund provider result; equivalent replay is idempotent |

Underpayment returns `409 OUTSTANDING_BALANCE`. Overpayment returns `409 OVERPAYMENT_REQUIRES_RESOLUTION` unless an approved override applies. Checkout returns the finalized invoice identity, financial summary and resulting operational states.

#### Property revenue reporting

| Method | Path | Required permission | Contract |
|---|---|---|---|
| GET | `/api/management/reports/property-revenue` | `REPORT:VIEW` | Filter by `from`, `to`, `basis`, property, provider, method, transaction type, room type and `zoneId`; return gross, refunds, credits, net, cash, invoiced, unpaid, held deposits, rows and reconciliation issues |
| GET | `/api/management/reports/property-revenue/export` | `REPORT:EXPORT` | Export the same normalized report as `CSV`, `XLSX` or `PDF`; include checksum and row-count headers |

#### Property response ownership

Property Commerce response data may contain reservation/invoice identifiers for the authorized property only. It must not contain platform plan prices, subscription entitlement state or another property's ledger rows.

### Platform Billing API

Platform Billing is the owner-to-LuxeStay subscription boundary. Its transaction and refund APIs never write Property Commerce ledger rows; entitlement activation occurs only from verified platform payment evidence.

#### Catalog, orders and entitlement

| Method | Path | Required permission or authentication | Contract |
|---|---|---|---|
| GET | `/api/platform/subscription-plans` | Authenticated owner/authorized representative | Return active plans and feature limits; no merchant secrets |
| POST | `/api/platform/subscription-orders` | `PLATFORM_BILLING:CREATE`; `Idempotency-Key` | Request identifies `targetHotelId` and `planId`; server snapshots price, duration and features |
| GET | `/api/platform/subscription-orders/{orderId}` | `PLATFORM_BILLING:VIEW` and order ownership | Return safe order, payment-attempt and application status |
| POST | `/api/platform/subscription-orders/{orderId}/payment-attempts` | `PLATFORM_BILLING:CREATE`; `Idempotency-Key` | Request selects provider/method; merchant configuration and amount are server-owned |
| POST | `/api/platform/subscription-orders/{orderId}/cancel` | `PLATFORM_BILLING:UPDATE` and order ownership | Cancel only an unpaid order |
| POST | `/api/platform/subscriptions/{targetHotelId}/renewal-orders` | `PLATFORM_BILLING:CREATE`; `Idempotency-Key` | Create a server-snapshotted renewal order |
| POST | `/api/platform/subscriptions/{targetHotelId}/upgrade-orders` | `PLATFORM_BILLING:CREATE`; `Idempotency-Key` | Validate target plan and approved upgrade policy before creating the order |
| POST | `/api/platform/subscriptions/{targetHotelId}/downgrade-orders` | `PLATFORM_BILLING:CREATE`; `Idempotency-Key` | Apply approved policy or return `409 POLICY_NOT_CONFIGURED` without mutation |
| GET | `/api/platform/subscriptions/{targetHotelId}/history` | `PLATFORM_BILLING:VIEW` | Return contract and entitlement transition evidence |
| GET | `/api/platform/subscriptions/{targetHotelId}/entitlement` | `PLATFORM_BILLING:VIEW` | Return the authoritative platform entitlement read model and limits |
| GET | `/api/platform/subscription-policies` | `PLATFORM_BILLING:VIEW` | Return configured downgrade/proration availability; this is a read-only policy check |

#### Platform callback and refunds

| Method | Path | Authentication or permission | Contract |
|---|---|---|---|
| POST | `/api/payment-providers/platform/{provider}/callback` | Provider signature/system merchant verification; no customer JWT | Apply one platform transaction and one eligible subscription effect; replay is acknowledged without duplicate activation |
| POST | `/api/platform-payments/{transactionId}/refunds` | `PLATFORM_REFUND:CREATE`; `Idempotency-Key` | Request a full/partial platform refund against refundable balance |
| POST | `/api/platform-refunds/{refundId}/approve` | `PLATFORM_REFUND:APPROVE` | Approve according to platform refund policy |
| POST | `/api/platform-refunds/{refundId}/attempts` | `PLATFORM_REFUND:APPROVE` | Create a provider refund attempt from server configuration |
| GET | `/api/platform-refunds/{refundId}` | `PLATFORM_REFUND:VIEW` | Return refund state, amount and provider-attempt status |
| POST | `/api/payment-providers/platform/{provider}/refund-callback` | Provider signature/system merchant verification; no customer JWT | Verify and apply one platform refund result |

The platform callback response contains `accepted`, `replayed`, `errorCode`, `attemptId`, `attemptStatus`, `orderStatus`, `transactionPublicId` and `contractPublicId`. A successful callback is the only provider evidence that can move an order toward `PAID`/`APPLIED` and activate entitlement.

#### Platform configuration and reporting

| Method | Path | Required permission | Contract |
|---|---|---|---|
| GET | `/api/platform/payment-configuration` | `PAYMENT_READINESS:VIEW` | Return masked provider/environment configuration and readiness blockers |
| PUT | `/api/platform/payment-configuration` | `PAYMENT_READINESS:UPDATE` | Update non-secret configuration references; production approval remains separate |
| POST | `/api/platform/payment-configuration/validate?provider={provider}` | `PAYMENT_READINESS:UPDATE` | Run no-money readiness checks |
| GET | `/api/platform/payment-configuration/{provider}/{environment}` | `PAYMENT_READINESS:VIEW` | Return one masked configuration/readiness record |
| GET | `/api/admin/reports/platform-revenue` | `PLATFORM_REVENUE:VIEW` | Filter by `from`, `to`, `basis`, provider, method, transaction type, plan code and `zoneId`; return purchase/renewal/upgrade/refund/credit/net and reconciliation data |
| GET | `/api/admin/reports/platform-revenue/export` | `PLATFORM_REVENUE:EXPORT` | Export the same normalized platform report and include checksum and row-count headers |

### Financial API coverage

| Requirement | Documentation evidence |
|---|---|
| FR-001: independent bounded contexts | Context table, Property Commerce API and Platform Billing API sections; separate callback, refund and reporting paths |
| FR-046: stable financial errors and safe client contract | Stable error envelope, HTTP/retry matrix and callback/idempotency rules |

The source-verified endpoint inventory is maintained against controller mappings; an endpoint not listed in these tables is not a financial contract merely because an entity or service exists.

## 7. PROPERTY IMPORT & CLAIM API

### 7.1. Property Import Management

#### 7.1.1. Search and Stage Properties (Admin)
- **Endpoint**: /api/admin/property-imports/search
- **Method**: POST
- **Role**: SUPER_ADMIN
- **Permission**: `PROPERTY_IMPORT_CREATE` hoặc role `SUPER_ADMIN`
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
- **Permission**: `PROPERTY_IMPORT_VIEW` hoặc role `SUPER_ADMIN`

#### 7.1.3. Import Valid Items
- **Endpoint**: /api/admin/property-imports/{batchId}/import
- **Method**: POST
- **Permission**: `PROPERTY_IMPORT_EXECUTE` hoặc role `SUPER_ADMIN`
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
- **Authentication**: `isAuthenticated()`; controller hiện chưa lấy user id từ principal và đang dùng fallback id trong source, vì vậy claim chỉ được ghi `PARTIAL` cho đến khi được sửa và kiểm thử.
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
- **Permission**: `PROPERTY_CLAIM_VIEW` hoặc role `SUPER_ADMIN`

#### 7.2.3. Approve Claim
- **Endpoint**: /api/admin/property-claims/{id}/approve
- **Method**: POST
- **Permission**: `PROPERTY_CLAIM_APPROVE` hoặc role `SUPER_ADMIN`
- **Response** (200 OK): Grants the user OWNER role for the property.

`reject` dùng cùng permission `PROPERTY_CLAIM_APPROVE`. Quyền reviewer và requester phải được lấy từ principal; không dùng id cố định trong tài liệu nộp.
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

## Deferred customer booking domains (2026-07-28)

The following capabilities are intentionally `DEFER_FEATURE`; the current UI must not fabricate payloads or completion evidence for them:

- **Mixed RoomType booking**: the advertised contract remains one `roomTypeId` plus `quantity`. A cart containing multiple RoomTypes requires a new aggregate request, inventory lock strategy, pricing and cancellation rules.
- **Customer add-on services**: `ReservationServiceItem` and `AddServiceRequest` support staff-side reservation operations. There is no customer-owned browse/select/price/refund contract in the routed checkout flow.
- **Customer reviews**: public `reviewScore`/`reviewCount` values are aggregate search metadata, not proof of a Review entity or customer submission API. Review creation requires ownership/verified-stay rules, moderation, edit/delete policy and score aggregation.

Until separate feature specifications define those contracts, customer checkout must submit only the current reservation fields and property detail must present rating metadata honestly without a review-submission affordance.
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

## UI Audit Integration Notes (2026-07-27)

Không bổ sung endpoint mới trong đợt UI audit. Frontend xác minh và tiếp tục dùng các contract hiện có:

- `GET /api/auth/my-menu`: menu theo quyền; URL trả về phải tồn tại trong Angular route inventory.
- `GET /api/management/context?activePropertyId={id}`: trả property được cấp, active property, plan/limits/usage và dashboard. Frontend phải hiển thị success, empty hoặc error thay vì loading vô hạn.
- `GET /api/management/room-types?propertyId={id}` và `GET /api/management/rooms?propertyId={id}`: `propertyId` là context yêu cầu, backend vẫn xác minh assignment.
- `GET /api/subscriptions/plans` và `GET /api/subscriptions/me`: billing UI chỉ hiển thị hành động mua/nâng cấp khi có contract order/payment thật; không mô phỏng bằng alert.
- `GET /api/admin/{partner-endpoint}`: generic partner page phải kết thúc loading ở success/error và không suy diễn schema/action không được API trả về.

Response 401/403/404/409/422/500 phải tạo state có thể phục hồi. UI guard hoặc menu visibility không thay thế backend authorization.

## Notification and AI shell contract (2026-07-28)

- Notification panel và AI Concierge là control của `AdminLayout`, không phải route `/admin/notifications` hoặc `/admin/ai-assistant`.
- `POST /api/ai/chat` yêu cầu quyền `AI_CHAT:CREATE`, nhưng hiện lưu system notification trước khi trả lời; lỗi hoặc treo ở notification persistence vì vậy có thể chặn toàn bộ phản hồi AI. Frontend áp dụng timeout và retry để không giữ trạng thái typing vô hạn.
- `GET /api/notifications` và `POST /api/notifications/{id}/read` yêu cầu JWT cùng `REPORT:VIEW`. Danh sách chỉ gồm notification hệ thống (`user_id IS NULL`) và notification nhắm tới principal hiện tại; actor không được mark-read notification của user khác.
- Notification JSON dùng boolean `isRead`; field legacy `read` không thuộc contract. Client phải giữ trạng thái đã đọc sau khi tải lại danh sách.
- SockJS handshake `/ws/**` chỉ được mở để thiết lập transport. STOMP `CONNECT` bắt buộc `Authorization: Bearer <JWT>` và origin phải nằm trong `app.websocket.allowed-origins`.
- Subscription hợp lệ chỉ gồm `/topic/admin/notifications` cho principal có `REPORT:VIEW` và `/user/queue/notifications` cho principal đã xác thực. Client không được publish notification qua STOMP.
- System notification được push tới protected admin topic; user-targeted notification được push bằng Spring user destination. Topic cũ `/topic/notifications` không còn là contract hợp lệ.

## Customer support chat contract decision (2026-07-28)

- Mô hình được chọn là **hàng đợi CSKH trung tâm LuxeStay**, thuộc module `SYSTEM` và dùng function `AI_CHAT`. Đây không phải chat nhân viên theo property; function `HOTEL.CHAT` và menu `/admin/chat` trùng lặp phải được loại khỏi seed/quyền vận hành khách sạn.
- Chat dùng endpoint SockJS/STOMP riêng `GET /ws-chat/**`. HTTP handshake được mở để trình duyệt thiết lập SockJS, nhưng STOMP `CONNECT` bắt buộc header `Authorization: Bearer <JWT>` và chỉ chấp nhận origin cấu hình trong `app.websocket.allowed-origins`. Chat và notification có session marker/interceptor riêng để không dùng nhầm destination policy.
- Customer gửi `SEND /app/chat.support.send` với payload `{ "content": "..." }`. Sender luôn lấy từ JWT principal; client không gửi `senderId`, `receiverId` hoặc account hỗ trợ. Backend lưu customer message với `receiver_id = 0` để biểu diễn hàng đợi trung tâm và phát tới `/topic/support/messages`.
- Support subscribe `/topic/support/messages` và gửi `SEND /app/chat.support.reply` với payload `{ "customerId": 123, "content": "..." }`. Subscribe cần `AI_CHAT:VIEW`; reply cần `AI_CHAT:CREATE`. Backend chỉ cho reply tới customer đã có conversation trong hàng đợi trung tâm.
- Customer và support đều nhận reply cá nhân qua authenticated user destination `/user/queue/messages`; không subscribe `/user/{id}/queue/messages`. `convertAndSendToUser` dùng username của principal/customer, không dùng ID do client cung cấp.
- `GET /api/chat/me/history` chỉ trả lịch sử của principal hiện tại. `GET /api/chat/support/conversations` và `GET /api/chat/support/conversations/{customerId}` yêu cầu `AI_CHAT:VIEW`; endpoint support chỉ đọc customer đã xuất hiện trong hàng đợi.
- Nội dung rỗng hoặc dài hơn 2.000 ký tự bị từ chối. REST trả 401/403/404 theo authentication, permission hoặc conversation scope; STOMP trả error frame và không persist message khi CONNECT/SEND/SUBSCRIBE không hợp lệ.
- Regression bắt buộc gồm unauthenticated connect, sender spoofing, arbitrary history IDs, actor thiếu `AI_CHAT`, cross-account subscribe, sai customer recipient, user-destination delivery, reconnect/offline/send failure và accessible open/close/dialog controls.
## Receptionist admin access contract (2026-08-03)

- `GET /api/users/customers` returns customers visible to the caller and
  requires `CUSTOMER:VIEW`.
- `POST /api/users/customers` and `PUT /api/users/customers/{id}` require
  `CUSTOMER:CREATE` and `CUSTOMER:UPDATE`; the server always applies the
  `CUSTOMER` role and preserves tenant scope.
- `GET /api/v1/hotels/accessible` returns the caller's assigned properties for
  inventory selectors. System administrators retain the global property list.
- Receptionist inventory reads remain protected by `ROOM_TYPE:VIEW` and
  `ROOM:VIEW`; reservation and invoice reads use `RESERVATION:VIEW` and
  `INVOICE:VIEW` respectively.
- Frontend screens must not call optional service endpoints when the account
  lacks `HOTEL_SERVICE:VIEW`; a denied optional request must not trigger portal
  navigation.
