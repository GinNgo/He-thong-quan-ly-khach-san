# US1 Permission Foundation Evidence

## Verified

- Backend recognizes `TASK_EXECUTE=64` while preserving existing bit values.
- Backend rejects dependent actions without `VIEW`.
- Backend rejects actions outside a function's supported mask.
- Frontend automatically adds `VIEW` when enabling a dependent action.
- Frontend clears the full mask when `VIEW` is disabled.
- Frontend refuses unsupported action toggles.

## Commands

```powershell
Set-Location backend
.\mvnw.cmd -q '-Dtest=RolePermissionServiceTest,PermissionInterceptorTest' test

Set-Location ..\frontend
npm test -- --watch=false --include=src/app/features/admin/role-permission/role-permission.component.spec.ts
```

## Remaining US1 verification

- Request-fresh revocation integration test against a running backend/database.
- Cross-property IDOR matrix for all five roles.
- Complete route/menu/action inventory remediation.
- Full browser journey and final-worktree regression run.

