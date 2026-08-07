# UI Flow Test Matrix

Status ban đầu là `UNVERIFIED` trừ những candidate trong `incomplete-function-register.md`. Matrix này là thiết kế coverage; kết quả runtime được cập nhật ở giai đoạn implementation/execution.

**US1 execution 2026-08-01**: source inventory discovered the router surface, confirmed 2 stale `/client/profile` test routes, 10 placeholder links (9 active login/register and 1 dormant invoice mockup), 2 unsupported forgot-password surfaces, 2 explicitly disabled Coming Soon tabs, and 4 router-dormant mock component paths. Runtime-visible findings continue in US2/US3.

| ID | Actor | Route/Surface | Primary Flow | Required Variants | Priority | Existing Coverage Signal |
|----|-------|---------------|--------------|-------------------|----------|--------------------------|
| TF-PUB-001 | Public | `/` home | Tìm kiếm từ hero/sticky bar | invalid dates, empty/error/retry, keyboard, mobile | P1 | `home-search`, `public-customer-quality`, `public-flows` |
| TF-PUB-002 | Public | `/search` | Lọc, sort, phân trang, mở property | no result, API error, URL state, mobile filter | P1 | `search-result`, `search-booking-flow`, smoke |
| TF-PUB-003 | Public | `/hotel/:id` | Xem chi tiết và chọn room type | invalid id, image error, unavailable room, claim dialog | P1 | real smoke chỉ kiểm tra invalid id; cần mở rộng |
| TF-PUB-004 | Public | `/login` | Đăng nhập và returnUrl | invalid credential, social config, locked user, forgot password | P1 | `public-flows`, `payment`, real smoke |
| TF-PUB-005 | Public | `/register` | Đăng ký tài khoản | validation, duplicate identity, terms links | P1 | `public-flows` |
| TF-PUB-006 | Public | `/admin/login` | Admin login và redirect | invalid credential, locked user, forgot password | P1 | `admin-flows`, `public-flows`, smoke |
| TF-PUB-007 | Public | Header/footer | Navigation, locale, partner CTA, legal/support links | mobile menu, Escape, href target | P2 | `public-customer-quality`; legal links chưa đủ |
| TF-PUB-008 | Public | Search service tabs | Chọn loại lưu trú | disabled flight/transfer, no request, label Coming Soon | P3 | chưa thấy dedicated E2E |
| TF-CUS-001 | Customer | `/profile` | Xem/sửa profile và avatar | validation, upload error, reload persistence | P1 | coverage tồn tại nhưng có suite dùng route cũ |
| TF-CUS-002 | Customer | `/booking-history` | Xem, lọc và hủy booking | empty, cancellation rule, duplicate cancel, reload | P1 | payment/refund + integrated matrix; cần route-specific |
| TF-CUS-003 | Customer | `/settings` | Đổi mật khẩu | invalid current password, mismatch, submitting, success | P2 | chưa thấy browser coverage rõ |
| TF-CUS-004 | Customer | `/my-invoices` | Xem, tải, email invoice | empty/error, duplicate action, download/email failure | P1 | `stay-checkout-invoice` chỉ xem detail |
| TF-CUS-005 | Customer | `/booking/:roomTypeId` | Tạo reservation/hold và thanh toán | invalid context, price drift, duplicate submit, payment failure/recovery | P1 | property booking/payment suites |
| TF-CUS-006 | Customer | `/payment-simulator` | Xác nhận payment simulator | invalid context, duplicate confirm, callback recovery | P1 | unit + payment suites |
| TF-CUS-007 | Customer | `/payment-result` | Hiển thị kết quả và quay lại hành trình | success/fail/pending/invalid callback/reload | P1 | unit + property booking/payment |
| TF-CUS-008 | Customer | Support chat widget | Load history, send, reconnect | unauthenticated, offline, duplicate send, admin reply | P1 | `support-chat-lifecycle` |
| TF-CUS-009 | Customer | `/partner/register` + status | Đăng ký partner và theo dõi | validation, duplicate/pending/approved/rejected | P2 | chưa thấy dedicated real browser coverage |
| TF-ADM-001 | Admin | `/admin/dashboard` | Onboarding, analytics, work order, exports | incomplete CTA, data mismatch, loading/error, download | P1 | route smoke; current function depth thấp |
| TF-ADM-002 | Admin | `/admin/users`, `/admin/customers` | List/create/edit/status | permission, validation, pagination, duplicate mutation | P1 | `admin-flows` route-level |
| TF-ADM-003 | Admin | `/admin/room-types`, `/admin/rooms`, `/admin/services` | CRUD inventory | tenant scope, validation, image/error, duplicate submit | P1 | admin core/owner flows; depth cần audit |
| TF-ADM-004 | Admin | Reservations routes | Create, timeline, check-in, add service, settle, checkout | concurrency, invalid state, permission, reload | P1 | stay checkout/invoice + payment/refund |
| TF-ADM-005 | Admin | `/admin/invoices` | Xem/in/xuất hóa đơn | missing payment, print/download error, permissions | P1 | admin route-level + stay invoice flow |
| TF-ADM-006 | Admin | Roles/permissions/modules | Tạo/sửa role, lưu permission, module navigation | protected role, no permission, stale menu, reload | P1 | admin core + admin flows |
| TF-ADM-007 | Admin | Properties/imports/claims | CRUD/approve/import/claim | invalid file, duplicate import, approval permission, error feedback | P2 | route coverage không đầy đủ |
| TF-ADM-008 | Admin | Partner overview routes | Xem danh sách và action theo endpoint | disabled action, approve, empty/error, permission | P2 | unit có approval; browser cần mở rộng |
| TF-ADM-009 | Admin | `/admin/plans` | Xem/quản lý catalog plan | loading/error, permission, contact CTA | P2 | route-level |
| TF-ADM-010 | Admin | `/admin/chat` | Chọn conversation và reply | reconnect, duplicate send, permission, empty | P1 | support chat lifecycle |
| TF-MGT-001 | Owner/Manager | `/management/dashboard` | Load context, switch property, read metrics | one/many/no property, context error, stale data | P1 | integrated matrix + owner flows |
| TF-MGT-002 | Owner/Manager | `/management/properties` | Quản lý cơ sở từ navigation | distinguish from dashboard, CRUD intent, permission | P1 | candidate partial; route reuses dashboard component |
| TF-MGT-003 | Owner/Manager | room types/rooms | CRUD theo property | tenant isolation, expired subscription, validation | P1 | entitlement + owner/admin flows |
| TF-MGT-004 | Owner/Manager | payment configuration | View/save/disable provider config | wrong property, permission, secret masking, simulator/sandbox | P1 | dedicated Playwright suite |
| TF-MGT-005 | Owner/Manager | billing/subscription | Xem usage/limits và upgrade path | active/expired/lifetime/multi-subscription | P1 | `subscription-entitlements` |
| TF-X-001 | All | Guards/navigation | Direct URL, returnUrl, 403/404, logout | expired token, redirect loop, stale menu route | P1 | public/real smoke; cần matrix theo role |
| TF-X-002 | All | Responsive | Hoàn thành P1 tại 4 viewport | overflow, fixed nav overlap, mobile table/dialog | P1 | partial in home/public quality |
| TF-X-003 | All | Accessibility | Keyboard/focus/name/error announcement | Escape, focus restore, zoom 200%, reduced motion | P1 | localization/motion suite; coverage chưa toàn diện |
| TF-X-004 | All | Reliability | Network slow/offline/retry | duplicate submit, stale response, reload persistence | P1 | rải rác; cần quy tắc chung |
| TF-X-005 | All | Test integrity | Đối chiếu test với router/runtime | stale route, mocked-only, duplicate suite, silent skip | P1 | baseline thấy `/client/profile` stale |

## Execution Reconciliation - 2026-08-01

| Executed Suite/Scenario Group | Matrix IDs | Integration Level | Result | Disposition |
|-------------------------------|------------|-------------------|--------|-------------|
| `ui-source-inventory.spec.ts` (7 scenarios) | TF-X-005, TF-PUB-004..008, TF-ADM-005 | SOURCE | 4 PASS / 3 FAIL | UIF-006, UIF-007, UIF-010, UIF-011, UIF-013 and dormant UIF-015 remain registered |
| `ui-public-capability-audit.spec.ts` (6 scenarios) | TF-PUB-004..008 | BROWSER + CONTROLLED RUNTIME | 2 PASS / 4 FAIL | Home-to-login and Coming Soon behavior pass; forgot-password and legal-link gaps verified |
| `ui-admin-incomplete-audit.spec.ts` (6 scenarios) | TF-ADM-001 | INTERCEPTED | 0 PASS / 6 FAIL | UIF-001..UIF-005 verified; failures are product-gap evidence |
| `ui-management-incomplete-audit.spec.ts` (1 scenario) | TF-MGT-002 | INTERCEPTED | 0 PASS / 1 FAIL | UIF-012 verified with identical dashboard/properties surfaces |
| `ui-responsive-accessibility-audit.spec.ts` (8 scenarios) | TF-PUB-001, TF-PUB-004, TF-X-002, TF-X-003 | BROWSER | 8 PASS | Home/login overflow and focus checks pass at 375/768/1024/1440 |
| Initial `real-environment-smoke.spec.ts` + `ui-real-flow-audit.spec.ts` run (8 scenarios) | TF-PUB-003, TF-CUS-001..004, TF-ADM-002..010, TF-MGT-001..005, TF-X-001 | REAL INTEGRATION | 1 PASS / 1 FAIL / 3 SKIPPED / 3 NOT RUN | Historical credential-blocked baseline retained for traceability |
| Authenticated follow-up across real smoke, real-flow audit, subscriptions and payment configuration (15 scenarios) | TF-CUS-001..004, TF-ADM-002..010, TF-MGT-001, TF-MGT-004, TF-MGT-005, TF-X-001 | REAL INTEGRATION + 1 CONTROLLED FIXTURE | 14 PASS / 1 SKIPPED | Customer/admin/owner routes and subscription states pass; foreign-property denial remains data-blocked |
| Booking/payment positive and negative suites (4 scenarios) | TF-CUS-005..007 | INTERCEPTED | 4 PASS | Useful regression evidence; not sufficient for `COMPLETE` |
| `property-payment-configuration.spec.ts` (2 scenarios) | TF-MGT-004 | INTERCEPTED + OPTIONAL REAL | 1 PASS / 1 SKIPPED | Fixture coverage passed; real property-scope denial blocked by credentials |
| `stay-checkout-invoice.spec.ts` (1 scenario) | TF-CUS-004, TF-ADM-004, TF-ADM-005 | INTERCEPTED | 1 PASS | Full controlled lifecycle passed; real integration still required |
| Angular unit suite (155 tests) | Component/service coverage across matrix | UNIT | PASS | Diagnostic quality gate only |
| Angular production build | All frontend surfaces | BUILD | PASS WITH WARNINGS | CSS budget and CommonJS warnings recorded in execution report |

Coverage decision: authenticated core role journeys now have real-integration pass evidence. No incomplete-register gap is promoted to `COMPLETE`, because those specific controls still fail or require deeper traversal; intercepted and unit-only evidence remain non-completion evidence.

## Smoke P1 Candidate Set

1. Public home -> search -> property detail.
2. Customer login -> booking -> payment result -> booking history/invoice.
3. Admin login -> reservation check-in/service/payment/checkout -> invoice -> room dirty.
4. Owner login -> switch property -> inventory -> payment configuration -> billing.
5. Customer/admin support chat send/reply/reconnect.
6. Permission denied + tenant isolation + expired subscription read/mutation behavior.
7. Admin dashboard incomplete-function verification.
8. Responsive/keyboard pass cho home, booking, admin reservation và management inventory.
