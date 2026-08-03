# T248 - Custom role catalog governance

Date: 2026-08-04
Branch: `codex/property-operations`

## Decision

Custom roles remain **system-global templates**. The authoritative `app_role` model has
no property ownership column, role codes are globally unique, and staff-to-property
tenant isolation is represented separately by `user_properties`. The API therefore
accepts no property identifier and continues to require `ROLE` action permissions for
catalog reads and mutations.

Introducing property-owned roles would require a separately planned schema migration,
uniqueness policy, assignment migration and cross-property authorization model. T248
does not invent that policy or silently overload the global table.

## Implemented behavior

- Added validated, metadata-only create and update requests. Clients cannot set `id`,
  `systemRole`, `status`, user counts or audit/version response fields.
- Normalized role codes to uppercase and enforced case-insensitive global uniqueness.
- Kept create lifecycle fields server-owned: every new role starts `ACTIVE` and custom.
- Locked update, deactivate and reactivate mutations with `PESSIMISTIC_WRITE`.
- Replaced physical deletion with explicit soft deactivation and safe reactivation.
- Rejected deactivation while any user is assigned to the role.
- Preserved action parity: `ROLE:DELETE` deactivates; `ROLE:UPDATE` edits or reactivates.
- Added typed Angular requests and permission-aware create/edit/deactivate/reactivate UI
  behavior with loading, success and conflict handling.

System-role metadata immutability remains the explicit scope of T249. T248 preserves the
existing seeded-code lifecycle guard without claiming that follow-up task complete.

## Verification

Backend focused command:

```text
backend\\mvnw.cmd "-Dtest=RoleServiceTest,RoleControllerHttpTest" test
```

Result: 11/11 passed (6 service, 5 HTTP/permission contract).

Frontend focused command:

```text
npx ng test --watch=false --include="src/app/core/services/role.service.spec.ts" --include="src/app/features/admin/role-management/role-management.component.spec.ts"
```

Result: 7/7 passed across 2 files.

The backend base branch referenced subscription catalog sources owned by parallel work.
The verification run used temporary local snapshots plus a focused Maven test include;
all snapshots, repository shims and compiler configuration were removed before staging.
The Angular run similarly used a temporary `core/i18n` junction, removed before staging.

## Evidence map

- Validation and server-owned fields: `RoleCreateRequest`, `RoleUpdateRequest`,
  `RoleControllerHttpTest`.
- Global uniqueness, lock and lifecycle invariants: `RoleService`, `RoleRepository`,
  `RoleServiceTest`.
- Typed UI behavior: `role.service.ts`, `role.service.spec.ts`,
  `role-management.component.ts`, `role-management.component.spec.ts`.
