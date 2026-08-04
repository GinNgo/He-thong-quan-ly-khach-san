# T268 - Property dashboard reconciliation

## Scope and result

- Branch/worktree: `codex/property-operations` in the dedicated external worktree.
- Starting commit: `5009139`.
- Result: the management dashboard reports authoritative entitlement and usage for only the selected property, with freshness, reconciliation and stale-switch isolation.

## Behavior evidence

- `ManagementPortalService` resolves the current entitlement for the selected property instead of combining it with user-wide legacy subscription data.
- Room usage and dashboard status totals derive from one property-scoped room snapshot; the response exposes total, classified, unclassified and reconciliation state.
- Usage explicitly reports `properties = 1` and the selected-property scope.
- Response semantics include `generatedAt`, `dataStatus`, `errors` and `usageScope` so partial/error states are not mistaken for fresh complete data.
- The Angular dashboard renders freshness/error information and uses a request sequence id to ignore stale success or error responses after a property switch.

## Automated verification

Backend focused suite:

```text
backend\mvnw.cmd "-Dtest=ManagementPortalServiceTest" test
```

- PASS: 4 tests before the final assertion-only strengthening; covers count reconciliation and two-property entitlement/usage isolation.
- The final assertion verifies the already-produced status total against the same room snapshot. A subsequent full-module rerun is blocked by subscription catalog classes that live on a parallel base branch, not by dashboard compilation.

Frontend focused suite:

```text
npm test -- --watch=false --include=src/app/features/management/dashboard/management-dashboard.component.spec.ts
```

- PASS: 4 tests, including timestamp/error rendering and property-switch race isolation.

## Migration and recovery

- Database migration: N/A.
- Forward recovery: retain the prior response fields while consumers adopt the typed freshness/reconciliation fields.
- No production data, credential or destructive operation was used.
