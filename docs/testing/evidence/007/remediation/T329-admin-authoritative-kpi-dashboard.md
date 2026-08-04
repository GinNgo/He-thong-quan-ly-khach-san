# T329 - Authoritative Admin KPI Dashboard

Branch: `codex/cross-cutting`

## Result

`/admin/dashboard` no longer presents fixed revenue/bookings/occupancy values or the fake AI forecast. The system dashboard now reports a rolling seven-calendar-day period in `Asia/Ho_Chi_Minh` from authoritative Platform Billing and operational data, with explicit basis, scope, generation time, source watermark and reconciliation status.

## Authority and isolation

- Revenue is Platform Billing `NET`; Property Commerce money is not queried by this endpoint.
- Operational counts include only approved, active, non-demo properties.
- Occupancy uses assigned reservation rooms plus legacy direct-room reservations, excludes cancelled/expired/rejected/no-show stays and divides by non-maintenance operational rooms.
- The existing `REPORT:VIEW` interceptor remains in force and `AnalyticsService` additionally rejects any principal without `SUPER_ADMIN` or `ROLE_SUPER_ADMIN` before cross-property reads.
- Caller-supplied property ids, date ranges, recognition bases or totals are not accepted.

## Truthful UI states

- Loading is visible while report and operations data are reconciled.
- Failure clears stale cards and exposes an accessible retry action.
- A successful zero result is labelled as an empty period rather than a failed request.
- Non-zero cards and charts are rendered only from the response; the AI forecast series was removed.
- Angular zoneless callbacks explicitly trigger change detection on both success and error so the browser does not remain stuck in loading.

## Validation

| Layer | Command / method | Result |
|---|---|---|
| Focused main compilation | Java 21 `javac` with the Maven dependency classpath and source dependency set for reporting, repositories, DTO, service and controller | PASS |
| Backend unit/context | `.\\mvnw.cmd -q surefire:test '-Dtest=AnalyticsServiceTest,AnalyticsControllerTest,ChatControllerIntegrationTest'` after focused compilation | PASS, 15/15; the full Spring context parsed the new JPQL repository methods |
| Platform report reconciliation | `.\\mvnw.cmd -q surefire:test '-Dtest=PlatformRevenueReconciliationIntegrationTest'` after focused test compilation | PASS, 1/1 to one VND |
| Angular | `npm test -- --watch=false --include=src/app/core/services/analytics.spec.ts --include=src/app/features/admin/dashboard/dashboard.spec.ts` | PASS, 3/3 |
| Browser | `PLAYWRIGHT_PORT=4294 npx playwright test e2e/admin-authoritative-dashboard.spec.ts --project=chromium --workers=1` | PASS, 2/2; loading, non-zero reconciliation metadata, failure and retry were exercised |
| Production build | `npm run build` | PASS; only pre-existing payment-configuration budget and STOMP/SockJS CommonJS warnings remain |

The repository-wide Maven lifecycle remains independently blocked by pre-existing missing `SubscriptionPlanDTO` / `SubscriptionCatalogService` source dependencies outside T329. Focused compilation and executable Spring/JPA tests validate the changed backend scope without modifying that unrelated baseline.

## Evidence artifacts

- `docs/testing/evidence/007/remediation/T329-admin-authoritative-dashboard.png`
- `docs/testing/evidence/007/remediation/T329-admin-dashboard-error-retry.png`
- Surefire XML under `backend/target/surefire-reports/`

## Migration, financial policy and recovery

- Migration: N/A; T329 adds no schema or data mutation.
- Production credentials / real money: N/A; no provider call or production configuration is used.
- Financial policy: no new pricing, refund, tax or recognition policy is invented; the dashboard reuses the existing Platform Billing `NET` report contract.
- Rollback: revert the T329 commit to restore the prior endpoint/UI. Forward recovery is preferred because rollback would reintroduce hard-coded analytics and a misleading forecast.

## Coordinator updates

- Mark T329 complete in `specs/007-payment-billing-completion/tasks.md` after merging the task commit.
- Promote CROSS-018 in `docs/audit/system/MASTER_FUNCTION_INVENTORY.md`.
- Add T329 coverage to `docs/audit/system/FULL_SYSTEM_TRACEABILITY_MATRIX.md` for FR-044 / SC-013.
