# T233 Owner Draft Submission

Date: 2026-08-04

## Scope

- Adds an explicit property approval workflow for an owner to submit a draft property for review.
- Keeps the legacy hotel submit route compatible while exposing the authenticated partner route used by the registration-status page.
- Changes `status` and `approvalStatus` to `PENDING_APPROVAL` and keeps `operationStatus` at `INACTIVE` in one transaction.
- Removes unsubmitted DRAFT rows from the admin approval queue; a row becomes actionable only after the transition succeeds.
- Returns a typed transition response with the authoritative actor id and audit timestamp.

## Authorization and isolation

- Both submit routes require an authenticated `CustomUserDetails` principal and use only `userId` from that principal.
- Submission requires the exact requester/property `OWNER` mapping in `PENDING` state.
- Property and owner mapping reads use pessimistic write locks; cross-account property ids fail closed as not found.
- The workflow accepts only the exact `DRAFT` / `DRAFT` / `INACTIVE` state and rejects repeated or inconsistent transitions without mutation.
- The audit event is tenant-scoped to the property and stores the requester as the actor in the same transaction.

## Verification

1. Backend isolated focused harness:

   - Production compilation: 432 source files passed using target-only compatibility stubs for the unrelated base billing gap.
   - `PropertyApprovalWorkflowServiceTest`: 6 passed.
   - `PropertyApprovalWorkflowPersistenceTest`: 2 passed.
   - `PropertyRegistrationControllerTest`: 13 passed.
   - `HotelControllerIntegrationTest`: 2 passed.
   - T230-T232 registration/status regressions: 23 passed.

   Result: 46 tests passed, 0 failed. Coverage includes the positive transition, repeated/inconsistent state rejection, principal-id authority, cross-account IDOR protection, actor/time audit evidence, audit-failure rollback and admin queue visibility.

2. Frontend focused Angular harness:

   - `partner-registration-status.component.spec.ts`: 7 passed.
   - `partner-registration-status.service.spec.ts`: 2 passed.

   Result: 9 tests passed, 0 failed. Coverage includes exact raw-state action visibility, typed submit contract, per-property busy/double-submit protection, successful refresh with mixed rows and safe retryable error handling.

3. Repository check:

   `git diff --check`

   Result: passed.

## Baseline build constraint

The normal Maven lifecycle remains blocked by the unrelated base gap where `PlatformBillingController` references absent `SubscriptionPlanDTO` and `SubscriptionCatalogService`. The backend harness used target-only compatibility stubs, compiled the required production and test slices, executed all focused tests and cleaned generated artifacts. The normal Angular builder remains blocked by unrelated missing base i18n services; the focused Angular harness compiled and executed only the T233 status component and service tests.

## Migration and rollback

- Migration: N/A; this task is schema-neutral.
- Rollback: revert the task commit to restore the previous submit behavior and queue filter. Properties already submitted should remain in review or be moved through the governed approval lifecycle rather than edited directly.
