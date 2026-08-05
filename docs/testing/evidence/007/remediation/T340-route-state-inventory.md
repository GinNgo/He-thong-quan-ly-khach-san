# T340 route-state inventory

Inventory date: 2026-08-04

Legend: `STANDARD` uses an explicit loading outcome plus visible error/empty guidance where data can be absent; `FORM` uses busy, field/global error and safe resubmit behavior; `STATIC` has no remote data state; `REDIRECT` delegates to a canonical route.

## Public and customer routes

| Routes | Policy | Evidence / owner |
|---|---|---|
| `/`, `/search`, `/hotel/:id` | `STANDARD` | Home/search/detail component suites and public browser journeys |
| `/booking/:roomTypeId` | `FORM` | Booking checkout busy/error/idempotent retry suites |
| `/profile`, `/booking-history`, `/refunds`, `/my-invoices`, `/notifications`, `/settings` | `STANDARD` | Customer profile/refund/invoice/notification/settings component and browser suites |
| `/payment-simulator`, `/payment-result` | `FORM` | Explicit invalid/processing/success/failure presentation; payment browser suites |
| `/forgot-password`, `/reset-password`, `/verify-email`, `/login`, `/register`, `/admin/login` | `FORM` | Auth component/browser suites |
| `/partner/register`, `/partner/registration-status` | `FORM` / `STANDARD` | Visible submit/loading/error and registration status outcomes |
| `/403` | `STATIC` | Dedicated forbidden route |

## Administration routes

| Routes | Policy | Evidence / owner |
|---|---|---|
| `/admin/dashboard`, `/admin/platform-revenue`, `/admin/platform-refunds` | `STANDARD` | T329/T335 reporting and refund suites |
| `/admin/users`, `/admin/customers`, `/admin/room-types`, `/admin/rooms`, `/admin/services` | `STANDARD` | CRUD screens retain loading/empty/error/toast behavior; domain component suites |
| `/admin/reservations`, `/admin/refunds`, `/admin/reservations/timeline`, `/admin/reservations/create` | `STANDARD` / `FORM` | Reservation lifecycle and timeline suites |
| `/admin/invoices` | `STANDARD` | T340 adds loading/error/empty/retry plus invoice-action feedback |
| `/admin/modules`, `/admin/roles`, `/admin/role-permissions` | `STANDARD` / `FORM` | System/role component feedback and mutation messages |
| `/admin/chat` | `STANDARD` | T325-T339 queue, history, attachment and audit state suites |
| `/admin/properties`, `/admin/platform-payment-configuration` | `STANDARD` / `FORM` | Property/payment configuration suites |
| `/admin/plans` | `STANDARD` | T340 adds independent catalog and account-assignment loading/error/empty/retry states |
| `/admin/audit-log`, `/admin/financial-audit` | `STANDARD` | Operational and financial audit component/browser suites |
| `/admin/property-imports` | `STANDARD` / `FORM` | T340 adds batch/item loading/error/empty/retry and visible mutation failures |
| `/admin/property-claims` | `STANDARD` / `FORM` | T340 adds queue loading/error/empty/retry and visible approve/reject failures |
| `/admin/property-owners`, `/admin/property-registrations`, `/admin/unsubscribed-owners`, `/admin/property-approvals`, `/admin/property-staff`, `/admin/property-room-types`, `/admin/property-rooms`, `/admin/subscription-orders`, `/admin/subscription-payments`, `/admin/software-contracts` | `STANDARD` | Shared `PartnerOverviewComponent` loading/error/empty component coverage |
| `/admin/profile` | `FORM` | Profile busy/error feedback |
| `/admin/404` | `STATIC` | Dedicated not-found route |
| `/admin/role`, `/admin/roles-management`, `/admin/permissions/roles`, `/admin/room-type`, `/admin/manage-rooms`, `/admin` | `REDIRECT` | Canonical route owns presentation |

## Management routes

| Routes | Policy | Evidence / owner |
|---|---|---|
| `/management/dashboard`, `/management/properties` | `STANDARD` / `FORM` | T332/T333 component and browser coverage |
| `/management/room-types`, `/management/rooms` | `STANDARD` / `FORM` | Inventory route uses shared feedback state and T337 export coverage |
| `/management/payment-configuration` | `STANDARD` / `FORM` | Property payment readiness/configuration suites |
| `/management/property-revenue` | `STANDARD` | T334/T336 report/export state coverage |
| `/management/audit-log`, `/management/financial-audit` | `STANDARD` | Authorized audit viewer suites |
| `/management/billing` | `STANDARD` / `FORM` | Catalog, entitlement, policy and payment-panel state coverage |
| `/management/subscription`, `/management` | `REDIRECT` | Canonical route owns presentation |

## T340 closure scan

- Routed console-only gaps found and remediated: `/admin/property-claims`, `/admin/property-imports`, `/admin/plans` and `/admin/invoices`.
- Remaining feature `console.error` calls accompany existing visible user-facing errors or belong to the unused legacy `InvoiceManagementComponent`; no canonical route relies on console output as its only failure presentation.
- New browser coverage executes all four remediated routes and proves error presentation, retry recovery and empty guidance.
