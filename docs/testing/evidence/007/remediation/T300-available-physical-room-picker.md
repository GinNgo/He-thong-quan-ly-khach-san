# T300 Available Physical Room Picker Evidence

Status: `COMPLETE_VERIFIED`

Implementation commit: `29338ee feat(T300): complete available physical room picker`

## Implemented Boundary

- Adds a typed, property-scoped room availability context for one reservation, including the
  room type, stay dates, required quantity, current assignment ids and deterministic candidates.
- Rejects terminal reservations and reservations that do not have exactly one bookable room type.
- Filters candidates by property, room type, physical-room state, housekeeping readiness,
  maintenance state and overlapping active assignment snapshots. Legacy assignments fall back to
  reservation dates; boundary and released assignments do not block inventory.
- Adds a dedicated `RESERVATION_ASSIGNMENT:VIEW` endpoint and preserves tenant not-found behavior
  before inventory reads.
- Adds a shared responsive Angular picker with loading, retryable error, empty, partial-shortage,
  exact-quantity and stale-response recovery states. T300 intentionally stops before assignment;
  submit/reassign/release commands remain T301.

## Focused Validation

| Validation | Result |
|---|---|
| Backend focused JUnit suite | PASS 20/20 across locking/state matrix, property IDOR, permission interceptor, typed HTTP JSON, JPQL contract and persisted H2 overlap fixture |
| Frontend focused Angular/Vitest suite | PASS 12/12 across typed client, admin permission visibility and picker UI/state behavior |
| Isolated backend main compilation | PASS; `AvailableRoomContextDTO.class` produced after removing only unrelated baseline blockers from a temporary copy |
| `git diff --cached --check` before source commit | PASS |

The H2 fixture persists active, released, boundary and unassigned room rows. It proves an active
snapshot overlap is excluded while the boundary, released and unassigned rooms remain available in
stable floor/room-number/id order. Service tests cover AVAILABLE with CLEAN/INSPECTED and reject
reserved, occupied, dirty, cleaning, maintenance, out-of-service and non-`NONE` maintenance states.

## Baseline Isolation

The normal Maven build is blocked before T300 sources by pre-existing UTF-8 BOMs in
`UserController.java` and `UserService.java`, then by incomplete parallel platform-billing source.
The isolated backend run copied the branch, removed those unrelated blockers and limited test
compilation to the six T300 suites.

The normal Angular graph is blocked by pre-existing missing `LocaleService` and
`PublicI18nService` modules referenced by parallel payment/invoice work. The focused run used a
temporary branch copy with minimal compile-only stubs for those two unrelated services; no stub is
included in the T300 commit. All T300 production and test sources were compiled by that run.

## Safety

- Migration: N/A; T300 reads existing room and assignment snapshots.
- Financial mutation: N/A; the picker does not price, charge, refund or settle a reservation.
- No production credentials, provider calls, database migration or real-money transaction was used.

