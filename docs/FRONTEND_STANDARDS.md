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
