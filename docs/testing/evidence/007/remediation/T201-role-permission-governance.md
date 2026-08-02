# T201 - Role permission governance

## Implemented

- All governed system roles (`SUPER_ADMIN`, `ADMIN`, `CUSTOMER`, `PROPERTY_OWNER`, `HOTEL_ADMIN`, `HOTEL_MANAGER`, `RECEPTIONIST`, and `ACCOUNTANT`) are immutable in the permission editor, even if a stale database row has `system_role = false`.
- Permission updates fail closed for a null request/list, null entries, null ids/masks, duplicate function ids, unknown function ids, and action bits outside `VIEW|CREATE|UPDATE|DELETE|EXPORT|APPROVE`.
- `app_role.version` is used as the optimistic concurrency token. The API requires `expectedVersion` and returns the resulting version; stale updates raise the standard optimistic-lock error contract.
- `app_role_permission_audit` stores append-only before/after snapshots, actor id, expected version and resulting version in the same transaction.
- The Angular role permission screen disables all governed system roles and sends/refreshes the expected version returned by the backend.

## Verification

Commands run on 2026-08-02:

```text
backend\\mvnw.cmd -q "-Dtest=RolePermissionServiceTest" test
Result: PASS (5 tests, 0 failures, 0 errors in Surefire report)

backend\\mvnw.cmd -q -DskipTests compile
Result: PASS

backend\\mvnw.cmd -q "-Dtest=AdminUserControllerIntegrationTest" -DforkCount=0 test
Result: PASS (3 tests, Spring/H2 entity and repository context started successfully)

frontend\\npm run build -- --configuration development
Result: PASS
```

The focused service tests cover system-role privilege protection, malformed/duplicate/unknown masks, stale-version rejection, and successful version/audit persistence. The integration suite confirms the new versioned role and audit mappings start in the Spring/H2 context. The frontend build verifies the updated expected-version API contract and governed-role UI state.

## Residual evidence gap

An HTTP integration test should still exercise the 409 response and two simultaneous database updates against SQL Server/H2 locking semantics before this row is considered final release evidence.
