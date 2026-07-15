# Admin Core Report - 2026-07-15

## Route, menu and function audit

- Angular Admin canonical route duplicates: **0**.
- Runtime `/api/auth/my-menu`: **5 modules, 25 functions**.
- Duplicate function codes in runtime menu: **0**.
- Duplicate routes in runtime menu: **0**.
- Admin placeholders matching `works!`, `component works`, `TODO`, `Not
  implemented`, `Coming soon` or `Sắp ra mắt`: **0**.
- The authoritative Admin menu is the database response from
  `/api/auth/my-menu`. The frontend fallback contains only Dashboard/Profile and
  never merges a second static menu into the response.

Duplicates found before migration:

- `CHECKIN_OPERATION`, `IN_HOUSE_GUESTS`, `CHECKOUT_OPERATION` all used
  `/admin/reservations`.
- `HOUSEKEEPING_OPERATION`, `MAINTENANCE_OPERATION` both used
  `/admin/property-rooms`.
- `ACTIVE_SUBSCRIPTIONS` reused `/admin/property-owners`.
- Hotel services reused function code `HOTEL`; it is now `HOTEL_SERVICE`.
- Booking payment reused `FINANCE`. The unsupported payment page is hidden and
  tracked as BLOCKED instead of routing to a placeholder/404.

## Migration

Flyway `V9__admin_menu_role_inventory.sql` was applied successfully to SQL
Server, moving schema version 8 to 9. Before changing functions it created:

- `backup_app_function_v9`
- `backup_app_role_permission_v9`

Alias action masks were merged bitwise into canonical functions before alias
rows were removed. Role and room note columns were added safely. DataInitializer
continues to upsert by unique function code and no longer recreates aliases.

## Implemented behavior

### Roles

- List, client pagination/sorting, code/name search and status filter.
- Create/edit, unique normalized code, user count, role type, status and update
  timestamp.
- Soft deactivate for custom roles; system roles are protected.
- Direct system-role delete verification returned HTTP 409.

### Role permissions

- Module/function matrix for VIEW, CREATE, UPDATE, DELETE, EXPORT and APPROVE.
- Row/module/global action selection, unsaved-change indicator, reset and
  confirmation before save.
- One bitwise action-mask record per role/function; reload retained mask `5`
  (`VIEW | UPDATE`) in the real API test.
- SUPER_ADMIN matrix is read-only. A CUSTOMER calling the Role API received 403.

### Room types

- Real API list, search, property/status filters, sorting and pagination.
- Create/edit/soft deactivate, property-scoped code uniqueness, price, bed,
  area, capacity, descriptions, physical-room count and image URL management.
- API verification created RoomType `679` and later returned `INACTIVE` after
  soft delete.

### Physical rooms

- Real API list, search and property/type/floor/room/housekeeping/maintenance
  filters, sorting and pagination.
- Create/edit/change type, maintenance/open-room actions and soft deactivate.
- Bulk create validates property/type and reports duplicates separately.
- API verification created 3 rooms, reported 1 duplicate, changed maintenance
  status and safely deactivated the test rooms.

## Module status

| Module | Status | Note |
|---|---|---|
| Dashboard | PASS | Existing real dashboard retained |
| Modules/Khai báo trang | PASS | Existing API-backed module management retained |
| Roles | PASS | Core CRUD and protection verified |
| Role Permissions | PASS | Matrix/action mask/reload/403 verified |
| Users | PASS | Existing API-backed CRUD retained |
| Customers | PASS | Existing user management in CUSTOMER mode retained |
| Properties/Hotels | PARTIAL | List payload recursion and authority fixed; broader CRUD workflow remains as existing |
| Room Types | PARTIAL | Core CRUD/images/capacity complete; amenity relation is not modeled |
| Rooms | PARTIAL | Core CRUD/bulk/status complete; persisted status-history timeline is not modeled |
| Reservations | PASS | Existing operational module retained |
| Hotel Services | PASS | Function code corrected to HOTEL_SERVICE |
| Chat | PASS | Existing real module retained |
| Invoices | PASS | Existing real module retained |
| Reservation Payments | BLOCKED/HIDDEN | No dedicated API/page exists; no placeholder menu is rendered |
| Subscription Plans | PASS | Existing real module retained |
| Subscription Payments | PARTIAL | Generic API-backed overview, not full CRUD |
| Software Contracts | PARTIAL | Generic API-backed overview, not full CRUD |

## Verification

- SQL Server migration: **PASS**, schema version 9.
- Backend tests: **35/35 PASS**.
- Frontend unit tests: **20/20 PASS**.
- Angular production build: **PASS**; existing bundle/CommonJS warnings remain.
- Playwright Admin: **3/3 PASS** in the final clean run.
- Runtime menu: duplicate codes **0**, duplicate routes **0**.
- Admin source placeholder scan: **0**.

Screenshots:

- `docs/screenshots/admin-roles-loading-before.png`
- `docs/screenshots/admin-roles-after.png`
- `docs/screenshots/admin-rooms-after.png`

## Remaining work

- Add indexed server-side pagination for very large Role/RoomType/Room lists.
- Add room status-history entity/API before exposing a history action.
- Add a real RoomType amenity relation before exposing amenity CRUD/filtering.
- Build dedicated Reservation Payment reconciliation and full Subscription
  Payment/Contract CRUD; these are not represented as completed features.
