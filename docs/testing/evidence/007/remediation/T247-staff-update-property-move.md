# T247 - Staff update, role change and property move

Date: 2026-08-04
Capability: `PROP-OPS-003`

## Implemented behavior

- Added validated `PUT /api/users/staff/{id}`, protected by `USER:UPDATE`, for staff profile, optional replacement password, active allowlisted roles and target property changes.
- Locked the staff account first and all staff assignment periods second so concurrent moves serialize in one deterministic order.
- Rejected inactive accounts/assignments, self-management, privileged targets, foreign staff and property-owner mutation of a staff account shared with an inaccessible property.
- Required a 3-500 character reason for property moves. A move closes the existing active assignment with actor/reason/timestamps and creates a new active period without deleting history.
- Resolved the destination through authenticated managed-property access and applied the destination property's entitlement lock, active staff count and `MAX_STAFF` check before inserting the new assignment.
- Kept profile, roles, default property and assignment changes in one transaction. A forced target-assignment insert failure proves the complete update rolls back.
- Prevented `PUT /api/users/{id}` from bypassing the dedicated staff validation whenever staff assignment history exists.
- Updated the Angular client and edit dialog to use the dedicated endpoint and require a property-move reason before submission.

## Focused verification

### Backend service, HTTP, concurrency, rollback and staff regression

```powershell
.\mvnw.cmd -q "-Dtest=UserServiceTest,StaffUpdateIntegrationTest,StaffUpdateConcurrencyIntegrationTest,StaffCreationIntegrationTest,StaffCreationRollbackIntegrationTest,StaffReadTenantIsolationIntegrationTest" test
```

Result: 33/33 passed, 0 failures, 0 errors.

- `UserServiceTest`: 14/14 passed.
- `StaffUpdateIntegrationTest`: 6/6 passed.
- `StaffUpdateConcurrencyIntegrationTest`: 2/2 passed.
- `StaffCreationIntegrationTest`: 7/7 passed.
- `StaffCreationRollbackIntegrationTest`: 1/1 passed.
- `StaffReadTenantIsolationIntegrationTest`: 3/3 passed.

Covered cases include same-property profile/role update, successful property move, source-history retention, destination quota scope, forbidden role, weak replacement password, missing move reason, cross-property not-found behavior, shared foreign assignment denial, generic-endpoint bypass denial, forced persistence rollback and two simultaneous moves leaving exactly one active assignment.

The base branch is missing unmerged subscription/refund/chat/notification sources owned by parallel agents. Focused backend runs temporarily supplied read-only snapshots of the existing parallel subscription catalog sources and a test-only Maven compiler include. All snapshots, repository compatibility methods and temporary build configuration were removed before staging.

### Angular typed client and edit behavior

```powershell
npm test -- --watch=false --include=src/app/core/services/user.spec.ts --include=src/app/features/admin/user-management/user-management.spec.ts
```

Result: 12/12 passed across two spec files.

The run temporarily referenced the existing parallel `core/i18n` source snapshot required by base Angular compilation. The junction was removed before staging.

## Security and data boundaries

- Tenant isolation: a non-system actor must manage the destination and have access to every active assignment of the target staff account; foreign targets return not found and no mutation occurs.
- Role authorization: only active property-staff role codes from the server allowlist are accepted.
- State validation: inactive account/assignment states cannot be mutated through the update endpoint.
- Concurrency: the pessimistic user lock serializes competing moves before assignment history is inspected or changed.
- Transactionality: a failed destination assignment insert restores the original profile, roles, default property and active source assignment.
- Session safety: a valid replacement password is encoded and revokes the current staff session through the existing revocation service.

## Migration, rollback and recovery

- Schema/configuration migration: N/A. Existing user and assignment tables plus lifecycle metadata are reused.
- Rollback: revert the T247 commit; no data conversion is required. Assignment periods already created by successful moves remain valid historical data.
- Forward recovery: preserve the dedicated endpoint and lock order when PROP-OPS-009 adds explicit mutation versions and the safe history viewer.

## Coordinator aggregation

- Mark T247 complete in `specs/007-payment-billing-completion/tasks.md`.
- Promote `PROP-OPS-003` in `docs/audit/system/MASTER_FUNCTION_INVENTORY.md`.
- Promote `PROP-OPS-003` in `docs/audit/system/FULL_SYSTEM_TRACEABILITY_MATRIX.md`.
