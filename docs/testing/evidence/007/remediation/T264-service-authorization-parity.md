# T264 - Service authorization parity

## Scope and result

- Branch/worktree: `codex/property-operations` in the dedicated external worktree.
- Starting commit: `e3cf6b2`.
- Result: the service route, UI permission model and every `/api/services` endpoint now share `HOTEL_SERVICE` action semantics.

## Behavior evidence

- List and detail require `HOTEL_SERVICE:VIEW`.
- Create requires `HOTEL_SERVICE:CREATE`; update requires `HOTEL_SERVICE:UPDATE`; delete requires `HOTEL_SERVICE:DELETE`.
- Legacy `HOTEL` permission no longer grants service catalog access.
- `PermissionInterceptor` still performs backend enforcement; frontend controls remain defense-in-depth only.
- Existing property authorization and cross-tenant not-found behavior in `HotelServiceLogicImpl` remain unchanged.

## Automated verification

```text
backend\mvnw.cmd "-Dtest=HotelServiceAuthorizationParityTest,HotelServiceLogicImplTest" test
```

- PASS: 10 tests, 0 failures, 0 errors, 0 skipped.
- Covers controller annotation parity, correct-action allow, wrong-function denial, insufficient-action denial and the existing tenant-safe service regression.

## Migration and recovery

- Database migration: N/A.
- Forward recovery: restore the same `HOTEL_SERVICE` function/action annotations if a later controller refactor drops them.
- Rollback would restore the broken UI/API mismatch and is not recommended.
