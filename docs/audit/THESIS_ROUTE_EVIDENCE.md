# Route và actor evidence - 2026-07-28

Nguồn chính: frontend/src/app/app.routes.ts. Route presence chỉ chứng minh entry point UI; hoàn thành end-to-end còn cần API, authorization, persistence và verification.

## Public/customer routes

| Route | Actor dự kiến | Guard | Capability |
| --- | --- | --- | --- |
| / | Guest | Không | Home/search |
| /search | Guest | Không | Property search |
| /hotel/:id | Guest | Không | Property detail/availability |
| /booking/:roomTypeId | Customer | clientAuthGuard | Booking checkout |
| /profile | Customer | clientAuthGuard | Profile |
| /booking-history | Customer | clientAuthGuard | Reservation history |
| /my-invoices | Customer | clientAuthGuard | Customer invoice |
| /settings | Customer | clientAuthGuard | Account settings |
| /payment-simulator | Customer/Test | Không | Payment simulator |
| /payment-result | Customer/Test | Không | Payment result |
| /login, /register | Guest | Không | Authentication |
| /partner/register | Customer/Partner | Không | Property registration |
| /partner/registration-status | Customer/Partner | clientAuthGuard | Registration status |

## Admin routes

Admin layout có authGuard. Các nhóm có permissionGuard và function/action metadata gồm dashboard, users/customers, room-types, rooms, services, reservations, invoices, modules, chat, plans, roles, role-permissions, property-imports và property-claims.

Các màn hình partner overview gồm property owners, registrations, unsubscribed owners, approvals, staff, property room types/rooms, subscription orders/payments và software contracts. Các route này cần đối chiếu riêng với backend authorization vì một số route dùng metadata data thay vì permissionGuard trực tiếp.

## Management routes

Management layout dùng authGuard + roleGuard với PROPERTY_OWNER, HOTEL_ADMIN, HOTEL_MANAGER, SUPER_ADMIN và ADMIN. Các route hiện có:

- /management/dashboard
- /management/properties
- /management/room-types
- /management/rooms
- /management/billing
- /management/subscription -> redirect /management/billing

## Route/security observations

- Frontend có nhiều guard khác nhau: authGuard, clientAuthGuard, permissionGuard và roleGuard.
- Backend vẫn là nguồn quyết định quyền mutation; không kết luận an toàn chỉ từ route guard.
- /admin/properties và một số partner-overview route cần kiểm tra backend permission cụ thể trước khi gắn COMPLETE.
- /admin/chat liên quan support chat/notification; backend và frontend unit tests đã pass, nhưng Playwright authenticated E2E hiện BLOCKED.
- Wildcard redirect và alias route phải được kiểm tra khi lập screenshot/caption để tránh dùng URL không canonical.

## Evidence next step

Đối chiếu từng route với controllers, function code, service và test. Ghi capabilityId và reportSections vào FEATURE_TRACEABILITY_MATRIX.md; route không có evidence end-to-end giữ PARTIAL/REVIEW.
