# UI Functional Audit Matrix

**Feature**: Full UI Functional Audit & Premium Polish
**Audit started**: 2026-07-27
**Runtime**: Frontend `4200` and audit backend `8081` verified on 2026-07-28; repository default API remains `8080` and must be restored before handoff.
**Final status legend**: `COMPLETE`, `PARTIAL`, `MISSING`, `BLOCKED`, `BROKEN`

> Rows remain non-complete unless the primary path and required branches were observed through the real UI. Browser evidence below is concise and intentionally omits credentials.

## Environment and Automated Baseline

| Check | Command/Target | Result | Evidence / Blocker |
|---|---|---|---|
| Git branch | `codex/ui-functional-audit-polish` | COMPLETE | Working branch; `main` and `origin/main` both resolve to `0e1421a` |
| Feature selection | `.specify/feature.json` | COMPLETE | Points to `.specify/Feature-03-UI-Functional-Audit-Polish` |
| Frontend runtime | `http://localhost:4200` | COMPLETE | Real browser loaded public, admin and management routes; mobile viewport measured at 375px with no page overflow on sampled admin dashboard/room-type form |
| Backend runtime | `http://localhost:8081` (temporary audit port) | COMPLETE | API-backed admin and management pages returned data/empty/error states; `8080` was left untouched because another service owns it |
| Customer account/data | Local seeded or existing account | BLOCKED | Customer authenticated checkout/history scenarios were not re-run in this pass; no credential recorded |
| System Admin account/data | Local seeded or existing account | COMPLETE | Admin login succeeded and admin dashboard, CRUD, partner and alias routes were exercised without recording credentials |
| Property actor/context | Local owner/manager with assigned property | PARTIAL | Manager session verified dashboard, billing, rooms and property context; complete room-type/property-switch matrix remains pending |
| Frontend unit tests | `npm test -- --watch=false` | COMPLETE | Exit 0; 26 test files and 37 tests passed in 186.89s; jsdom canvas warnings only |
| Frontend production build | `npm run build` | COMPLETE | Exit 0; initial bundle 2.53MB exceeds 2.00MB budget; font-inline and CommonJS warnings recorded as performance follow-up |
| Backend regression | `.\mvnw.cmd test` | COMPLETE | Exit 0; 86 tests passed, 0 failures/errors in 6m44s |

## Public and Customer Routes

| ID | Story / Requirement | Actor | Route / Menu | Component | Primary action | API / Data | Permission / Scope | Scenarios | Status | Evidence / Gap |
|---|---|---|---|---|---|---|---|---|---|---|
| AUD-001 | US1, US2 / FR-001, FR-006, SC-001 | Public | `/` | `HomeComponent` | Discover destinations and start search | Public suggestions, destinations, property media | Public approved inventory | Primary, empty/error, responsive, keyboard | PARTIAL | Browser loaded LuxeStay shell/content at `/`; public search interaction and error branch remain unverified |
| AUD-002 | US1, US2 / FR-001, FR-006, SC-002 | Public | `/search` | `PropertySearchPageComponent` | Filter/sort/search properties | Public property search and availability | Public approved inventory | Primary, no results, API error/retry, responsive | BLOCKED | Runtime pending T011 |
| AUD-003 | US1, US2 / FR-001, FR-006 | Public | `/hotel/:id` | `HotelDetailComponent` | Review property and select available room type | Hotel detail, room types, availability, media | Public approved property | Valid/invalid ID, dates, image/API error | BLOCKED | Runtime pending T011 |
| AUD-004 | US1, US2 / FR-001, FR-006, FR-018 | Customer | `/booking/:roomTypeId` | `BookingCheckoutComponent` | Validate and submit reservation | Reservation booking contract, selected room type and stay | Authenticated customer; owned booking | Success, validation, duplicate submit, API failure | BLOCKED | Runtime/account pending T010/T012 |
| AUD-005 | US1, US2 / FR-001, FR-006 | Customer | `/profile` | `ProfileComponent` | Read/update profile | Customer profile context | Authenticated customer | Load, update validation, failure/retry | BLOCKED | Runtime/account pending T010/T012 |
| AUD-006 | US1, US2 / FR-001, FR-006 | Customer | `/booking-history` | `ProfileComponent` with `tab=bookings` | Review/cancel owned bookings | My bookings and cancellation | Customer ownership | Empty, cancel confirm, denied foreign ID | BLOCKED | Shared-component route-data behavior pending T012 |
| AUD-007 | US1, US2 / FR-001, FR-006 | Customer | `/my-invoices` | `MyInvoicesComponent` | Review owned invoices | Reservation/invoice APIs | Customer ownership | Empty, load error, invoice detail/download state | BLOCKED | Runtime/account pending T012 |
| AUD-008 | US1 / FR-001, FR-006 | Customer | `/settings` | `AccountSettingsComponent` | Update account settings/password | User profile/password APIs | Authenticated customer | Validation, submitting, success/failure | BLOCKED | Runtime/account pending T012 |
| AUD-009 | US1, US2 / FR-001, FR-006, FR-018 | Customer/demo | `/payment-simulator` | `PaymentSimulatorComponent` | Simulate supported payment flow | Payments and reservation data | Contract-dependent; reservation ownership | Success, repeat submit, invalid reservation | BLOCKED | Runtime/data pending T011/T012 |
| AUD-010 | US1, US2 / FR-001, FR-006 | Customer/demo | `/payment-result` | `PaymentResultComponent` | Understand callback result and recover | Payment result query/API | Contract-dependent | Success/failure/refresh/retry | BLOCKED | Runtime/data pending T011/T012 |
| AUD-011 | US1 / FR-001, FR-006 | Public | `/login` | `LoginComponent` | Authenticate customer/partner | Auth login/JWT | Public entry | Valid/invalid, return URL, locked user | PARTIAL | Browser verified visible labels, local background asset and no 375px overflow; submit/error/return-url branches remain unverified |
| AUD-012 | US1 / FR-001, FR-006 | Public | `/register` | `RegisterComponent` | Create customer account | Auth registration | Public entry | Validation, duplicate account, success | PARTIAL | Browser verified labels targeting existing controls, local background asset and no 375px overflow; duplicate/success branches remain unverified |
| AUD-013 | US1 / FR-001, FR-006 | Public/customer | `/partner/register` | `PartnerRegisterComponent` | Submit property partner application | Partner/property registration APIs | Contract-dependent applicant scope | Guest redirect, validation, submit failure | BLOCKED | Runtime/account pending T012 |
| AUD-014 | US1 / FR-001, FR-006 | Customer/partner | `/partner/registration-status` | `PartnerRegistrationStatusComponent` | Review own application status | Partner application status | Authenticated applicant ownership | Pending/approved/rejected/none/error | BLOCKED | Runtime/account pending T012 |
| AUD-015 | US1 / FR-001, FR-013 | Public/admin | `/admin/login` | `AdminLoginComponent` | Authenticate staff/admin and reach correct shell | Auth login/JWT/menu | Public entry; staff role after login | Valid/invalid, return route, wrong role | PARTIAL | Valid seed login reached `/admin/dashboard`; labels/local asset/no overflow verified, invalid and wrong-role branches remain unverified |
| AUD-016 | US1 / FR-001, FR-013 | Any denied actor | `/403` | `ForbiddenComponent` | Understand denial and recover safely | None | Permission denied | Direct route and guard redirect | BLOCKED | Runtime pending T017 |
| AUD-017 | US1 / FR-001, FR-013 | Public | Unknown client route `** -> /` | Router redirect | Recover from unknown public URL | None | Public | Unknown URL, no loop/blank screen | BLOCKED | Runtime pending T017 |

## Client Navigation and Shared Controls

| ID | Story / Requirement | Actor | Route / Menu | Component | Primary action | API / Data | Permission / Scope | Scenarios | Status | Evidence / Gap |
|---|---|---|---|---|---|---|---|---|---|---|
| AUD-018 | US1, US4 / FR-001, FR-013, FR-014 | Public | Header links: `/search`, `/#rooms`, `/#services`, `/#offers` | `ClientLayout` | Navigate public content with current-location clarity | DOM sections/routes | Public | Desktop/mobile, fragment target, keyboard | BLOCKED | Runtime pending T011/T036 |
| AUD-019 | US1, US4 / FR-001, FR-013 | Customer/partner | Account menu links and partner CTA | `ClientLayout` | Reach profile, trips, invoices, settings, partner/management/admin | User context/profile | Role/status-dependent | Guest/customer/owner/admin/locked | BLOCKED | Runtime/account pending T012/T017 |
| AUD-020 | US1, US4 / FR-001, FR-014 | Any | Language/currency control | `ClientLayout` | Change or understand locale/currency | No connected behavior visible in source | Public | Click, keyboard, state feedback | BLOCKED | Source suggests disconnected control; verify T011 then GAP-010 |
| AUD-021 | US1, US4 / FR-001, FR-009 | Public/customer | Chat widget | `ChatWidgetComponent` | Open chat and exchange/recover | Chat/WebSocket or configured API | Public/customer contract | Open/close, send, offline/error | BLOCKED | Runtime pending T011/T012 |

## Admin Routes

| ID | Story / Requirement | Actor | Route / Menu | Component | Primary action | API / Data | Permission / Scope | Scenarios | Status | Evidence / Gap |
|---|---|---|---|---|---|---|---|---|---|---|
| AUD-022 | US1, US2 / FR-001, FR-007 | Authorized staff | `/admin/dashboard` | `Dashboard` | Review operational KPIs | Analytics dashboard | `REPORT:VIEW`, active property/system | Load/error, denied, responsive | PARTIAL | Admin browser loaded onboarding/KPI shell and explicit empty maintenance state; content is hotel-setup oriented and denied/error branches remain unverified |
| AUD-023 | US1 / FR-001, FR-007 | Authorized staff | `/admin/profile` | `AdminProfileComponent` | Review/update staff profile | User profile | Authenticated staff | Load/update/error | BLOCKED | Runtime/account pending T013 |
| AUD-024 | US1 / FR-001, FR-007 | System/property admin | `/admin/users` | `UserManagement` | Manage staff users | Users API | `USER:VIEW` and mutations | List/search/CRUD/denied | PARTIAL | Browser loaded three seeded staff rows and opened accessible add-staff dialog; mutation/denied branches not submitted |
| AUD-025 | US1 / FR-001, FR-007 | Authorized staff | `/admin/customers` | `UserManagement` with `userType=CUSTOMER` | Manage customer records | Users API | `CUSTOMER:VIEW` and mutations | Route-data, list/search/CRUD/denied | BLOCKED | Runtime pending T013 |
| AUD-026 | US1, US2 / FR-001, FR-007 | Hotel operations | `/admin/room-types` | `RoomTypeManagement` | Manage room type inventory | Room type APIs | `ROOM_TYPE:VIEW/*`, property scope | CRUD, validation, empty/error, cross-property | PARTIAL | Browser opened create form with accessible controls, validation alert and no 375px overflow; save success/cross-property branches remain unverified |
| AUD-027 | US1, US2 / FR-001, FR-007 | Hotel operations | `/admin/rooms` | `RoomManagement` | Manage physical rooms | Room/management APIs | `ROOM:VIEW/*`, property scope | CRUD/bulk/status/error/cross-property | PARTIAL | Canonical route reached through `/admin/manage-rooms` alias and rendered seeded rooms; mutation/bulk/denied branches remain unverified |
| AUD-028 | US1, US2 / FR-001, FR-007 | Hotel operations | `/admin/services` | `ServiceManagement` | Manage hotel services | Hotel services API | `HOTEL_SERVICE:VIEW/*`, property scope | CRUD/validation/empty/error | BLOCKED | Runtime pending T014 |
| AUD-029 | US1, US2 / FR-001, FR-007 | Hotel operations | `/admin/reservations` | `ReservationManagement` | Search/manage reservation lifecycle | Reservation APIs | `RESERVATION:VIEW/*`, property scope | List/status/error/permission | BLOCKED | Runtime pending T014 |
| AUD-030 | US1, US2 / FR-001, FR-007 | Hotel operations | `/admin/reservations/timeline` | `ReservationTimelineComponent` | Review bookings on timeline | Reservation APIs | `RESERVATION:VIEW`, property scope | Empty/dense/date/error/mobile | BLOCKED | Runtime pending T014 |
| AUD-031 | US1, US2 / FR-001, FR-007, FR-018 | Hotel operations | `/admin/reservations/create` | `ReservationCreate` | Create reservation | Reservation/customer/room APIs | `RESERVATION:CREATE`, property scope | Validation/duplicate/error/success | BLOCKED | Runtime pending T014 |
| AUD-032 | US1, US2 / FR-001, FR-007 | Accountant/staff | `/admin/invoices` | `InvoiceManagement` | Review/manage invoices | Invoice/payment APIs | `INVOICE:VIEW/*`, property scope | List/detail/empty/error/permission | BLOCKED | Runtime pending T014 |
| AUD-033 | US1 / FR-001, FR-007 | System admin | `/admin/modules` | `ModuleManagementComponent` | Manage application modules/menu metadata | System/module APIs | `SYSTEM:VIEW/*` | List/edit/error/denied | BLOCKED | Runtime pending T013 |
| AUD-034 | US1 / FR-001, FR-007 | Authorized staff | `/admin/chat` | `ChatDashboardComponent` | Review/respond to conversations | Chat/WebSocket APIs | Backend-authorized staff | Empty/message/offline/error | BLOCKED | Runtime pending T013 |
| AUD-035 | US1 / FR-001, FR-007 | System/property admin | `/admin/properties` | `PropertyManagementComponent` | Manage/approve properties | Hotel/property APIs | Backend authorization; route has no explicit permission guard | CRUD/approval/denied | BROKEN | Browser rendered list shell but `Thêm mới` has no handler/dialog; create path is inert (source: property-management.html) |
| AUD-036 | US1 / FR-001, FR-007 | System admin | `/admin/plans` | `SubscriptionPlansComponent` | Manage subscription plans | Subscription plan APIs | `SYSTEM:VIEW/*` | List/edit/error/denied | BLOCKED | Runtime pending T013 |
| AUD-037 | US1 / FR-001, FR-007 | System admin | `/admin/roles` | `RoleManagementComponent` | Manage roles | Roles API | `ROLE:VIEW/*` | CRUD/system-role protection/error | BLOCKED | Runtime pending T013 |
| AUD-038 | US1 / FR-001, FR-007 | System admin | `/admin/role-permissions` | `RolePermissionComponent` | Review/update permission matrix | Role permission API | `ROLE_PERMISSION:VIEW/*` | Load/save/error/keyboard | BLOCKED | Runtime pending T013 |
| AUD-039 | US1 / FR-001, FR-007 | System admin | `/admin/property-imports` | `PropertyImportsComponent` | Review/manage property imports | Property import APIs | `PROPERTY_IMPORT:VIEW/*` | List/action/error/denied | BLOCKED | Runtime pending T013 |
| AUD-040 | US1 / FR-001, FR-007 | System admin | `/admin/property-claims` | `PropertyClaimsComponent` | Review property claims | Property claim APIs | `PROPERTY_CLAIM:VIEW/*` | List/action/error/denied | BLOCKED | Runtime pending T013 |
| AUD-041 | US1 / FR-001, FR-007 | System admin | `/admin/property-owners` | `PartnerOverviewComponent` endpoint `property-owners` | Review property owners | Generic admin partner endpoint | Backend authorization; no route guard | Schema/load/denied/action availability | BROKEN | Browser shows `Không thể tải dữ liệu` with retry; endpoint/component contract currently fails for this route |
| AUD-042 | US1 / FR-001, FR-007 | System admin | `/admin/property-registrations` | `PartnerOverviewComponent` endpoint `property-registrations` | Review accounts with listings | Generic partner endpoint | Backend authorization; no route guard | Schema/load/denied | BLOCKED | Route renders explicit empty state; seeded listing-account data is unavailable, so primary review path remains blocked |
| AUD-043 | US1 / FR-001, FR-007 | System admin | `/admin/unsubscribed-owners` | `PartnerOverviewComponent` endpoint `property-owners/unsubscribed` | Review owners without plans | Generic partner endpoint | Backend authorization; no route guard | Schema/load/denied | BLOCKED | Route renders explicit empty state; no representative unsubscribed-owner dataset available |
| AUD-044 | US1 / FR-001, FR-007 | System admin | `/admin/property-approvals` | `PartnerOverviewComponent` endpoint `property-approvals` | Review property approvals | Generic partner endpoint | Backend authorization; no route guard | Schema/action/load/denied | PARTIAL | Real table loaded one record, but headers expose raw API keys and no approval action is available in the generic view |
| AUD-045 | US1 / FR-001, FR-007 | System admin | `/admin/property-staff` | `PartnerOverviewComponent` endpoint `property-staff` | Review property staff | Generic partner endpoint | Backend authorization; no route guard | Schema/load/denied | BLOCKED | Route renders explicit empty state; representative cross-property staff data unavailable |
| AUD-046 | US1 / FR-001, FR-007 | System admin | `/admin/property-room-types` | `PartnerOverviewComponent` endpoint `property-room-types` | Review cross-property room types | Generic partner endpoint | Backend authorization; no route guard | Schema/load/denied | PARTIAL | Real table loaded three records, but generic raw API-key headers and no row actions limit review workflow |
| AUD-047 | US1 / FR-001, FR-007 | System admin | `/admin/property-rooms` | `PartnerOverviewComponent` endpoint `property-rooms` | Review cross-property rooms | Generic partner endpoint | Backend authorization; no route guard | Schema/load/denied | PARTIAL | Real table loaded nine rooms, but generic raw API-key headers and no row actions limit review workflow |
| AUD-048 | US1 / FR-001, FR-007 | System admin | `/admin/subscription-orders` | `PartnerOverviewComponent` endpoint `subscription-orders` | Review plan orders | Generic partner endpoint | Backend authorization; no route guard | Schema/load/denied | BLOCKED | Route renders explicit empty state; no representative plan-order dataset available |
| AUD-049 | US1 / FR-001, FR-007 | System admin | `/admin/subscription-payments` | `PartnerOverviewComponent` endpoint `subscription-payments` | Review plan payments | Generic partner endpoint | Backend authorization; no route guard | Schema/load/denied | BLOCKED | Route renders explicit empty state; no representative payment dataset available |
| AUD-050 | US1 / FR-001, FR-007 | System admin | `/admin/software-contracts` | `PartnerOverviewComponent` endpoint `software-contracts` | Review software contracts | Generic partner endpoint | Backend authorization; no route guard | Schema/load/denied | BLOCKED | Route renders explicit empty state; no representative contract dataset available |
| AUD-051 | US1 / FR-001, FR-013 | Admin actors | `/admin/role`, `/admin/roles-management`, `/admin/permissions/roles` | Router redirects | Reach canonical role/permission pages | None | Same as target route | Redirect, back/forward, no loop | PARTIAL | All aliases reached `/admin/roles` or `/admin/role-permissions` in admin session; denied-role branch not run |
| AUD-052 | US1 / FR-001, FR-013 | Admin actors | `/admin/room-type`, `/admin/manage-rooms` | Router redirects | Reach canonical inventory pages | None | Same as target route | Redirect and permission behavior | PARTIAL | Aliases reached `/admin/room-types` and `/admin/rooms`; permission branch remains unverified |
| AUD-053 | US1 / FR-001, FR-013 | Admin actors | `/admin/404`, unknown `/admin/**` | `NotFoundComponent` / redirect | Recover from unknown admin URL | None | Authenticated admin shell | Unknown URL, recovery action, no loop | PARTIAL | Unknown `/admin/does-not-exist` reached `/admin/404` with recovery CTA; CTA click not re-run |
| AUD-054 | US1 / FR-001, FR-013 | Admin actors | `/admin -> /admin/dashboard` | Router redirect | Reach authorized landing page | Menu/permission context | `authGuard`, target permission | Redirect with allowed/denied role | PARTIAL | Admin session redirected `/admin` to `/admin/dashboard`; denied-role branch remains unverified |

## Admin Shell and Dynamic Navigation

| ID | Story / Requirement | Actor | Route / Menu | Component | Primary action | API / Data | Permission / Scope | Scenarios | Status | Evidence / Gap |
|---|---|---|---|---|---|---|---|---|---|---|
| AUD-055 | US1, US4 / FR-001, FR-013 | Admin actors | Dynamic sidebar | `Sidebar` | Navigate only granted functions | `/api/auth/my-menu` | Backend-generated menu plus route guards | Loading/error/deduplication/active/mobile | PARTIAL | Admin shell rendered active navigation, LuxeStay branding and mobile collapse control; denied/menu-error branches remain unverified |
| AUD-056 | US1, US4 / FR-001, FR-013 | Admin actors | Global quick search | `AdminLayout` | Navigate to an existing admin screen | Static `quickLinks` | Authenticated admin | Existing/missing route, keyboard submit | BLOCKED | Source lists several non-existent routes; verify T013 then GAP-007 |
| AUD-057 | US1, US4 / FR-001, FR-009 | Admin actors | Notification panel | `AdminLayout` | Read and mark notifications | Notification API/WebSocket | Authenticated admin | Loading/empty/error/retry/realtime | BLOCKED | Runtime pending T013 |
| AUD-058 | US1, US4 / FR-001, FR-009 | Admin actors | AI assistant | `AiAssistant` | Open/use assistant or receive clear unavailable state | Assistant API/config | Authenticated admin | Open/send/error/offline | BLOCKED | Runtime pending T013 |
| AUD-059 | US4 / FR-012, FR-013 | Admin actors | Admin shell branding/avatar/footer | `AdminLayout` | Recognize consistent LuxeStay shell | Profile/avatar URL; local initials fallback | Authenticated admin | Image failure/offline/branding review | PARTIAL | Browser shows LuxeStay shell/footer and initials avatar; offline image-failure branch remains unverified |

## Management Routes and Shell

| ID | Story / Requirement | Actor | Route / Menu | Component | Primary action | API / Data | Permission / Scope | Scenarios | Status | Evidence / Gap |
|---|---|---|---|---|---|---|---|---|---|---|
| AUD-060 | US1, US2 / FR-001, FR-008 | Property owner/manager | `/management/dashboard` | `ManagementDashboardComponent` | Review active-property overview | Management context/dashboard | Assigned property only | Load/no property/error/switch property | PARTIAL | Manager session loaded property context/metrics and honest `NO_PLAN` state; no-property/switch/error branches remain unverified |
| AUD-061 | US1 / FR-001, FR-008 | Property owner/manager | `/management/properties` | `ManagementDashboardComponent` | Review assigned properties behavior | Management context | Assigned properties only | Route-data/shared component behavior | BLOCKED | Runtime pending T016 |
| AUD-062 | US1, US2 / FR-001, FR-008 | Property owner/manager | `/management/room-types` | `ManagementInventoryComponent` mode `room-types` | Manage scoped room types | Management room types | Active assigned property | CRUD/empty/error/cross-property | BLOCKED | Runtime pending T016 |
| AUD-063 | US1, US2 / FR-001, FR-008 | Property owner/manager | `/management/rooms` | `ManagementInventoryComponent` mode `rooms` | Manage scoped physical rooms | Management rooms/bulk | Active assigned property | CRUD/bulk/empty/error/cross-property | PARTIAL | Property-scoped table rendered nine seeded rooms and statuses; mutation/bulk/cross-property branches remain unverified |
| AUD-064 | US1, US2 / FR-001, FR-008 | Property owner/manager | `/management/billing` | `SubscriptionBillingComponent` | Review plan, limits and billing | Subscription APIs | Own tenant/account | Active/expired/no plan/error | PARTIAL | No-plan state rendered honestly; purchase buttons are disabled `Thanh toán online chưa hỗ trợ`, so online payment remains intentionally unsupported |
| AUD-065 | US1 / FR-001, FR-008, FR-013 | Property owner/manager | `/management/subscription -> /management/billing` | Router redirect | Reach canonical billing page | None | Authenticated property actor | Redirect/query context/no loop | BLOCKED | Runtime pending T016/T017 |
| AUD-066 | US1 / FR-001, FR-008, FR-013 | Property owner/manager | `/management -> /management/dashboard` | Router redirect | Reach management landing page | Management context | Authenticated property actor | Allowed/wrong role/no property | BLOCKED | Runtime pending T016/T017 |
| AUD-067 | US1, US4 / FR-001, FR-008, FR-013 | Property owner/manager | Management navigation and property selector | `ManagementLayout` | Switch property and retain correct route context | `/api/management/context` | Assigned properties only | Loading/error/no property/switch/forged query | BLOCKED | Runtime pending T016/T036 |

## Coverage Summary

| Inventory | Count | Current outcome |
|---|---:|---|
| Public/customer routes and controls | 21 | Sampled public shell/auth surfaces; customer account/data journeys remain blocked or partial |
| Admin routes and shell controls | 38 | Admin and all generic partner routes sampled; outcomes include partial, broken and data-blocked states |
| Management routes and shell controls | 8 | Dashboard, rooms and billing sampled with real property context; remaining context branches pending |
| Total audit items | 67 | 28 rows have updated runtime evidence in this pass; unvisited rows remain explicitly non-complete |

Final completion metrics (P1 pass rate, regression duration, breakpoint and accessibility results) are populated by T036 and T040.
