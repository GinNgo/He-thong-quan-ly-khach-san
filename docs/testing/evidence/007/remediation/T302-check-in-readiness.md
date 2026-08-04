# T302 Check-in Readiness and Execution

Status: `PARTIAL_BLOCKED_EXTERNAL`

## Implemented

- Added a server-authoritative readiness contract with stable blocker codes, property timezone, scheduled/earliest/latest timestamps, required and assigned room counts, and policy version.
- Added a five-minute early window only for demo properties; normal properties default to zero minutes. Invalid property times fall back to configured defaults while invalid default configuration fails fast.
- Split `CHECKIN:VIEW` readiness from `CHECKIN:UPDATE` execution and added additive migration `V55` without replacing existing masks.
- Removed the generic status mutation bypass. Check-in now locks reservation, assignments and sorted property-scoped rooms, revalidates readiness, transitions rooms to `OCCUPIED`, transitions the reservation to `CHECKED_IN`, and preserves idempotent replay invariants.
- Replaced the one-click admin action with a responsive readiness workspace including retry, blockers, assignment CTA, explicit confirmation, duplicate-submit protection, stale response suppression, 409 refresh/reconfirmation, already-checked-in display and local VI/EN copy.

## Focused Verification

| Surface | Result |
|---|---|
| Backend isolated Maven: `CheckInPolicyTest`, `ReservationLifecyclePermissionMatrixTest`, `ReservationLifecycleLockingTest`, `ReservationLifecyclePropertyIdorTest` | PASS |
| Backend isolated MockMvc: `ReservationCheckInHttpContractTest` | 2/2 PASS |
| Angular: `check-in-readiness.component.spec.ts` | 5/5 PASS |
| Angular: `reservation-lifecycle.service.spec.ts` | 4/4 PASS |
| Angular: `reservation-lifecycle-permissions.spec.ts` | 7/7 PASS |
| `git diff --check` | PASS |

The normal backend worktree remains blocked by pre-existing UTF-8 BOM errors in `UserController.java` and `UserService.java`; validation used the established isolated backend snapshot and did not modify those files.

## Real Browser Gate

Three independent local browser paths were attempted on 2026-08-04:

1. The in-app Browser could not attach its webview to the local page.
2. Chrome reached the T302 build served at `http://127.0.0.1:4210`, but the application redirected to the admin login because browser authentication is origin-specific.
3. The documented legacy `admin/admin` local demo credential was rejected by the running backend. No `LUXESTAY_E2E_*` staff credentials were configured.

The live backend did return `401` for `GET /api/reservations/1/check-in-readiness`, proving the route is protected rather than public, but this is not a successful authenticated journey. T302 must remain `PARTIAL` until a receptionist/manager demo credential and suitable confirmed reservation fixture are available. No production credential or real-money action was used.
