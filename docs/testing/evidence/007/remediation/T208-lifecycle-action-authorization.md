# T208 Lifecycle Action Authorization Evidence

Date: 2026-08-03
Branch: `codex/ui-functional-audit-polish`
Scope: STAY-011 action-level reservation lifecycle authorization

## Implementation

- Added independent permission functions for physical-room assignment, operational cancellation and no-show; check-in now uses the existing dedicated `CHECKIN` function.
- Assignment/readiness, check-in, operational cancellation and no-show endpoints use exact `@Permission` masks. The legacy checkout route now requires `CHECKOUT:CREATE`.
- `PUT /api/reservations/{id}/status` remains available for non-sensitive operational updates but rejects `CANCELLED`, `NO_SHOW`, `CHECKED_IN` and `CHECKED_OUT`, preventing the generic path from bypassing command permissions.
- Reservation lifecycle services keep the property access check ahead of every domain write. Cross-property attempts retain not-found semantics to avoid IDOR enumeration.
- Migration `V42__reservation_lifecycle_permissions.sql` adds the functions and default masks for system administrators, owners, hotel administrators/managers and receptionists; accountant access is not granted.
- The Angular reservation screen renders and disables lifecycle actions from exact permission masks and calls dedicated client endpoints with duplicate-action protection.

## Verification

```powershell
Set-Location backend
.\mvnw.cmd -q '-Dtest=ReservationLifecyclePermissionMatrixTest,ReservationLifecyclePropertyIdorTest,ReservationServiceTest' test

Set-Location ..\frontend
npx ng test --watch=false --include='src/app/core/services/reservation-lifecycle.service.spec.ts' --include='src/app/features/admin/reservation-management/reservation-lifecycle-permissions.spec.ts'
```

## Results

| Suite | Result |
|---|---:|
| `ReservationLifecyclePermissionMatrixTest` | 5/5 passed |
| `ReservationLifecyclePropertyIdorTest` | 4/4 passed |
| `ReservationServiceTest` | 15/15 passed |
| Angular lifecycle client and permission suites | 3/3 passed |

The backend matrix proves exact-mask allowance, missing-mask denial, super-admin bypass, dedicated command delegation and generic-path rejection. The property matrix executes all four sensitive commands against a foreign property and verifies rejection before assignment, room or refund writes. The Angular suites prove hidden denied controls, dedicated endpoint selection and visible authorized actions.
