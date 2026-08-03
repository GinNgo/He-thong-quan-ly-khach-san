# T249 - System-role integrity

Date: 2026-08-04
Branch: `codex/property-operations`

## Immutable policy

The following seeded codes are governed system roles:

`SUPER_ADMIN`, `ADMIN`, `CUSTOMER`, `PROPERTY_OWNER`, `HOTEL_ADMIN`,
`HOTEL_MANAGER`, `RECEPTIONIST`, `ACCOUNTANT`.

For a governed role, `code`, `name`, `description`, the system classification and the
`ACTIVE` lifecycle state are immutable through catalog APIs. Permission-matrix
immutability remains enforced by `RolePermissionService` using the same entity policy.

The policy treats either a known seeded code or an explicit `system_role=true` flag as
authoritative. This prevents a stale false flag from turning a seeded role into an
editable custom role and also preserves legacy system roles whose code is not in the
current seed list.

## Implemented behavior

- Centralized governed codes and classification in the `Role` entity.
- Added JPA pre-persist/pre-update enforcement that repairs stale system flags and keeps
  governed roles `ACTIVE` before persistence.
- Rejected metadata updates, deactivate/reactivate actions and reuse of reserved system
  codes for custom role creation or rename.
- Projected known seeded codes as `SYSTEM` and `ACTIVE` even if legacy stored flags are
  stale, preventing unsafe controls from being rendered while startup repair runs.
- Updated `DataInitializer` and role-permission governance to use the same policy.
- Centralized the Angular system-role predicate and used it in both catalog and
  permission-matrix screens; edit/deactivate/reactivate controls are hidden and method
  guards still reject stale-flag tampering.

The catalog is global, so no property identifier is accepted or trusted. Endpoint
authorization remains `ROLE:UPDATE` for metadata/reactivation and `ROLE:DELETE` for
deactivation; system-role policy is enforced after authorization and cannot be bypassed
by a stale client payload.

## Verification

Backend focused command:

```text
backend\\mvnw.cmd "-Dtest=RoleServiceTest,RoleControllerHttpTest,RoleSystemIntegrityTest,RolePermissionServiceTest" test
```

Result: 24/24 passed (10 catalog service, 5 permission service, 6 HTTP contract,
3 entity policy).

Frontend focused command:

```text
npx ng test --watch=false --include="src/app/core/services/role.service.spec.ts" --include="src/app/features/admin/role-management/role-management.component.spec.ts"
```

Result: 9/9 passed across 2 files, including stale seeded-code classification and UI
method guards. Angular compilation also covered the shared predicate in the permission
matrix component.

Temporary subscription source snapshots/Maven test includes required by the parallel
base branch and the temporary Angular `core/i18n` junction were removed before staging.
No migration or configuration rollback applies; the change is application-level and
forward-compatible with existing `app_role` rows.
