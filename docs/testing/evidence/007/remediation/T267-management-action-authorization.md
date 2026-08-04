# T267 - Management portal action authorization

## Scope and result

- Branch/worktree: `codex/property-operations` in the dedicated external worktree.
- Starting commit: `5929c4a`.
- Result: all management endpoints and corresponding routes/controls now use action-level permissions equivalent to the direct admin APIs.

## Behavior evidence

- The 17 management endpoints declare resource/action permissions for property context/profile, room types, rooms and housekeeping completion.
- Property context/profile reads use `HOTEL:VIEW`; profile mutation uses `HOTEL:UPDATE`; resource CRUD uses matching `ROOM_TYPE` or `ROOM` actions; housekeeping completion requires `ROOM:UPDATE`.
- Existing server-side property access checks still hide cross-tenant identifiers; frontend guards do not replace backend enforcement.
- Management routes and sidebar entries require the matching view permission.
- Dashboard profile edit, room-type amenity editing, room mutations and maintenance controls disappear when the relevant action is absent.
- Stale role labels alone cannot authorize an action because `PermissionInterceptor` evaluates the function/action mask.

## Automated verification

Backend:

```text
backend\mvnw.cmd "-Dtest=ManagementPortalAuthorizationMatrixTest,TenantIsolationIntegrationTest" test
```

- PASS: 2 action-matrix tests plus 5 tenant HTTP/IDOR tests; total 7.

Frontend:

```text
npm test -- --watch=false --include=src/app/features/management/management-route-authorization.spec.ts --include=src/app/layout/management-layout/management-layout.spec.ts --include=src/app/features/management/dashboard/management-dashboard.component.spec.ts --include=src/app/features/management/inventory/management-inventory.component.spec.ts
```

- PASS: 15 tests total: route guard 1, layout/sidebar 5, dashboard 5 and inventory 4.
- Temporary compatibility sources were removed before staging.

## Migration and recovery

- Database migration: N/A.
- Forward recovery: keep direct and management API permission annotations covered by the same reflection/interceptor matrix when endpoints change.
- No production data or credentials were used.
