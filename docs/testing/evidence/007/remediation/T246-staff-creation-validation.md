# T246 - Staff creation and property assignment

Date: 2026-08-04
Capability: `PROP-OPS-002`

## Implemented behavior

- Added validated `POST /api/users/staff`, protected by `USER:CREATE`, and stopped property staff creation through the generic user endpoint.
- Added `StaffCreateRequest` validation for username, email, full name, optional phone, one or more roles, target property and an explicit initial password of 8-256 characters.
- Removed the `123456` fallback. The administrator must supply the initial password; the UI states the policy and does not imply that a default password is generated.
- Added `GET /api/users/staff/roles`, protected by `USER:VIEW`, returning only active allowlisted property-staff roles. System/customer/owner roles are not assignable through this flow.
- Normalized usernames and email addresses with Unicode NFKC, trim and locale-stable lowercase before duplicate checks and persistence. Username/email conflicts use stable `409` codes.
- Retained the target-property entitlement lock and `MAX_STAFF` quota check. Non-system administrators must pass `PropertyAccessService.requireManagedHotel(...)`; caller-supplied property ids are never accepted as authority.
- Flushed the user before writing the staff assignment inside one transaction. A forced assignment failure proves the previously inserted user is rolled back.
- Updated the Angular dialog and typed client to load dedicated role/property options and submit the dedicated staff payload. Required password, role and property failures are rejected before HTTP submission.

## Password versus invitation decision

The current application has no staff invitation token, invitation expiry, acceptance page or invitation email lifecycle. T246 therefore uses an explicit administrator-supplied initial password governed by the existing shared password policy. Inventing a partial invitation flow would create unreachable or insecure accounts and is outside this task. A future invitation feature must be separately specified with one-time tokens, expiry, delivery, acceptance and audit behavior before replacing this contract.

## Focused verification

### Backend service, HTTP, tenant and rollback suites

```powershell
.\mvnw.cmd -q "-Dtest=UserServiceTest,StaffCreationIntegrationTest,StaffCreationRollbackIntegrationTest,StaffReadTenantIsolationIntegrationTest" test
```

Results:

- `UserServiceTest`: 13/13 passed.
- `StaffCreationIntegrationTest`: 7/7 passed after the final duplicate-email case was added and rerun.
- `StaffCreationRollbackIntegrationTest`: 1/1 passed.
- `StaffReadTenantIsolationIntegrationTest`: 3/3 passed.

Covered behavior includes successful normalized creation, password hashing, active assignment persistence, invalid/missing-strength password rejection, case-insensitive username and email conflicts, forbidden roles, cross-property denial, active allowlisted role serialization and transaction rollback when assignment persistence fails.

The base branch is missing unmerged subscription/refund/chat/notification sources owned by parallel agents. Focused backend runs temporarily supplied read-only snapshots of the existing parallel sources and a test-only Maven compiler include. All snapshots and temporary build configuration were removed before staging; none are part of T246.

### Angular typed client and dialog behavior

```powershell
npm test -- --watch=false --include=src/app/core/services/user.spec.ts --include=src/app/features/admin/user-management/user-management.spec.ts
```

Result: 9/9 passed across two spec files. The run temporarily referenced the existing parallel `core/i18n` source snapshot required by the base Angular compilation; that junction was removed before staging.

## Security and data boundaries

- Tenant isolation: non-system staff creation resolves the target through authenticated managed-property access and rejects a foreign property with `403 ACCESS_DENIED` before account persistence.
- Role authorization: only active role codes in the property-staff allowlist are accepted; system administrator, owner and customer roles are denied.
- Duplicate safety: normalized pre-checks and database uniqueness remain authoritative; stable username/email conflict responses do not expose unrelated account data.
- Transactionality: user, role links and staff assignment share one service transaction; forced assignment failure leaves no user row.
- Secrets: plaintext initial passwords are validated and immediately encoded. They are not logged or returned.

## Migration, rollback and recovery

- Schema/configuration migration: N/A. Existing user, role and assignment tables are reused.
- Rollback: revert the T246 commit to restore the previous staff-create behavior; no data conversion is required.
- Forward recovery: preserve the dedicated endpoint, normalization, role allowlist and single-transaction invariant when adding invitation or staff-update workflows.

## Coordinator aggregation

- Mark T246 complete in `specs/007-payment-billing-completion/tasks.md`.
- Promote `PROP-OPS-002` in `docs/audit/system/MASTER_FUNCTION_INVENTORY.md`.
- Promote `PROP-OPS-002` in `docs/audit/system/FULL_SYSTEM_TRACEABILITY_MATRIX.md`.
