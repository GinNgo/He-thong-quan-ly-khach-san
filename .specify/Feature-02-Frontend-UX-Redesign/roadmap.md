# Feature-02 — Frontend UX Redesign Roadmap

**Branch:** `feature/frontend-ux-redesign`
**Baseline:** Feature-01 commit `70a73c3`
**Created:** 2026-07-26
**Overall status:** IN PROGRESS

## Goal

Hội tụ UX LuxeStay cho ba nhóm actor mà không thay đổi contract bảo mật Feature-01:

- System: `SUPER_ADMIN`, `ADMIN`.
- Hotel operation: `PROPERTY_OWNER`, `HOTEL_ADMIN`, `HOTEL_MANAGER`, `RECEPTIONIST`, `ACCOUNTANT`, `STAFF`.
- Customer: tìm kiếm, đặt phòng, thanh toán, booking cá nhân, hồ sơ.

Frontend menu/guard chỉ cải thiện UX. Backend tiếp tục là nguồn sự thật cho authentication, permission, tenant scope và customer ownership.

## Delivery order

| ID | Sub-feature | Scope | Dependency | Status |
|---|---|---|---|---|
| 02A | Design System & Shared Components | Tokens, focus, shared feedback states, shared component consistency | Feature-01 | IN PROGRESS |
| 02B | App Shell, Sidebar, Header & Role Navigation | Desktop/mobile shell, role menu, active route, breadcrumbs, property context | 02A | NOT STARTED |
| 02C | Hotel, Room Type, Room & Housekeeping Management | Property and inventory operations | 02A, 02B | NOT STARTED |
| 02D | Reservation, Check-in, Check-out & Extra Services | Reservation lifecycle and operation desk | 02A, 02B, 02C | NOT STARTED |
| 02E | Invoice, Payment, Revenue & Reports | Billing lifecycle and reporting UX | 02A, 02B, 02D | NOT STARTED |
| 02F | Customer Search, Hotel Detail, Booking & Payment | Public/customer journey | 02A | NOT STARTED |
| 02G | Users, Roles, Subscription & System Administration | System and property administration | 02A, 02B | NOT STARTED |
| 02H | Responsive, Accessibility & UX Regression | Cross-feature keyboard, screen-size and route regression | 02A–02G | NOT STARTED |

## Source-grounded route inventory

| Page | Route | Actor | Component | Current classification | Priority |
|---|---|---|---|---|---|
| Home | `/` | Public/customer | `HomeComponent` | BASIC UI | P1 |
| Property search | `/search` | Public/customer | `PropertySearchPageComponent` | BASIC UI | P1 |
| Hotel detail | `/hotel/:id` | Public/customer | `HotelDetailComponent` | BASIC UI | P1 |
| Booking checkout | `/booking/:roomTypeId` | Customer | `BookingCheckoutComponent` | BASIC UI | P1 |
| Customer profile/bookings | `/profile`, `/booking-history` | Customer | `ProfileComponent` | UX ISSUE: two routes share tab-driven component | P1 |
| Customer invoices/settings | `/my-invoices`, `/settings` | Customer | lazy standalone components | BASIC UI | P2 |
| Payment simulator/result | `/payment-simulator`, `/payment-result` | Customer/demo | lazy standalone components | BASIC UI; API contract must remain idempotent | P1 |
| Partner registration/status | `/partner/register`, `/partner/registration-status` | Customer/partner | lazy standalone components | BASIC UI | P2 |
| Admin dashboard | `/admin/dashboard` | Authorized staff | `Dashboard` | BASIC UI | P1 |
| Staff/customer users | `/admin/users`, `/admin/customers` | Authorized staff | `UserManagement` | BASIC UI | P2 |
| Room types | `/admin/room-types` | Hotel operation | `RoomTypeManagement` | BASIC UI | P1 |
| Rooms | `/admin/rooms` | Hotel operation | `RoomManagement` | BASIC UI | P1 |
| Services | `/admin/services` | Hotel operation | `ServiceManagement` | BASIC UI | P1 |
| Reservations/list/timeline/create | `/admin/reservations/**` | Hotel operation | reservation components | BASIC UI | P1 |
| Invoices | `/admin/invoices` | Accountant/authorized staff | `InvoiceManagement` | BASIC UI | P1 |
| Properties | `/admin/properties` | System/property admin | `PropertyManagementComponent` | BASIC UI | P1 |
| Roles/permissions/modules/plans | `/admin/{roles,role-permissions,modules,plans}` | System admin | lazy standalone components | BASIC UI | P2 |
| Partner administration | `/admin/property-*`, subscription and contract routes | System admin | `PartnerOverviewComponent` | UX ISSUE: generic endpoint-driven page; authorization audit required | P1 |
| Management dashboard/properties | `/management/{dashboard,properties}` | Property actors | `ManagementDashboardComponent` | UX ISSUE: two routes share component | P1 |
| Management inventory | `/management/{room-types,rooms}` | Property actors | `ManagementInventoryComponent` | BASIC UI | P1 |
| Subscription billing | `/management/billing` | Property owner/admin | `SubscriptionBillingComponent` | BASIC UI | P1 |
| Forbidden/not found | `/403`, `/admin/404` | All | error components | BASIC UI | P1 |

## Audit matrix contract

Detailed page/API matrix is maintained in `audit-matrix.md` and must contain:

`Page → Route → Actor → Component → API hiện tại → Dữ liệu cần → Dữ liệu đang trả → Field thiếu → Permission → Tenant scope → Frontend change → Backend change → Priority → Acceptance criteria`.

Rules:

1. API fields remain `TBD — source audit required` until matching Angular service and Java controller/service/DTO are read.
2. No backend endpoint is added from UI assumptions.
3. Every mutation retains backend permission and tenant/customer ownership enforcement.
4. Any route lacking an explicit frontend guard is treated as UX/security-audit work, not proof that backend is public.
5. No sub-feature advances while its required test/build gate is red.

## Cross-feature gates

For each sub-feature:

1. Specification and clarification grounded in source/docs.
2. Plan, checklist and tasks converge with implementation.
3. Acceptance tests pass.
4. Frontend unit tests and production build pass.
5. Backend Maven regression passes when API/backend code changes; final convergence always runs full Maven regression.
6. `git diff --check` and staged secret/artifact scan pass.
7. Commit contains only explicit files.
8. Non-force push to `origin/feature/frontend-ux-redesign`.
9. Status changes to PASSED only after commit and push evidence exists.

## Explicit non-goals

- No replacement of Angular, PrimeNG, Bootstrap or Tailwind.
- No new UI dependency while installed stack covers requirement.
- No weakening of Feature-01 RBAC, 401/403 behavior, tenant isolation or anti-IDOR.
- No production deploy, migration, payment, credential rotation, history rewrite, merge or force-push.
- No fake production data or client-side full-dataset filtering where server pagination/query is required.

## Commit sequence

1. `feat(ui): add shared hospitality design system`
2. `feat(ui): improve application shell and role navigation`
3. `feat(hotel): redesign property and room management`
4. `feat(reservation): improve booking operation workflows`
5. `feat(billing): redesign invoice and payment experience`
6. `feat(customer): redesign hotel search and booking flow`
7. `feat(admin): improve user role and subscription management`
8. `test(ui): add frontend UX regression coverage`
9. `docs(spec): complete Feature-02 specification artifacts`

Commits may combine only when every included sub-feature independently meets its acceptance criteria.