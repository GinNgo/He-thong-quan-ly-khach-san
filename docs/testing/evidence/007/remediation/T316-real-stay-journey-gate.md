# T316 - Real Stay Journey Environment Gate

Task: T316 / STAY-028  
Branch: `codex/stay-lifecycle`  
Status: `PARTIAL`

## Verified existing coverage

- `frontend/e2e/stay-checkout-invoice.spec.ts` covers the visible admin/customer flow
  from check-in through service charge, two balance payments, checkout, dirty-room state
  and final invoice. It intentionally intercepts every `/api/**` request, so it is not
  evidence against the real backend.
- `backend/tools/stay-lifecycle-sqlserver-validation.ps1` creates an isolated SQL Server
  database and runs `StayLifecycleSqlServerIT` plus `CheckoutAggregateSqlServerIT`.
  Existing evidence records assignment conflict/idempotent replay, concurrent check-in
  and checkout, invoice aggregate behavior and forced transaction rollback.
- These browser and database suites remain truthful but separate; neither is promoted
  to a complete real end-to-end claim.

## Fresh environment attempts

1. The process environment was inspected for `LUXESTAY_E2E_*` staff/customer credentials
   and deterministic property/reservation identifiers; none were configured.
2. The non-production seed users `receptionist1/receptionist1`, `manager1/manager1` and
   `customer1/customer1` were each submitted to `POST http://localhost:8080/api/auth/login`
   with a five-second request limit. All three requests timed out, so no authenticated
   role journey or fixture discovery was possible against that backend process.
3. Docker/SQL Server readiness was checked as the disposable-database route. The Docker
   server check did not return within the bounded command timeout, so starting a new
   isolated SQL Server/backend/browser stack was not safe or deterministic in this run.

The Angular Playwright web server also failed to start from its standard configuration
in the current worktree. No result from an intercepted browser suite is substituted for
the missing real-backend evidence.

## Remaining executable gap

T316 requires a dedicated test-only orchestrator that:

- starts disposable SQL Server and the real backend, then waits on explicit health checks;
- seeds stable receptionist/customer/property/reservation/room identifiers and exports
  short-lived test credentials without committing secrets;
- runs Playwright without `/api/**` interception through assignment, check-in, service,
  settlement, checkout and customer invoice views;
- proves negative permission/tenant denial, request timeout followed by idempotent retry,
  and visible state recovery after an injected transaction rollback;
- records browser trace/screenshots and database assertions from the same run.

No production credential, production database, real-money provider or destructive cleanup
was used. Schema rollback is N/A because this evidence-only task adds no migration.
