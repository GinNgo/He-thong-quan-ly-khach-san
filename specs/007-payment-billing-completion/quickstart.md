# Quickstart Validation Guide

This guide validates Feature 007 safely. It does not authorize real merchant credentials, production mode, real-money transfers or production database migration.

## Prerequisites

- Java 21, Maven, Node/npm and Docker available.
- SQL Server test container or an isolated SQL Server 2022 database.
- Simulator or official sandbox credentials only.
- Deterministic test users/properties for customer, owner, receptionist, staff, housekeeping, admin and super admin roles.
- Review [financial-api-contract.md](./contracts/financial-api-contract.md) and [data-model.md](./data-model.md) before implementation validation.

## 1. Preserve and record baseline

```powershell
git status --short --branch
git diff --stat
```

Save baseline command outputs in `docs/testing/evidence/007/baseline/`. Do not reset, clean or overwrite unrelated worktree changes.

## 2. Build and run current automated tests

```powershell
Set-Location backend
./mvnw.cmd test
./mvnw.cmd package -DskipTests

Set-Location ../frontend
npm test -- --watch=false
npm run build
npx playwright test --list
```

Record pass/fail/skipped results before fixing failures. A historical run does not count as final evidence.

## 3. Validate migrations

Run two isolated profiles:

1. Clean database: apply every Flyway migration from the beginning and seed deterministic fixtures.
2. Upgrade database: start from the documented pre-Feature-007 schema/data fixture, run preflight checks, migrations and backfill.

Expected outcomes:

- No destructive schema reset.
- No ambiguous payment is silently assigned to a context/property.
- All uniqueness, ownership and foreign-key checks pass.
- Clean and upgraded schemas expose equivalent Feature 007 contracts.

## 4. Property Commerce safe journey

1. Configure a property with `SIMULATOR` or `SANDBOX`, deposit policy, test bank identity, expiry and bilingual instructions.
2. Create a booking using server-calculated availability and price.
3. Create a deposit attempt without supplying an authoritative amount.
4. Verify QR/manual instructions show the correct property, exact amount, unique content and environment label.
5. Confirm through signed simulator/provider callback or authorized manual confirmation.
6. Replay and concurrently submit the same callback.
7. Verify exactly one successful ledger effect and the correct booking financial state.

Negative checks: wrong signature, amount, merchant, currency, booking, expired attempt, cross-property access and UI self-confirmation must change no financial data.

## 5. Check-in to checkout journey

1. Assign a valid room and check in.
2. Add a service, minibar line and authorized surcharge from server-owned data.
3. Add multiple successful test payments if needed.
4. Compare checkout preview to database charge/payment/refund fixtures.
5. Attempt underpaid checkout and confirm it is blocked.
6. Execute successful checkout.
7. Verify invoice finalization, allocations, room `DIRTY` state and exactly one housekeeping task.
8. Inject a failure at each persistence boundary and verify complete rollback.

### 5.1 Audit-gap acceptance: tenant services and printed invoice

1. Use two properties with distinct food/drink/minibar catalogs and one checked-in reservation in each property.
2. Add a service to reservation A, replay the same `Idempotency-Key`, and verify one `reservation_charge_lines` row with reservation A's `hotel_id`.
3. Attempt to use property B's service ID for reservation A and verify a denial with no new charge line.
4. Generate the finalized invoice, download the PDF as customer and authorized staff, and extract text to verify service/minibar name, quantity, unit price, tax/discount, payments/refunds and total/paid/balance.
5. Print from the admin invoice screen and verify the browser print view contains the same itemized lines as the PDF; verify no legacy `generateInvoice()` request is made.
6. Change the catalog price/name after finalization and verify both documents remain unchanged.

Expected evidence: HTTP authorization/IDOR results, idempotency replay result, SQL Server rows, PDF text/checksum and browser screenshots are recorded under `docs/testing/evidence/007/final/`.

## 6. Property refund journey

Request partial, repeated, excessive and concurrent refunds against one original successful transaction. Successful cumulative refunds must never exceed the charge; the original transaction stays immutable; gross/refund/net reconciliation updates exactly once.

## 7. Platform Billing journey

1. Create an owner/property and select an active plan.
2. Create an order and inspect its backend-owned price/duration/feature snapshot.
3. Pay through the platform simulator/sandbox merchant.
4. Replay and concurrently deliver the callback.
5. Verify exactly one platform ledger effect, contract and entitlement history transition.
6. Repeat for renewal and an approved upgrade.

Negative checks: changed catalog price after order creation, client-modified price, wrong system merchant, expired order, cancellation, unsupported downgrade/proration and cross-owner access must activate zero features.

## 8. Reporting reconciliation

For fixed fixtures and filters, compare:

- Property report API totals and rows.
- Platform report API totals and rows.
- Successful immutable financial transactions.
- Final invoice lines and payment allocations.
- Refund/credit transactions.
- Excel/PDF/CSV exported rows and totals.

Expected equations:

`property gross - property refunds = property net`  
`platform gross - platform refunds/credits = platform net`

Pending/failed/cancelled/expired attempts are excluded from collected money, and deposits are allocated only once.

## 9. Mandatory browser journeys

Run Playwright and manual evidence for:

1. Owner registration, property approval, plan purchase and activation.
2. Customer search, booking, deposit and property visibility.
3. Check-in, services/surcharges, multiple payments, checkout, invoice and housekeeping.
4. Cancellation and full/partial refund.
5. Subscription renewal/upgrade and platform reporting.

Each journey must include positive, validation, permission/IDOR, replay/concurrency, timeout/provider-failure and safe-retry cases where applicable.

## 10. Final-worktree release gate

Run on the final worktree and a fresh database:

```powershell
Set-Location backend
./mvnw.cmd test
./mvnw.cmd package

Set-Location ../frontend
npm test -- --watch=false
npm run build
npx playwright test
```

Also run clean-migration, SQL Server integration, security, tenant-isolation, payment idempotency/concurrency, refund concurrency, checkout rollback, export and report-reconciliation suites.

Completion requires:

- No P0/P1 known issue.
- No mandatory skipped test.

## VNPay sandbox demo run (2026-08-08)

The local environment contains the VNPay sandbox gate and the four required configuration keys (`PAYMENT_SANDBOX_ENABLED`, `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, `VNPAY_URL`, and `VNPAY_RETURN_URL`) without recording their values in this artifact. The focused automated verification completed successfully:

```powershell
Set-Location backend
.\mvnw.cmd -q '-Dtest=VnpayPaymentGatewayTest,PaymentProviderAdaptersTest,PaymentControllerIntegrationTest' test
```

The checks cover URL signing, VNPay callback normalization, browser-return display-only behavior, IPN acknowledgement, merchant/signature validation and server-authoritative payment state. The logged-in VNPay SIT page was inspected and currently shows Terminal Code `Z2PFKFYP` with IPN URL `#`; no external configuration was submitted. Before a real browser payment journey, set the SIT IPN URL to the deployed HTTPS endpoint `/api/payments/vnpay-ipn`, then create a real pending booking/payment session in LuxeStay. Production remains fail-closed (`PAYMENT_PRODUCTION_ENABLED=false`, `PAYMENT_PRODUCTION_APPROVED=false`).
- No remaining in-scope `PARTIAL`, `PLACEHOLDER`, `BROKEN` or `MISSING` inventory item.
- Reconciliation exact to one VND.
- No cross-property data access or mutation.
- No real money or real merchant used.
- Production payment still disabled unless separately approved.
