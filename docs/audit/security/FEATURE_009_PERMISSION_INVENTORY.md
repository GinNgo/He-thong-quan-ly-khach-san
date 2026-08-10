# Feature 009 Permission Inventory

## Existing foundations

- Backend action mask: `backend/src/main/java/com/hotel/security/ActionCode.java`.
- Function registry: `backend/src/main/java/com/hotel/security/FunctionCode.java` and `app_function`.
- Enforcement: `PermissionInterceptor` reads request authentication permission masks.
- Permission persistence: `RolePermission` with optimistic versioning and append-only `RolePermissionAudit`.
- Tenant scope: `PropertyAccessService` and Hibernate tenant filters on property-owned entities.
- Frontend presentation: `PermissionService`, `permissionGuard`, admin/management layouts and role-permission editors.

## Required convergence

- Add `TASK_EXECUTE = 64` consistently in backend and frontend.
- Add function-level supported-action metadata and validate masks against it.
- Require `VIEW` for every dependent action.
- Preserve request-fresh backend enforcement; refresh stale frontend context without redirect loops.
- Review hard-coded Admin/Super Admin bypass semantics against property access rules.
- Inventory route/menu/action mappings and correct endpoints whose CRUD annotation uses the wrong action.
- Reclassify operational transitions from generic `UPDATE`/`APPROVE` to `TASK_EXECUTE` where appropriate.
- Audit role membership and property assignment changes as well as role permission snapshots.

