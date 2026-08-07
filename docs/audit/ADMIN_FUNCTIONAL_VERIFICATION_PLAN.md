# Kế hoạch xác minh chức năng Admin LuxeStay

**Ngày lập:** 2026-07-29  
**Phạm vi:** toàn bộ route con dưới `/admin` trong `frontend/src/app/app.routes.ts`  
**Mục tiêu:** xác minh chức năng theo hành trình dữ liệu và quyền thật; không dùng việc route mở được hoặc `body visible` làm kết luận hoàn thành.

## 1. Quy ước trạng thái

| Trạng thái | Ý nghĩa |
| --- | --- |
| `PASS` | Fixture/backend LuxeStay tải được dữ liệu, thao tác chính và authorization đã chạy đạt. |
| `PARTIAL` | Có một phần hành trình hoặc test, còn thiếu mutation, error/empty state, quyền hoặc integration. |
| `FAIL` | Đã chạy trên môi trường LuxeStay nhưng chức năng thực tế không đạt acceptance criteria. |
| `BLOCKED` | Chưa thể kết luận do backend/cổng/credential/fixture/cấu hình hoặc phụ thuộc ngoài phạm vi. |
| `NOT_APPLICABLE` | Route cấu trúc/redirect hoặc chức năng không có thao tác nghiệp vụ áp dụng; phải có lý do. |

Không được dùng `PASS` cho route chỉ có component render. Mọi kết quả hiện tại dưới đây là **STATIC_REVIEW/BLOCKED_RUNTIME** cho tới khi chạy lại trên backend LuxeStay cô lập.

## 2. Snapshot môi trường hiện tại

| Hạng mục | Quan sát ngày 2026-07-29 | Tác động |
| --- | --- | --- |
| Angular dev server | `localhost:4200` đang chạy (PID 17964) | Có thể kiểm tra route shell. |
| Cổng `8080` | Đang do Docker `videoai-api-1` publish, không phải backend LuxeStay | Frontend có thể nhận `Failed to fetch`/response sai; không kết luận chức năng. |
| Credential `LUXESTAY_E2E_*` | Chưa có trong môi trường hiện tại | Các test real-environment sẽ skip hoặc không đủ dữ liệu. |
| Playwright hiện hữu | `admin-flows.spec.ts` chủ yếu `body visible`; có `expect(true)`; `admin-core-management.spec.ts` dùng credential hardcode khác nhau | Chưa đủ bằng chứng data-backed/mutation/authorization. |
| Unit spec Admin | Có ở chat, partner overview, property, room, room-type, service; nhiều route chưa có spec | Cần bổ sung test theo ma trận. |
| Tài nguyên ngoài phạm vi | Nhiều container Docker khác đang chạy | Không được dừng/sửa; phải chọn cổng LuxeStay riêng. |

### Kết quả chạy thử ngày 2026-07-29

- `admin-flows.spec.ts`: 17/17 pass nhưng chỉ là shell smoke; không đủ điều kiện `PASS` theo contract.
- `admin-core-management.spec.ts`: 1 fail, 2 không chạy vì helper `admin/admin` bị giữ ở `/admin/login`; screenshot lỗi ghi nhận thông báo tài khoản/mật khẩu sai.
- Kết luận runtime: `BLOCKED`, không quy lỗi này cho từng màn hình nghiệp vụ cho tới khi fixture/credential/backend LuxeStay được cô lập.

## 3. Route/function matrix

| ID | Route | Component | Guard/permission tĩnh | API/dependency và thao tác cần kiểm tra | Test hiện có | Trạng thái hiện tại | Gap/task |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ADM-01 | `/admin/dashboard` | `Dashboard` | `permissionGuard` `REPORT.VIEW` | `GET /analytics/dashboard`; KPI, doanh thu, occupancy, chart, error/empty; work-order đang mô phỏng empty bằng timer | Chưa có component spec; smoke shell | `BLOCKED` | Data-backed dashboard + error/empty; T066/T068 |
| ADM-02 | `/admin/profile` | `AdminProfileComponent` | Chỉ kế thừa `authGuard`, không có permission route | `GET/PUT /users/me`, `PUT /users/me/password`, upload avatar; đọc/sửa hồ sơ, đổi mật khẩu, validation | Chưa có spec | `BLOCKED` + guard review | Xác định policy route và test profile/password; T061/T066/T068 |
| ADM-03 | `/admin/users` | `UserManagement` (`userType=STAFF`) | `USER.VIEW` | `GET/POST/PUT/DELETE /users`; list, create, update, delete, validation, role/status | E2E shell; chưa mutation data-backed | `BLOCKED` | CRUD + permission actions + test; T066/T067/T068 |
| ADM-04 | `/admin/customers` | `UserManagement` (`userType=CUSTOMER`) | `CUSTOMER.VIEW` | Cùng `/users`, lọc customer; list, create/update/delete theo policy | E2E shell | `BLOCKED` | Xác minh filter và CRUD/authorization; T066/T067 |
| ADM-05 | `/admin/room-types` | `RoomTypeManagement` | `ROOM_TYPE.VIEW` | `GET /v1/hotels`, `GET/POST/PUT/DELETE /room-types`; CRUD, image/price/capacity/status | Unit + core E2E có row assertion (credential `admin/admin`) | `BLOCKED` | Chạy bằng fixture LuxeStay, mutation và permission; T064/T066/T067 |
| ADM-06 | `/admin/rooms` | `RoomManagement` | `ROOM.VIEW` | `GET /v1/hotels`, `/room-types`, `/rooms`; POST/PUT/DELETE `/rooms`, POST `/rooms/bulk`; maintenance/status | Unit + core E2E có row assertion | `BLOCKED` | CRUD, bulk, maintenance, conflict/permission; T066/T067 |
| ADM-07 | `/admin/services` | `ServiceManagement` | `HOTEL_SERVICE.VIEW` | `GET/POST/PUT/DELETE /services`; CRUD và validation giá/trạng thái | Unit; E2E shell | `BLOCKED` | Data-backed CRUD + permission; T066/T067 |
| ADM-08 | `/admin/reservations` | `ReservationManagement` | `RESERVATION.VIEW` | `GET /reservations`, `GET /reservations/:id`, `PUT /:id/status`, `POST /:id/cancel`, `DELETE /:id`; status/cancel/detail | E2E shell | `BLOCKED` | Reservation lifecycle, error/idempotency, authorization; T066/T067 |
| ADM-09 | `/admin/reservations/timeline` | `ReservationTimelineComponent` | `RESERVATION.VIEW` | `GET /rooms` + `GET /reservations`; 14-day grid, room/date match, empty/error | Chưa có spec; E2E shell | `BLOCKED` | Verify data join, date boundary/check-out exclusive; T066 |
| ADM-10 | `/admin/reservations/create` | `ReservationCreate` | `RESERVATION.CREATE` | `POST /reservations`; validate guest/date/room/overlap/payment | Chưa có spec; smoke route | `BLOCKED` | Create reservation + overbooking/error; T067/T068 |
| ADM-11 | `/admin/invoices` | `InvoiceManagement` | `INVOICE.VIEW` | `GET /invoices`, `GET /invoices/reservation/:id`, `POST /invoices/reservation/:id`; list/generate/status | E2E shell | `BLOCKED` | Invoice generation and duplicate/idempotency; T066/T067 |
| ADM-12 | `/admin/modules` | `ModuleManagementComponent` | `SYSTEM.VIEW` | Direct `HttpClient` module/function endpoints; tree load, add/edit/delete module/page, validation | E2E shell | `BLOCKED` | Capture exact endpoints, CRUD + backend permission; T061/T066/T067 |
| ADM-13 | `/admin/chat` | `ChatDashboardComponent` | `AI_CHAT.VIEW` | REST support conversations/history + WebSocket `/ws`; load history, send reply, reconnect/error | Unit; E2E shell | `BLOCKED` | Isolated WebSocket/auth and send/reconnect evidence; T066/T067 |
| ADM-14 | `/admin/properties` | `PropertyManagementComponent` | No explicit `permissionGuard`; parent `authGuard` only | `GET/POST/PUT /v1/hotels`, locations, submit/approve/reject; CRUD/approval | Unit; E2E shell | `BLOCKED` + authorization gap | Add/verify route/backend authorization, CRUD and approval; T061/T067/T068 |
| ADM-15 | `/admin/plans` | `SubscriptionPlansComponent` | `SYSTEM.VIEW` | `GET /subscriptions/plans`, `GET /subscriptions/me`; display plans/subscriptions; `purchase()` only toast, no payment mutation | Chưa có spec; E2E shell | `PARTIAL` + runtime blocked | Do not claim purchase complete; either implement separate payment flow or mark roadmap; T068 |
| ADM-16 | `/admin/roles` | `RoleManagementComponent` | `ROLE.VIEW` | `GET/POST/PUT/DELETE /roles`; CRUD, system-role protection | Core E2E row assertion; no mutation proof | `BLOCKED` | CRUD/role policy/permission test; T066/T067 |
| ADM-17 | `/admin/role-permissions` | `RolePermissionComponent` | `ROLE_PERMISSION.VIEW` | `GET /role-permissions/tree/:roleId`, `POST /role-permissions/:roleId`; matrix load/save | Core E2E row assertion; no save proof | `BLOCKED` | Save/reload bitmask and unauthorized action; T066/T067 |
| ADM-18 | `/admin/property-imports` | `PropertyImportsComponent` | `PROPERTY_IMPORT.VIEW` | `GET /admin/property-imports`, POST search/stage, GET items, POST import; preview/duplicate/import | No unit/spec; E2E shell | `BLOCKED` | Fixture/provider isolation, error state, import mutation; T066/T067/T068 |
| ADM-19 | `/admin/property-claims` | `PropertyClaimsComponent` | `PROPERTY_CLAIM.VIEW` | `GET /admin/property-claims`, POST approve/reject; status/reason/owner transfer | No unit/spec; E2E shell | `BLOCKED` | Approval/rejection authorization and audit result; T066/T067 |
| ADM-20 | `/admin/property-owners` | `PartnerOverviewComponent` | **No explicit guard** | `GET /admin/property-owners`; read-only owner/account/subscription report | Unit component; no data-backed E2E | `BLOCKED` + guard review | Permission policy + data load/empty/error; T061/T066/T068 |
| ADM-21 | `/admin/property-registrations` | `PartnerOverviewComponent` | **No explicit guard** | `GET /admin/property-registrations`; read-only ownership registrations | Unit component | `BLOCKED` + guard review | Permission policy + data load; T061/T066 |
| ADM-22 | `/admin/unsubscribed-owners` | `PartnerOverviewComponent` | **No explicit guard** | `GET /admin/property-owners/unsubscribed`; read-only follow-up list | Unit component | `BLOCKED` + guard review | Permission policy + data load; T061/T066 |
| ADM-23 | `/admin/property-approvals` | `PartnerOverviewComponent` | **No explicit guard** | `GET /admin/property-approvals`; `POST /v1/hotels/:id/approve|reject`; approve/reject | Unit component; no data-backed E2E | `BLOCKED` + guard review | Verify row ID contract, approval mutation and authorization; T061/T067 |
| ADM-24 | `/admin/property-staff` | `PartnerOverviewComponent` | **No explicit guard** | `GET /admin/property-staff`; read-only assignment report | Unit component | `BLOCKED` + guard review | Permission/data load; T061/T066 |
| ADM-25 | `/admin/property-room-types` | `PartnerOverviewComponent` | **No explicit guard** | `GET /admin/property-room-types`; link to `/admin/room-types` | Unit component | `BLOCKED` + guard review | Data load and linked-route authorization; T061/T066 |
| ADM-26 | `/admin/property-rooms` | `PartnerOverviewComponent` | **No explicit guard** | `GET /admin/property-rooms`; link to `/admin/rooms` | Unit component | `BLOCKED` + guard review | Data load and linked-route authorization; T061/T066 |
| ADM-27 | `/admin/subscription-orders` | `PartnerOverviewComponent` | **No explicit guard** | `GET /admin/subscription-orders`; read-only orders/status | Unit component | `BLOCKED` + guard review | Permission/data load; T061/T066 |
| ADM-28 | `/admin/subscription-payments` | `PartnerOverviewComponent` | **No explicit guard** | `GET /admin/subscription-payments`; read-only payment reconciliation | Unit component | `BLOCKED` + guard review | Permission/data load/privacy; T061/T066/T068 |
| ADM-29 | `/admin/software-contracts` | `PartnerOverviewComponent` | **No explicit guard** | `GET /admin/software-contracts`; read-only contract view | Unit component | `BLOCKED` + guard review | Permission/data load/privacy; T061/T066/T068 |

### Route cấu trúc không phải capability độc lập

`/admin/role`, `/admin/roles-management`, `/admin/permissions/roles`, `/admin/room-type`, `/admin/manage-rooms` là redirect alias; `/admin/404` và wildcard là error handling. Các route này phải có smoke/authorization check nhưng không tạo thêm capability nghiệp vụ.

## 4. Tiêu chí test cho từng route

1. **Access:** unauthenticated -> `/admin/login`; role/permission sai -> `/403`; route không có guard phải được rà soát backend authorization.
2. **Read:** request đúng backend LuxeStay, loading, data, empty, 401/403/5xx và retry/feedback.
3. **Mutation/action:** kiểm tra payload, validation, success refresh, server error, double-submit/idempotency và quyền từng action (`VIEW/CREATE/UPDATE/DELETE/APPROVE`).
4. **Data isolation:** property/tenant scope không đọc hoặc sửa dữ liệu ngoài tenant.
5. **Evidence:** lưu command, ngày chạy, role, fixture ID, request/response đã loại secret, screenshot caption và Playwright artifact.

## 5. Runbook mở khóa E2E

1. Không dừng container ngoài phạm vi. Chọn cổng backend LuxeStay riêng (ví dụ `18080`) và cấu hình frontend `environment.apiUrl` tương ứng trong phiên chạy.
2. Khởi động SQL Server/database LuxeStay và backend với `application-e2e.yml`; nạp `E2eFixtureInitializer`.
3. Cung cấp đủ `LUXESTAY_E2E_CUSTOMER_USERNAME/PASSWORD`, `LUXESTAY_E2E_ADMIN_USERNAME/PASSWORD`, `LUXESTAY_E2E_OWNER_USERNAME/PASSWORD`.
4. Chạy `npm --prefix frontend run build` và unit trước.
5. Chạy targeted Admin E2E với `--workers=1 --retries=0`; sau đó mở rộng toàn bộ route/action matrix.
6. Nếu backend, fixture hoặc credential chưa sẵn sàng, giữ `BLOCKED` và ghi nguyên nhân; không dùng kết quả từ `videoai-api-1`.

## 6. Completion backlog

| Task | Ưu tiên | Nội dung | Hoàn thành khi |
| --- | --- | --- | --- |
| ADM-FIX-001 | P0 | Cô lập backend/DB/fixture/credential LuxeStay cho E2E | Tất cả smoke không còn `Failed to fetch`, fixture reset được |
| ADM-FIX-002 | P0 | Bổ sung guard/permission policy cho profile, properties và 10 partner-overview route | Route/menu và backend authorization có policy/test rõ |
| ADM-FIX-003 | P0 | Thay E2E shell bằng helper login + data-backed assertions cho 29 route | Mỗi route có read/data/error evidence |
| ADM-FIX-004 | P0 | Bổ sung mutation/authorization E2E cho CRUD, approval, import, claims, role-permission, reservation/invoice | Mỗi action applicable có success/error/forbidden test |
| ADM-FIX-005 | P1 | Bổ sung unit spec cho dashboard, profile, users/customers, reservations, invoices, modules, plans, imports, claims | Component state/validation/action được kiểm tra |
| ADM-FIX-006 | P1 | Sửa dashboard work-orders đang mô phỏng bằng timer | Dữ liệu đến từ API hoặc được ghi rõ là demo/NOT_APPLICABLE |
| ADM-FIX-007 | P1 | Hoàn thiện hoặc hạ trạng thái purchase trong SubscriptionPlans | Có payment contract/test hoặc ghi rõ roadmap, không toast giả là complete |
| ADM-FIX-008 | P1 | Chuẩn hóa endpoint/error typing cho import/claims và bỏ `console.error/alert` trong flow release | Feedback UI có loading/empty/error và test |
| ADM-FIX-009 | P1 | Kiểm tra tenant isolation và backend permission cho mọi `/admin/*` API | Integration/security tests chứng minh không IDOR |
| ADM-FIX-010 | P2 | Chụp screenshot CURRENT theo route/role sau khi E2E pass | Screenshot có caption, privacy review và registry |

## 7. Kết luận hiện tại

Chưa đủ bằng chứng để kết luận toàn bộ Admin đã chạy tốt. Tại ngày 2026-07-29, kết luận trung thực là: **29 route nghiệp vụ đã được inventory; 1 chức năng (`/admin/plans` purchase) có dấu hiệu PARTIAL rõ từ source; các route còn lại cần giữ BLOCKED_RUNTIME cho tới khi backend LuxeStay cô lập và chạy data-backed read/mutation/authorization.** Các task `T059-T068` trong Spec Kit là kế hoạch thực hiện; backlog `ADM-FIX-001..010` là phần hoàn thiện sản phẩm cần triển khai nếu người dùng quyết định sửa code.
