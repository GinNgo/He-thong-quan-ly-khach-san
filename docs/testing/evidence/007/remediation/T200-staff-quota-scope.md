# T200 - Property-scoped staff quota

## Scope

T200 closes PROP-OPS-005 by applying the authoritative entitlement and usage scope to staff creation and rehire. A manager operating multiple properties cannot consume or display another property's staff quota.

## Implementation

- `UserPropertyRepository.countActiveStaffByHotelId` counts distinct active `STAFF` users for one property only.
- `UserService.createUser()` and `reactivateStaff()` resolve the requested property, lock its entitlement with `getCurrentForUpdate`, count that property, and call `checkFeatureLimitForProperty(..., "MAX_STAFF", current, 1)` before inserting the user or assignment.
- `UserService.updateUser()` no longer calls the legacy user-wide `requireFeature` check because an edit does not increase staff usage.
- `ManagementPortalService.context()` returns `usage.staff` for the selected property; the management dashboard renders it against `MAX_STAFF`.

## Verification

Commands run on 2026-08-02:

```text
backend\\mvnw.cmd -q "-Dtest=UserServiceTest,ManagementPortalServiceTest" test
Result: PASS (12 tests, 0 failures, 0 errors)

frontend\\npm test -- --watch=false --include=src/app/features/management/dashboard/management-dashboard.component.spec.ts
Result: PASS (2 tests, 0 failures, 0 errors)
```

`UserServiceTest` verifies the lock -> property count -> property quota -> user insert order and that the target-property query is used instead of the accessible-property aggregate. `ManagementPortalServiceTest` verifies selected-property `usage.staff` exposure.

## Residual evidence gap

The focused tests use Mockito. A database-backed concurrent HTTP test should be added when the operations harness is next extended to prove two simultaneous requests serialize on the platform entitlement row.
