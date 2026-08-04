# T235 Property Suspension, Reactivation And Closure

Date: 2026-08-04

## Scope

- Adds one locked lifecycle workflow for approved properties: `ACTIVE/APPROVED/ACTIVE` can become `SUSPENDED/APPROVED/SUSPENDED` or `CLOSED/APPROVED/CLOSED`; suspended properties can reactivate or close; closed properties are terminal.
- Requires a trimmed reason between 10 and 500 characters and persists the action, reason, authoritative actor id and timestamp on the property.
- Returns server-derived allowed transitions and exposes permissioned admin endpoints for list, suspend, reactivate and close.
- Keeps assigned owner/staff mappings active, preserves bookings and historical rows, and disables legacy hard deletion or caller-controlled lifecycle updates.
- Sends durable in-app notifications to every active assigned user and appends tenant-scoped operational audit evidence in the same transaction.
- Keeps suspended and closed properties selectable in management context while public inventory and operational actions remain blocked.
- Leaves immutable review history, owner history APIs, email/outbox delivery and retry processing to T236.

## Authorization And Isolation

- `GET /api/admin/properties/lifecycle` requires `ADMIN` or `SUPER_ADMIN` plus `PROPERTY_LIFECYCLE/VIEW`.
- Lifecycle mutations require `ADMIN` or `SUPER_ADMIN` plus `PROPERTY_LIFECYCLE/APPROVE`.
- The actor id is read only from `CustomUserDetails.userId`; request payloads cannot supply an actor or tenant id.
- The workflow obtains a pessimistic property lock before validating the source state and writing the transition.
- Exact replay requires the same final state, action, actor and reason and returns `changed=false` without duplicate audit or notification. Different replay data fails with a state conflict.
- Cross-property operational access continues to use the assigned-property boundary and requires all three canonical active states.

## Verification

1. Backend production compilation:

   - Maven production compile passed for 440 source files using target-only compatibility stubs for the unrelated base billing gap.

2. Backend focused and regression suite:

   - T235 lifecycle service, persistence, controller and legacy-bypass tests passed.
   - Property access, public inventory, registration status, submit, approval/rejection, ownership activation and notification regressions passed.
   - Result: 98 tests passed, 0 failed.

3. Frontend focused suite:

   - Property lifecycle HTTP service: 4/4 passed.
   - Admin property management: 7/7 passed.
   - Management dashboard: 4/4 passed.
   - Result: 15 tests passed, 0 failed.

4. Angular development build:

   - `ng build --configuration development`: passed.
   - Temporary compatibility-only i18n stubs were removed and are not part of this task.

5. Repository check:

   - `git diff --check`: passed.

## Data Preservation

- Persistence tests prove suspension keeps owner/staff mappings active and leaves the confirmed reservation attached to the same property.
- Audit or durable-notification persistence failure rolls the property transition back and does not delete or alter the reservation.
- Booking customers are not cancelled or notified because no refund or reaccommodation policy is defined for this task.

## Migration And Recovery

- Migration `V71__property_lifecycle_metadata_permission.sql` is additive: nullable lifecycle metadata columns, one actor foreign key, one lifecycle index and the `PROPERTY_LIFECYCLE` permission grant.
- It does not rewrite property, booking, ownership or notification rows. No production database or production credential was used.
- Safe forward recovery: apply V70 before V71, then deploy the application commit. Re-running the guarded DDL does not recreate existing columns, key, index, function or permission row.
- Rollback: revert the application commit and leave the nullable lifecycle metadata unused. Do not drop the columns during emergency rollback because that would discard lifecycle evidence.

## Baseline Constraints

- The normal Maven lifecycle remains blocked by unrelated missing `SubscriptionPlanDTO` and `SubscriptionCatalogService` sources plus unrelated test slices absent from this branch. The focused harness keeps compatibility stubs under generated `target/` only and cleans them before commit.
- The base frontend lacks two i18n services present in the coordinator worktree. They were stubbed only during verification and removed afterward.
