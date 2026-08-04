# T301 Room Assignment Lifecycle Evidence

Status: `COMPLETE_VERIFIED`

Implementation commits:

- `bcc85e0 feat(T301): complete room assignment lifecycle`
- `ada8586 fix(T301): close room assignment safety gaps`
- `3bed3ba fix(T301): restore focus after room release`

## Implemented Boundary

- Adds dedicated permissioned assignment/reassignment and release commands backed by persistent
  mutation idempotency. Recovery suppliers replay a business result when the transaction committed
  before the idempotency record was completed.
- Retires both legacy assignment routes with `410 Gone` and a successor link so authorized callers
  cannot bypass the required reason and idempotency contract.
- Locks the reservation, active assignment rows and the sorted union of current/target rooms in a
  stable order. Room locks are property-scoped, and conflicts use immutable assignment stay
  snapshots with the legacy reservation-date fallback.
- Supports partial reassignment: unchanged assignment rows retain their original `assignedAt`, only
  removed rows are released, and only added rooms create new active rows. Legacy primary-room
  pointers are synchronized to the first active room or cleared after release.
- Requires a trimmed 3-500 character reason and appends `ROOMS_ASSIGNED`, `ROOMS_REASSIGNED` or
  `ROOMS_RELEASED` audit events with before/after room ids.
- Extends the responsive Angular picker with current-room preselection, view/update permission
  separation, explicit release confirmation, stable retry keys, `409` refresh/reconfirmation and
  localized VI/EN copy. Checked-in stays remain readable but not mutable; delayed conflict refresh
  disables all stale controls. The release alertdialog traps focus, supports Escape and restores
  focus to its trigger on cancel and to the workspace title after confirmation. Customer booking
  detail remains read-only and displays room numbers.

## Focused Validation

| Validation | Result |
|---|---|
| Backend focused JUnit suite | PASS 38/38 across HTTP serialization, persistent idempotency/recovery, legacy-route retirement, action permission, JPQL contract, H2 overlap persistence, deterministic locking/reassignment/release and tenant IDOR |
| Frontend focused Angular/Vitest suite | PASS 24/24 across typed client requests, admin view/update/state masks, picker assignment/reassignment/release/delayed-conflict/accessibility/runtime-language states and customer read-only display; final picker rerun PASS 10/10 after the focus restoration fix |
| Isolated backend main compilation | PASS; all T301 production sources compiled before the focused suites |
| `git diff --cached --check` before source commit | PASS |

The persisted H2 fixture proves that an assignment snapshot continues to conflict after reservation
dates change, while boundary-touching and released assignments do not conflict. Locking tests cover
exact replay, partial reassignment, unchanged-row preservation, release replay, stale inventory,
room-state transitions and legacy pointer synchronization.

## Commands

Backend reports were produced by the isolated Maven run and parsed from
`target/surefire-reports/TEST-*.xml`: 7 suites, 38 tests, zero failures, errors or skips.

Frontend:

```text
npm test -- --watch=false --include src/app/shared/physical-room-picker/physical-room-picker.component.spec.ts --include src/app/features/admin/reservation-management/reservation-lifecycle-permissions.spec.ts --include src/app/features/client/profile/profile-booking-read.component.spec.ts --include src/app/core/services/reservation-lifecycle.service.spec.ts
```

Result: 4 files passed, 24 tests passed.

## Baseline Isolation

The normal Maven build remains blocked before T301 sources by unrelated UTF-8 BOMs in
`UserController.java` and `UserService.java`, followed by incomplete parallel platform-billing
dependencies. The isolated backend copy removed only those unrelated blockers and limited test
compilation to the seven T301 suites.

The normal Angular graph remains blocked by unrelated missing `LocaleService` and
`PublicI18nService` modules. The focused run used the existing temporary branch copy with minimal
compile-only stubs for those services; no stub is part of the T301 commit.

## Safety

- Migration: N/A; T301 uses the existing room, assignment, audit and idempotency schema.
- Financial policy: N/A; assignment changes do not change room type, dates, quantity, price,
  deposit, charge, payment, invoice, refund or ledger state.
- Lifecycle guard: assignment mutation is limited to pre-check-in operational states.
- No production credential, provider call, database migration or real-money transaction was used.
