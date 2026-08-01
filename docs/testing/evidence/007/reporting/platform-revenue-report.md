# T129 Platform Billing Revenue Report

## Scope

The Platform Billing report is system-scoped and reads only `platform_financial_transactions`, subscription orders, payment attempts and subscription entitlements. It never accepts a property ID and does not query Property Commerce entities.

The report exposes:

- purchase, renewal, upgrade, downgrade credit and subscription refund totals;
- gross, refunds, credits, net, cash collected and unpaid balance;
- plan mix with recurring eligibility derived from the plan billing period;
- subscription status and provider payment status counts;
- provider/method/transaction/plan filters;
- successful-attempt-to-ledger reconciliation issues and transaction rows.

Non-successful attempts are excluded from collected totals. A successful attempt without a matching immutable debit is marked unreconciled with `PLATFORM_PAYMENT_LEDGER_MISSING`; amount differences use `PLATFORM_PAYMENT_AMOUNT_MISMATCH`.

## Endpoint

`GET /api/admin/reports/platform-revenue`

The endpoint normalizes inclusive local dates to an exclusive instant range, supports `NET`, `CASH_COLLECTED` and `INVOICED` basis values, validates time zones and requires `PLATFORM_REVENUE` view permission.

## Automated Validation

Commands from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=PlatformRevenueServiceTest,PlatformRevenueControllerTest' -DforkCount=0 test
.\mvnw.cmd '-Dtest=PlatformRevenueRepositoryTest' -DforkCount=0 test
```

Result on 2026-08-02: 8 tests passed, 0 failed, 0 skipped; compile and H2 JPQL query validation succeeded.

No property tenant filter, production credential, external provider, migration or real-money operation was used.
