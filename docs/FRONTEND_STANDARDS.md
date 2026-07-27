# FRONTEND_STANDARDS.md

## Admin UI Requirements

The administration portal must provide reusable components and consistent user experience across all modules.

---

## Technology Stack

Angular 20+

PrimeNG

Bootstrap 5

ngx-translate

Chart.js

PrimeFlex

---

## Shared Components

Create reusable components.

### Data Table Component

Features:

* Server Side Paging
* Server Side Sorting
* Global Search
* Column Search
* Multi Column Sort
* Export Excel
* Export PDF
* Column Visibility
* Responsive Layout

Component:

shared/components/data-table

---

### Filter Panel Component

Features:

* Collapse / Expand
* Keyword Search
* Status Filter
* Date Range Filter
* Dynamic Filter Controls

Component:

shared/components/filter-panel

---

### Confirm Dialog Component

Used for:

* Delete
* Cancel Reservation
* Check Out

Component:

shared/components/confirm-dialog

---

### Permission Directive

Examples:

*hasPermission="'ROOM_VIEW'"

*hasPermission="'ROOM_CREATE'"

*hasPermission="'ROOM_DELETE'"

Hide UI automatically.

---

## Pagination Standard

Every list page must support:

Page Number

Page Size

Total Records

Sort Column

Sort Direction

Keyword Search

Response:

{
"items": [],
"pageNumber": 1,
"pageSize": 20,
"totalItems": 100,
"totalPages": 5
}

---

## Table Standard

All management screens must use the same table component.

Examples:

Room Management

Customer Management

Reservation Management

Invoice Management

User Management

Role Management

Service Management

---

## Date Components

Use modern date controls.

Preferred:

PrimeNG DatePicker

Features:

* Single Date
* Date Range
* Time Selection
* Localization
* Mobile Friendly

Examples:

Check-in Date

Check-out Date

Invoice Date

Report Filter

---

## Select Components

Do not use native HTML select.

Preferred:

PrimeNG Select

Features:

* Searchable
* Clearable
* Virtual Scroll
* Lazy Load
* Multi Select

Examples:

Role Selection

Room Type

Status

Customer

Service

---

## Dashboard Components

Create reusable dashboard widgets.

### Statistic Card

Display:

* Total Rooms
* Occupied Rooms
* Revenue
* Reservations

---

### Revenue Chart

Library:

Chart.js

Display:

Revenue by Month

---

### Occupancy Chart

Display:

Occupancy Rate

---

### Reservation Trend Chart

Display:

Reservation Trend

---

### Room Type Distribution

Display:

Pie Chart

---

## Form Standards

All forms must support:

Validation

Error Message

Loading State

Readonly State

Permission Check

---

## Admin Layout

Structure:

Sidebar

Topbar

Breadcrumb

Content Area

Footer

---

## Responsive Rules

Desktop

Tablet

Mobile

All pages must be responsive.

Minimum Width:

320px

---

## Theme

Use UI_COLOR_SYSTEM.md

Do not use random colors.

Use design style:

Booking.com

Airbnb

Modern SaaS Dashboard

Enterprise Administration System

---

## Code Standards

Use standalone components.

Use lazy loading.

Use feature modules.

Use reusable services.

Avoid duplicate UI code.

---

## Route-Level Bundle Boundaries

- Route shells and leaf pages must use `loadComponent` unless measured startup behavior requires eager loading.
- Shared global CSS such as Bootstrap and PrimeIcons must be registered once; do not import the same stylesheet from both `angular.json` and `styles.css`.
- External font declarations belong in the global stylesheet and must not be repeated in component CSS.
- CommonJS-only realtime dependencies must stay behind a lazy route boundary and be documented in build evidence; do not hide optimization warnings without recording the package and reason.
- Production initial bundles must remain below the configured Angular budget, and route-boundary changes require unit, build and representative browser navigation checks.

---

## Angular Zoneless Async State

Angular 22 của dự án chạy không khai báo `zone.js`. Component cập nhật state trong `HttpClient`, WebSocket hoặc callback bất đồng bộ phải dùng một trong hai pattern:

1. Signal/computed state trong template; hoặc
2. Inject `ChangeDetectorRef` và gọi `markForCheck()` sau mọi nhánh `next`, `error`, `complete` làm thay đổi view state.

Mọi async page phải thoát khỏi loading ở cả success và error. Retry button phải gọi lại cùng request contract và có `aria-live`/`role="alert"` phù hợp.

## Canonical Navigation

- Menu động, quick search, breadcrumb và page title chỉ dùng route có trong `app.routes.ts`.
- Route alias phải redirect tới route canonical; không để search/menu dẫn tới admin 404.
- Permission denial phải tới `/403` hoặc state giải thích rõ, không blank screen hoặc redirect loop.

## Honest Feature States

- Không dùng `confirm()`/`alert()` để mô phỏng payment, subscription hoặc mutation chưa nối API.
- Chức năng chưa có contract thật phải disabled với nhãn “Chưa hỗ trợ” hoặc ghi `MISSING/PARTIAL` trong audit.
- Không dùng dữ liệu mock hoặc UI giả làm bằng chứng hoàn thiện.
