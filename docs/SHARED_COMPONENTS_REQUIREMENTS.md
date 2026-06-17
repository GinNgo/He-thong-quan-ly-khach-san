# SHARED_COMPONENTS_REQUIREMENTS.md

## Objective

Build reusable Angular components for the entire administration system.

Do not create duplicate table, filter or form implementations.

All modules must reuse shared components.

Examples:

Room Management

Reservation Management

Invoice Management

Customer Management

User Management

Role Management

Service Management

Report Management

---

# Shared Components

## Data Table Component

Path:

shared/components/data-table

Requirements:

* Server Side Paging
* Server Side Sorting
* Multi Column Sorting
* Global Search
* Column Search
* Dynamic Columns
* Export Excel
* Export PDF
* Responsive Layout
* Loading State
* Empty State

Inputs:

columns

data

totalRecords

pageSize

permissions

Outputs:

pageChange

sortChange

filterChange

rowClick

edit

delete

view

---

## Filter Component

Path:

shared/components/filter-panel

Requirements:

* Dynamic Filters
* Keyword Search
* Date Range
* Status Filter
* Dropdown Filter
* Multi Select Filter
* Reset Button
* Search Button

Generate filter controls from configuration.

---

## Form Dialog Component

Path:

shared/components/form-dialog

Requirements:

* Create Mode
* Update Mode
* View Mode

Support:

Reactive Forms

Validation

Permission Checking

Loading State

---

## Confirm Dialog Component

Path:

shared/components/confirm-dialog

Requirements:

Delete Confirmation

Cancel Reservation

Check Out Confirmation

Custom Messages

---

## Permission Directive

Path:

shared/directives

Requirements:

*hasPermission

Examples:

*hasPermission="'ROOM_VIEW'"

*hasPermission="'ROOM_CREATE'"

*hasPermission="'ROOM_DELETE'"

Hide UI automatically.

---

## Date Picker Component

Use PrimeNG DatePicker.

Requirements:

Single Date

Date Range

Time Picker

Localization

Mobile Friendly

Reusable Wrapper Component

Path:

shared/components/date-picker

---

## Select Component

Use PrimeNG Select.

Requirements:

Searchable

Clearable

Virtual Scroll

Lazy Loading

Multi Select

Reusable Wrapper Component

Path:

shared/components/app-select

---

## Statistics Card Component

Path:

shared/components/stat-card

Requirements:

Title

Value

Icon

Trend

Percentage Change

Reusable Dashboard Widget

---

## Chart Components

Use Chart.js.

Path:

shared/components/charts

Create:

Revenue Chart

Occupancy Chart

Reservation Trend Chart

Pie Chart

Bar Chart

Line Chart

All charts must be reusable.

---

## Pagination Model

Create reusable model:

PageRequest

PageResponse

SortRequest

FilterRequest

---

## API Integration

Every page must support:

pageNumber

pageSize

sortField

sortDirection

keyword

filters

---

## UI Requirements

PrimeNG

Bootstrap 5

Responsive Design

Desktop

Tablet

Mobile

---

## Code Requirements

Angular 20+

Standalone Components

Signals

Lazy Loading

Reusable Services

Strong Typing

No duplicated UI code.

Generate production-ready code.
