# T250 - Role and staff mutation audit/concurrency

Date: 2026-08-04
Branch: `codex/property-operations`

## Implemented behavior

- Role create, update, deactivate and reactivate requests require a 3-500 character
  audit reason. Update and lifecycle requests also require the current role version.
- Staff update, property move, deactivate and reactivate requests require the current
  user version and a reason. The API returns the version in both detailed and staff-list
  DTOs so the browser never invents concurrency state.
- Role and staff services retain their pessimistic row/assignment locking and reject a
  stale request version before mutation. Staff version checks happen only after the
  caller has passed staff/property authority checks, avoiding identifier/version leaks.
- Accepted mutations append redacted operational audit events containing actor, supplied
  reason, aggregate identity and before/after snapshots. Assignment snapshots include
  active and historical property periods without email, phone, password or token data.
- Role and staff screens collect reasons, surface stale-data failures through the existing
  error contract and expose history only when `AUDIT_LOG:VIEW` is present. History links
  pre-filter by domain, aggregate type and aggregate id; the existing audit service still
  applies system/tenant scope and property isolation on the server.

## Concurrency and isolation

- Concurrent role metadata updates using the same version serialize on the role row:
  exactly one succeeds, one receives `OptimisticLockingFailureException`, the version
  advances once and one append-only audit event is persisted.
- Concurrent staff property moves using the same version serialize on the user row and
  assignment rows: exactly one succeeds, the stale request is rejected with HTTP 409,
  and no duplicate active assignment is created.
- Existing positive, rollback, privileged-account, cross-property and hidden-resource
  staff tests remain part of the focused suite.

## Verification

Backend focused command:

```text
backend\mvnw.cmd "-Dtest=RoleServiceTest,RoleControllerHttpTest,RolePermissionServiceTest,UserServiceTest,StaffUpdateIntegrationTest,StaffUpdateConcurrencyIntegrationTest,RoleMutationConcurrencyIntegrationTest,OperationalAuditServiceTest,OperationalAuditControllerTest" test
```

Result: 50/50 passed with zero failures, errors or skipped tests, including lifecycle
HTTP validation for a missing expected version.

Frontend focused command:

```text
npm test -- --watch=false --include='src/app/core/services/role.service.spec.ts' --include='src/app/core/services/user.spec.ts' --include='src/app/features/admin/role-management/role-management.component.spec.ts' --include='src/app/features/admin/user-management/user-management.spec.ts' --include='src/app/features/admin/audit-log/audit-log.component.spec.ts'
```

Result: 28/28 passed across five files. The Angular compiler emitted only the existing
unrelated optional-chain warning in `client-layout.html`.

## Migration and recovery

No migration is required. `app_role.version`, `users.version` and the append-only
operational audit table already exist. Rollback is application-only: revert the request
contracts, service checks and UI fields together. Existing audit rows remain immutable
and readable. Temporary subscription source snapshots, Maven test includes and Angular
`core/i18n` junctions used to work around parallel-base build gaps were removed before
staging.
