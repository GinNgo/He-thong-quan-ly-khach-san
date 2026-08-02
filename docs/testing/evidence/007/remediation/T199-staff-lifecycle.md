# T199 - Staff deactivate/remove lifecycle

Date: 2026-08-02
Task: `PROP-OPS-004`

## Implemented

- Replaced physical `DELETE /api/users/{id}` behavior with reasoned lifecycle endpoints:
  - `POST /api/users/{id}/deactivate`
  - `POST /api/users/{id}/reactivate`
- Deactivation is property-scoped, requires a non-blank reason, records the actor/time/reason on the assignment, and never deletes the user or historical assignment.
- The account becomes `INACTIVE` only when no active property assignment remains; this revokes an existing JWT session on its next authenticated request through the existing status reload policy.
- Rehire creates a new active assignment row, preserving the previous employment period and its reason.
- Generic staff edits cannot silently change status or transfer a staff member between properties; lifecycle actions are required.
- Management UI shows active and historical assignments, removes the destructive delete action, and provides accessible reason/property dialogs for deactivate and rehire.
- Additive SQL Server migration `V35__staff_assignment_lifecycle_audit.sql` adds lifecycle metadata and a property/staff/status index with pre-checks. Recovery is forward-compatible: deploy the prior application version without dropping evidence columns.

## Validation

| Layer | Command | Result |
|---|---|---|
| Backend service/security | `./mvnw.cmd -q "-Dtest=UserServiceTest,AccountStatusPolicyTest,CustomUserDetailsServiceAccountStatusTest,JwtAuthFilterAccountStatusTest" test` | PASS |
| Backend compile | `./mvnw.cmd -q -DskipTests compile` | PASS |
| Frontend lifecycle component | `npm test -- --watch=false --include=src/app/features/admin/user-management/user-management.spec.ts` | PASS (2/2) |

## Safety evidence

- A last active assignment is marked `INACTIVE`, the account is locked, and `AccountStatusPolicy.requireActive` rejects the user without deleting either row.
- Deactivating one of several active assignments leaves the account active for the remaining property.
- Rehire appends a new assignment and leaves the previous inactive assignment and reason unchanged.
- Property managers cannot target an assignment outside their managed-property scope, and the UI requires a reason before submitting.
