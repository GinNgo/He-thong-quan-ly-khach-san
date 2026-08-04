# T256 - Operational policies and house rules

## Scope and result

- Branch/worktree: `codex/property-operations` in the dedicated external worktree.
- Starting commit: `69d16c0`.
- Result: property-owned localized policy versions, owner/admin lifecycle UI, public display, checkout acknowledgement and immutable reservation snapshots are implemented.
- Financial boundary: this task stores and displays cancellation wording only. It does not invent cancellation fees, refund amounts, deposit changes or other financial policy.

## Behavior evidence

- `OperationalPolicyVersion` carries `hotel_id`, monotonically increasing property-local version, draft/published state, effective window, Vietnamese/English content and optimistic row version.
- Draft creation locks the property before allocating the next version. Publishing is transactional, closes the preceding effective window and rejects an overlapping future publication.
- Published versions cannot be edited. A policy from another property returns not-found semantics after the independent assignment check.
- `propertyPolicyTenantFilter` is declared on the entity and activated for authenticated non-system requests.
- The public endpoint returns only the effective policy for an active, approved, publicly active property and localizes English with Vietnamese fallback.
- Booking creation resolves the policy again on the server for the stay date, rejects a stale supplied version and snapshots id/version/effective time plus the localized source fields into `reservations`.
- The admin property dialog and owner dashboard reuse one editor. Public property detail shows the current rules, and checkout requires acknowledgement before submitting when a policy exists.

## Automated verification

Backend focused suite:

```text
backend\mvnw.cmd "-Dtest=OperationalPolicyServiceTest,ReservationOperationalPolicySnapshotTest,TenantFilterArchitectureTest" test
```

- PASS: 8 tests, 0 failures, 0 errors, 0 skipped.
- Covers property ownership, version allocation, publish cutover, localization, cross-property IDOR, snapshot serialization/immutability and tenant-filter architecture.

Frontend policy/detail/checkout suite:

```text
npm test -- --watch=false --include=src/app/core/services/operational-policy.service.spec.ts --include=src/app/shared/components/operational-policy-editor/operational-policy-editor.component.spec.ts --include=src/app/features/client/hotel-detail/hotel-detail.component.spec.ts --include=src/app/features/client/booking-checkout/booking-checkout.component.spec.ts
```

- PASS: 15 tests across 4 files.

Frontend management surfaces:

```text
npm test -- --watch=false --include=src/app/features/admin/property-management/property-management.spec.ts
npm test -- --watch=false --include=src/app/features/management/dashboard/management-dashboard.component.spec.ts
```

- PASS: 4 admin tests and 3 owner-dashboard tests.

Build:

```text
npm run build -- --configuration development
```

- PASS: Angular development bundle generated.

SQL Server:

```text
backend\tools\operational-policy-sqlserver-validation.ps1
```

- PASS: V82 applied twice to a disposable SQL Server 2022 database.
- Verified effective-date index, reservation snapshot columns/FK, snapshot write and duplicate `(hotel_id, version_number)` rejection.
- Disposable database and container were removed by the harness.

## Parallel-work compatibility

The base branch currently references subscription catalog and public i18n sources that exist only in other task worktrees, while new unrelated test sources also reference not-yet-landed stay/chat/notification classes. Focused validation used temporary compatibility copies and a compiler test-include block solely to compile the selected T256 tests. All compatibility files, repository method additions, i18n copies and `pom.xml` changes were removed before staging; none belongs to this task.

## Migration and recovery

- V82 is additive and idempotent: it creates `property_policy_versions`, adds nullable reservation snapshot columns, and adds constraints/indexes.
- Forward recovery: correct data or application logic in a later migration; never mutate a published snapshot in place.
- Rollback for an unpromoted disposable environment: drop the reservation FK/index and nullable columns, then drop `property_policy_versions`.
- Production execution and destructive rollback are not performed by this task.
