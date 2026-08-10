# Quickstart Validation Guide: Role Permissions and VNPay

This guide validates Feature 009 without enabling production payments or using real money.

## Prerequisites

- Java 21, Maven, Node/npm and Docker.
- Isolated SQL Server 2022 test database.
- Deterministic users for Customer, Admin, Manager, Accountant and Receptionist across two properties.
- VNPay simulator or official sandbox credentials only; production flags remain disabled.
- Review [data-model.md](./data-model.md) and [role-payment-api.md](./contracts/role-payment-api.md).

## 1. Record the baseline

```powershell
git status --short --branch
git diff --stat
```

Do not reset, clean or overwrite unrelated worktree changes.

## 2. Build and run current tests

```powershell
Set-Location backend
.\mvnw.cmd test
.\mvnw.cmd package -DskipTests

Set-Location ..\frontend
npm test -- --watch=false
npm run build
npx playwright test --list
```

Record existing failures before implementation. Final evidence must come from a fresh final-worktree run.

## 3. Validate clean and upgrade migrations

Run all Flyway migrations against:

1. A clean SQL Server database.
2. An upgrade fixture containing existing action masks, role permissions, property assignments and payment attempts.

Expected outcomes:

- Existing bit values remain unchanged.
- `TASK_EXECUTE` and supported-action metadata backfill deterministically.
- No invalid mask is silently discarded.
- Permission-version, task and reconciliation constraints/indexes succeed.
- No financial or audit evidence is deleted.

## 4. Validate permission dependencies and revocation

1. Give Receptionist `VIEW | CREATE | UPDATE | TASK_EXECUTE` for reservations.
2. Verify the menu, route, list, create/edit controls and permitted transition.
3. Attempt to save `UPDATE` without `VIEW`; expect validation failure.
4. Keep the receptionist session open, revoke `UPDATE` and `TASK_EXECUTE`, then submit both requests without logging out.
5. Verify both fail with `403`, no data changes, and audit/version evidence exists.
6. Restore only `VIEW`; verify read-only behavior in UI and direct API calls.

## 5. Validate the five default roles

For each role, execute at least one allowed and one denied action:

- Customer: own booking/payment/invoice allowed; other customer's data denied.
- Admin: platform catalog/roles/audit allowed; property finance denied unless explicitly granted.
- Manager: assigned-property resources and package purchase allowed; other property denied.
- Accountant: finance/reconciliation/export allowed; reservation operations and staff administration denied by default.
- Receptionist: reservation/check-in/check-out task execution allowed; role/package/platform reporting denied.

## 6. Validate task revocation and concurrency

1. Assign one operational task to a receptionist.
2. Revoke `TASK_EXECUTE` while the task is assigned.
3. Verify execute fails, assignment/history remain, and an authorized manager can reassign it.
4. Submit two concurrent completion requests with the same idempotency key; verify one domain effect and one completed history transition.
5. Repeat with different keys against the same version; verify one wins and the other receives a conflict/already-completed result.

## 7. Validate booking payment through VNPay

1. Create a valid booking in Property A.
2. Create a VNPay attempt without supplying an authoritative amount.
3. Verify redirect environment, reference, amount and expiry match server data.
4. Complete a sandbox/simulator payment and deliver the signed callback/IPN.
5. Replay and concurrently deliver the callback.
6. Verify exactly one successful property financial effect and correct booking status.

Negative cases: wrong signature, terminal code, amount, currency, reference, environment, expired/cancelled booking and Property B configuration must activate nothing and create appropriate audit/reconciliation evidence.

## 8. Validate subscription payment through VNPay

1. Use a Manager/owner with package `VIEW | TASK_EXECUTE` for Property A.
2. Create a subscription order and record its plan price/duration/feature snapshot.
3. Change the catalog after order creation and verify the pending order remains unchanged.
4. Pay through the platform VNPay sandbox configuration.
5. Replay/concurrently deliver the callback.
6. Verify exactly one platform payment effect and one entitlement/contract history application.

Negative cases: missing package permission, Property B access, wrong platform merchant, client-modified price, failed/cancelled/expired order and property callback endpoint must activate zero entitlement.

## 9. Validate reconciliation separation

Create one booking transaction and one subscription transaction for the same property owner.

- Property reconciliation contains only booking/property money and is property-scoped.
- Platform reconciliation contains only subscription money and requires platform scope.
- Accountant export totals match the visible property filters and database evidence to one VND.
- Invalid/conflicting callbacks appear as open cases without being counted as collected revenue.

## 10. Browser E2E journeys

Run Playwright with the real backend/database for:

1. Admin changes a custom role; active receptionist loses mutation rights on the next request.
2. Receptionist views but cannot execute a task after revocation; Manager reassigns it.
3. Customer booking -> VNPay -> display-only return -> confirmed booking.
4. Manager package order -> VNPay -> entitlement activation.
5. Accountant reconciles property payments and remains blocked from platform billing and operational mutations.

Include mobile and desktop layouts, loading/error/empty states, saved direct URLs, IDOR, stale version, duplicate callback and timeout/retry cases.

## 11. Final release gate

```powershell
Set-Location backend
.\mvnw.cmd test
.\mvnw.cmd package

Set-Location ..\frontend
npm test -- --watch=false
npm run build
npx playwright test
```

Completion additionally requires clean SQL Server migration, permission-matrix tests, next-request revocation tests, tenant isolation, task concurrency, both VNPay callback concurrency suites and exact reconciliation. Production payment remains disabled.

