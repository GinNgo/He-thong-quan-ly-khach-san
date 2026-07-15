# Admin Route, Menu and Function Matrix - 2026-07-15

Admin navigation uses the database-backed `/api/auth/my-menu` response. The
frontend fallback contains only Dashboard and Profile and is used only when the
menu API is unavailable.

| Function Code | Menu | Canonical route | Component | API | Duplicate before fix | Status |
|---|---|---|---|---|---|---|
| REPORT | Bảng điều khiển | `/admin/dashboard` | `Dashboard` | dashboard APIs | No | PASS |
| SYSTEM | Khai báo trang | `/admin/modules` | `ModuleManagementComponent` | `/api/modules`, `/api/functions` | No | PASS |
| ROLE | Vai trò | `/admin/roles` | `RoleManagementComponent` | `/api/roles` | No | PASS |
| ROLE_PERMISSION | Phân quyền | `/admin/role-permissions` | `RolePermissionComponent` | `/api/role-permissions` | No | PASS |
| USER | Người dùng | `/admin/users` | `UserManagement` | `/api/users` | No | PASS |
| CUSTOMER | Khách hàng | `/admin/customers` | `UserManagement` | `/api/users` | No | PASS |
| ROOM_TYPE | Loại phòng | `/admin/room-types` | `RoomTypeManagement` | `/api/room-types` | No | PARTIAL: amenities/server paging |
| ROOM | Phòng | `/admin/rooms` | `RoomManagement` | `/api/rooms` | No | PARTIAL: status history/server paging |
| RESERVATION | Đặt phòng | `/admin/reservations` | `ReservationManagement` | `/api/reservations` | Alias removed | PASS |
| HOTEL_SERVICE | Dịch vụ khách sạn | `/admin/services` | `ServiceManagement` | `/api/services` | Code migrated | PASS |
| INVOICE | Hóa đơn | `/admin/invoices` | `InvoiceManagement` | `/api/invoices` | No | PASS |
| RESERVATION_PAYMENT | Thanh toán đặt phòng | `/admin/payments` | not implemented | payment API missing | Code reused FINANCE | BLOCKED/HIDDEN |

## Duplicate cause

`DataInitializer` inserted separate operation functions such as
`CHECKIN_OPERATION`, `IN_HOUSE_GUESTS` and `CHECKOUT_OPERATION` with the same
`/admin/reservations` URL. Housekeeping and maintenance aliases similarly reused
`/admin/property-rooms`. Super administrators received every function, so the
same route appeared more than once even though Angular declared it once.

The safe migration merges each alias action mask into the canonical function,
removes the alias permission rows, then removes the alias function. It does not
delete roles or canonical role-permission mappings.

## Deprecated route policy

The canonical routes are `/admin/roles`, `/admin/role-permissions`,
`/admin/room-types` and `/admin/rooms`. Legacy aliases are redirects only; no
second page component is introduced.
