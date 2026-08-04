# T234 Admin Property Approval And Rejection

Date: 2026-08-04

## Scope

- Extends the existing owner-submit workflow into the single authoritative submit/approve/reject state machine.
- Approve changes the property to `ACTIVE` / `APPROVED` / `ACTIVE` and activates the exact pending owner mapping.
- Reject changes the property to `REJECTED` / `REJECTED` / `INACTIVE`, deactivates the pending owner mapping and persists the validated reason.
- Persists submit/review actor ids, timestamps and review reason on the property.
- Restricts the admin queue to the exact `PENDING_APPROVAL` state and leaves imported-property claims to their separate lifecycle.
- Sends a durable targeted owner notification and appends tenant-scoped audit evidence in the same transaction.
- Requires all three canonical active states for public inventory and operational management access.
- Replaces the admin prompt flow with typed approval DTOs and an inline validated rejection editor.

## Authorization And Isolation

- The admin approval controller requires `ADMIN` or `SUPER_ADMIN`; legacy hotel decision routes remain `SUPER_ADMIN` only.
- Reviewer identity comes only from the authenticated `CustomUserDetails.userId`; no caller-supplied reviewer id is accepted.
- The workflow locks the property and owner mappings and requires exactly one `PENDING` `OWNER` mapping before a decision.
- Missing, duplicate or inconsistent mappings fail closed without partial property, role, notification or audit mutation.
- Public and management reads require `status=ACTIVE`, `approval_status=APPROVED` and `operation_status=ACTIVE`.
- Audit scope is the affected tenant/property, and the durable notification targets only the approved or rejected owner.

## Verification

1. Backend isolated focused harness:

   - Production compilation: 435 source files passed using target-only compatibility stubs for unrelated base billing gaps.
   - `AdminPartnerControllerTest`: 4 passed.
   - `HotelControllerIntegrationTest`: 6 passed.
   - `PropertyRegistrationControllerTest`: 13 passed.
   - `NotificationServiceTest`: 3 passed.
   - `PropertyAccessServiceTest`: 7 passed.
   - `PropertyApprovalWorkflowPersistenceTest`: 5 passed.
   - `PropertyApprovalWorkflowServiceTest`: 9 passed.
   - `PropertyOwnershipLifecycleServiceTest`: 4 passed.
   - Registration rollback/service/status regressions: 23 passed.
   - `PublicInventoryEligibilityPolicyTest`: 4 passed.

   Result: 78 tests passed, 0 failed. Coverage includes role denial, authoritative reviewer identity, exact state transitions, reason validation, duplicate-owner fail-closed behavior, role activation, transaction rollback, targeted durable notification, tenant audit, public visibility and management access.

2. Frontend focused Angular harness:

   - `partner-overview.component.spec.ts`: 7 passed.
   - `property-approval-workflow.service.spec.ts`: 3 passed.

   Result: 10 tests passed, 0 failed. Coverage includes the typed queue, approve/reject HTTP contracts, permission-safe action visibility, inline reason validation, busy-state protection, success refresh and retryable failures.

3. Angular development build:

   - `ng build --configuration development`: passed.
   - Temporary compatibility-only i18n stubs were removed after verification and are not part of this task.

4. Repository check:

   - `git diff --check`: passed.

## Baseline Build Constraints

The normal Maven lifecycle remains blocked by unrelated base and parallel-branch gaps, including absent legacy subscription DTO/catalog classes and test sources that reference checkout, refund, chat and subscription classes not present on this branch. The focused harness compiled only the required production and test slices and executed the complete T230-T234 regression set. The base frontend is missing two i18n services already present in the coordinator worktree; temporary local stubs were used only for verification and then deleted.

## Migration And Recovery

- Migration: `V60__property_approval_review_metadata.sql` adds nullable submit/review metadata columns only; it does not rewrite existing property rows.
- Safe forward recovery: deploy the additive migration before the application commit. Existing legacy pending rows remain reviewable without submit metadata.
- Rollback: revert the application commit and leave the nullable columns unused. Do not drop the columns during an emergency rollback because that would discard review evidence.
