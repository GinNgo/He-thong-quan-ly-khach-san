# T245 - Staff list and tenant-scoped read

Date: 2026-08-04
Capability: `PROP-OPS-001`

## Implemented behavior

- Added `GET /api/users/staff`, protected by `USER:VIEW`, with a dedicated staff-screen DTO instead of the general account/SaaS DTO.
- Staff rows are selected from property assignments and filtered to the authenticated user's operational property scope; a system administrator can read all property staff.
- Assignment history and the legacy default property are filtered before serialization, so a multi-property staff account does not disclose a foreign property's id or name.
- Added `GET /api/users/staff/properties`, also protected by `USER:VIEW`, returning only `{id, name}` options authorized for the current user.
- Updated `/admin/users` to use the authenticated staff/property endpoints. It no longer obtains management options from the public property-search API.

## Focused verification

### Backend HTTP tenant and privacy suite

```powershell
.\mvnw.cmd "-Dtest=StaffReadTenantIsolationIntegrationTest" test
```

Result: `3/3` passed.

- A manager assigned to two properties sees staff from both properties.
- A foreign property's staff account is absent from the list and returns `404` through the detail route.
- A staff member with local and foreign assignment history exposes only the local assignment.
- Staff payloads omit profile/subscription fields that the screen does not consume.
- Property options contain only authorized properties and serialize only `id` and `name`.

The base branch was temporarily missing unmerged subscription/refund/chat compile dependencies owned by parallel agents. The focused run supplied read-only snapshots of those files and a test-only compiler include; none of those temporary files or configuration changes are part of T245.

### Existing staff service regression suite

```powershell
.\mvnw.cmd "-Dtest=UserServiceTest" surefire:test
```

Result: `11/11` passed, covering existing staff quota, scope, lifecycle and property-move protections.

### Angular behavior and HTTP client suites

```powershell
npm test -- --watch=false --include=src/app/core/services/user.spec.ts --include=src/app/features/admin/user-management/user-management.spec.ts
```

Result: `5/5` passed.

## Files requiring coordinator aggregation

- Mark T245 complete in `specs/007-payment-billing-completion/tasks.md`.
- Promote `PROP-OPS-001` in `docs/audit/system/MASTER_FUNCTION_INVENTORY.md`.
- Promote `PROP-OPS-001` in `docs/audit/system/FULL_SYSTEM_TRACEABILITY_MATRIX.md`.
