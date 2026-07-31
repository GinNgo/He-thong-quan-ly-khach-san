# T070 Reservation Charge-Line Persistence

## Scope

- Added the tenant-owned `ReservationChargeLine` entity for room, service, minibar, surcharge, tax, fee, discount and adjustment snapshots.
- Added append-only correction linkage through `reverses_line_id`; persisted charge rows reject updates.
- Captured catalog source/version, code, name, description, unit price, quantity, tax, discount, total, service usage time and actor evidence.
- Added tenant-safe read methods plus a pessimistic-lock lookup for correction workflows.
- Registered `reservationChargeLineTenantFilter` in the request interceptor and its architecture coverage.

## Validation Rules

- Every line belongs to the same property as its reservation.
- Service and minibar lines require a usage timestamp.
- VND snapshots are non-negative scale-zero values; quantities are positive with at most three decimal places.
- A reversal can only reference another line from the same property and reservation.
- The entity maps the additive V22 `reservation_charge_lines` schema; this task adds no migration.

## Automated Validation

Command from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=ReservationChargeLineTest,TenantFilterArchitectureTest' -DforkCount=0 test
```

Final result:

- Reservation charge-line tests: 4 passed
- Tenant-filter architecture tests: 3 passed
- Total: 7 passed
- Failures: 0
- Errors: 0
- Skipped: 0

The command wrapper timed out while Maven was still running, but the same Maven process completed normally and generated successful Surefire reports for both suites.

## Permissions and Safety

- N/A for endpoint permissions: T070 introduces persistence only; mutation authorization is enforced by the services/controllers in later US3 tasks.
- No production credentials, provider request, real-money operation or production database mutation was used.
- Tenant filtering is active for authenticated property requests; later mutation services must also validate aggregate ownership.

## Recovery

- The V22 schema is additive and was already delivered by T015.
- Application recovery is to stop creating new charge lines while preserving existing append-only evidence; persisted financial rows must not be deleted or rewritten.

# T071 Server-Priced Service and Minibar Charges

## Scope

- Added a transactional `ReservationChargeService` that locks the reservation before creating or correcting folio charges.
- Accepts only the catalog service identity, SERVICE/MINIBAR classification, quantity and usage time; unit price and total are always calculated from the active server catalog.
- Snapshots bilingual catalog identity/description, catalog version, actor, quantity, usage time and exact VND amounts.
- Treats the existing catalog price as tax-inclusive because the current service catalog has no separate tax rule; the persisted tax snapshot is therefore server-owned zero rather than caller supplied.
- Requires an authenticated property actor with `RESERVATION_SERVICE/CREATE`, active property access and a `CHECKED_IN` reservation.

## Append-Only Corrections

- A correction locks the original line and appends an `ADJUSTMENT` reversal linked through `reverses_line_id`.
- The original charge remains unchanged and a line cannot be corrected twice.
- An optional replacement is created from the current server catalog price in the same transaction.
- Cross-property catalog items, inactive services, non-service lines, future usage times and invalid VND/quantity values are rejected before persistence.

## Automated Validation

Focused command from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=ReservationChargeServiceTest,ReservationChargeLineTest,TenantFilterArchitectureTest' -DforkCount=0 test
```

Result:

- Service tests: 5 passed
- Entity tests: 4 passed
- Tenant-filter architecture tests: 3 passed
- Total: 12 passed
- Failures: 0
- Errors: 0
- Skipped: 0

Fresh application-context validation, with a test-only secret supplied to the command process:

```powershell
$env:JWT_SECRET='test_secret_for_context_validation_only_32_chars'
.\mvnw.cmd '-Dtest=BackendApplicationTests' -DforkCount=0 test
```

Result: 1 context test passed; all 51 JPA repositories, including the charge-line repository, were scanned and the Spring Boot application context started successfully.

## Safety and Recovery

- No endpoint, provider request, production credential, real-money action or production database mutation is part of T071.
- Recovery disables new charge creation while retaining all existing charge/reversal evidence; corrections are never rolled back by deleting or rewriting persisted lines.

# T072 Typed Surcharges and Negative Adjustments

## Scope

- Added typed positive surcharges for early check-in, late checkout, extra guests, damage, cleaning, lost keys and explicit other reasons.
- Added typed negative adjustments for service recovery, goodwill, price correction, manual discount and explicit other reasons.
- Positive surcharges persist as `SURCHARGE` lines; negative adjustments persist as `DISCOUNT` lines with a positive effect magnitude so folio calculation can subtract them without storing negative VND.
- Every command requires a description, exact positive integer VND, an authenticated actor, active property access and a locked `CHECKED_IN` reservation.

## Permission and Audit Rules

- Positive surcharge creation requires `RESERVATION_SURCHARGE/CREATE`.
- Negative adjustment requires both `RESERVATION_SURCHARGE/CREATE` and the separate `INVOICE_ADJUST/APPROVE` permission.
- Each accepted mutation appends a redacted Property Commerce audit event containing reservation, charge type, typed reason, amount, actor, correlation and creation state evidence.
- Permission, ownership, state and amount failures occur before charge/audit persistence.

## Automated Validation

Command from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=SurchargeServiceTest,ReservationChargeServiceTest,ReservationChargeLineTest,FinancialAuditServiceTest' -DforkCount=0 test
```

Result:

- Surcharge/adjustment tests: 5 passed
- Service charge regression: 5 passed
- Charge-line entity regression: 4 passed
- Financial audit regression: 1 passed
- Total: 15 passed
- Failures: 0
- Errors: 0
- Skipped: 0

## Safety and Recovery

- No tax policy, provider call, production secret, real-money action or production database mutation was introduced.
- Recovery stops new mutations while preserving charge and audit evidence; existing surcharge/discount rows are append-only and must not be rewritten or deleted.

# T073 Authoritative Folio Calculation

## Scope

- Added one server-side folio calculator for room, service/minibar, surcharge, tax, fee, discount, successful payment, refund/credit, net settlement and remaining balance.
- Room charges prefer immutable booking details/snapshots and fail closed when the detail total disagrees with the booking snapshot.
- New append-only charge lines are authoritative; full reversal adjustments negate the original component snapshot and duplicate reversals are rejected.
- Active legacy service rows remain read-compatible until migration, while authoritative Property Commerce ledger rows take precedence over legacy payment rows to prevent double counting.
- Deposit transactions are ordinary successful ledger evidence and are included exactly once rather than added again as a separate checkout payment.

## Reconciliation Rules

- Normal charge totals must equal `unit price x quantity + tax - discount` to one VND.
- Standalone tax and discount lines carry one unambiguous effect magnitude.
- Gross equals `room + service + surcharge + tax + fee - discount` after append-only corrections.
- Net settled equals successful debits minus refunds and other credits; credits cannot exceed successful payments.
- Balance equals gross minus net settled and may be negative only to represent overpayment for T074/T075 policy handling.
- Pending, failed, cancelled and expired legacy payments are excluded; ambiguous positive `REFUNDED` legacy rows require reconciliation instead of silent inclusion.

## Automated Validation

Combined regression command from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=FolioCalculationServiceTest,SurchargeServiceTest,ReservationChargeServiceTest,BookingFinancialSummaryServiceTest' -DforkCount=0 test
```

Result before the final legacy-status hardening:

- Total: 20 passed
- Failures: 0
- Errors: 0
- Skipped: 0

Final focused command after the legacy-status and reversal-reference guards:

```powershell
.\mvnw.cmd '-Dtest=FolioCalculationServiceTest' -DforkCount=0 test
```

Final result: 5 passed with no failures, errors or skips. Coverage includes exact VND reconciliation, service correction, surcharge/fee/tax/discount components, overpayment balance, deposit de-duplication, authoritative-ledger precedence, legacy service/payment fallback, cancelled-payment exclusion, ambiguous refund blocking, cross-property evidence and corrupt snapshot equations.

## Safety and Recovery

- The calculator is read-only and creates no financial mutation, migration, provider request or production effect.
- Recovery is application-only: stop preview/checkout consumers and retain all immutable source evidence for diagnosis; no charge or ledger row is rewritten.

# T074 Authoritative Checkout Preview

## Scope

- Added a read-only checkout preview that loads the reservation and recomputes the complete server-owned folio; the API accepts no caller-supplied amount or total.
- Preview is limited to `CHECKED_IN` reservations and authorized property users with `CHECKOUT/VIEW`; cross-property reservations fail as not found.
- Exact zero balance returns `SETTLED` and permits checkout. Positive balance returns `OUTSTANDING` with `OUTSTANDING_BALANCE`; negative balance returns `OVERPAID` with `OVERPAYMENT_REQUIRES_RESOLUTION`.
- `requireSettled` applies the same authoritative calculation and stable financial errors for later atomic checkout orchestration.

## Automated Validation

Focused command from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=CheckoutPreviewServiceTest,FolioCalculationServiceTest' -DforkCount=0 test
```

Result:

- Checkout preview tests: 5 passed
- Folio calculation regression: 5 passed
- Total: 10 passed
- Failures: 0
- Errors: 0
- Skipped: 0

Coverage includes settled, outstanding and overpaid previews; invalid reservation state; missing permission; cross-property access; exact VND validation; authoritative charge/payment/refund calculation; and no caller-authoritative financial input.

## Safety and Recovery

- T074 is read-only and creates no invoice, checkout mutation, provider request, migration, production credential or real-money effect.
- Debt and overpayment exceptions remain blocked until the explicit policy and evidence workflow in T075.
- Recovery disables the preview consumer while retaining all append-only charge and ledger evidence; no financial row requires rollback or deletion.

# T075 Debt Override and Overpayment Policy

## Scope

- Added an append-only, tenant-filtered `CheckoutOverride` model and repository mapped to the additive `checkout_overrides` table delivered by T015.
- Normal checkout still requires an exact zero server-owned balance. Outstanding debt remains blocked unless an authenticated actor supplies a non-blank reason and has `RESERVATION_DEBT_OVERRIDE/APPROVE`.
- Debt approval locks the reservation, re-runs the authoritative preview under the lock, and stores the recalculated positive VND balance; no caller-supplied amount is accepted.
- Accepted debt overrides snapshot property, reservation, amount, reason, actor and approver, then append a redacted Property Commerce audit event with the folio source version and correlation ID.
- Overpayment remains fail-closed with `OVERPAYMENT_REQUIRES_RESOLUTION`; no automatic refund, credit, cash payout or other unapproved commercial policy was invented.

## Concurrency, Permission, and Tenant Rules

- A payment arriving between initial preview and reservation lock removes the need for an override and creates no override/audit row.
- Cross-property or non-`CHECKED_IN` reservations are rejected after locking and before evidence persistence.
- `CREATE` alone is insufficient for debt override; the separate `APPROVE` action is required unless the actor is a system administrator.
- The `checkoutOverrideTenantFilter` is activated and cleared with the existing authenticated request tenant filters.

## Automated Validation

Combined regression command from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=CheckoutOverrideServiceTest,CheckoutPreviewServiceTest,FolioCalculationServiceTest,TenantFilterArchitectureTest,FinancialAuditServiceTest' -DforkCount=0 test
```

Result:

- Checkout override tests: 7 passed
- Checkout preview regression: 5 passed
- Folio calculation regression: 5 passed
- Tenant-filter architecture: 3 passed
- Financial audit regression: 1 passed
- Total: 21 passed
- Failures: 0
- Errors: 0
- Skipped: 0

Fresh Spring context command with a process-only test secret:

```powershell
$env:JWT_SECRET='test_secret_for_context_validation_only_32_chars'
.\mvnw.cmd '-Dtest=BackendApplicationTests' -DforkCount=0 test
```

Result: 1 context test passed with no failures, errors or skips; the new entity, tenant filter, repository and service were discovered successfully.

## Safety and Recovery

- No production secret, provider request, real-money operation, production mode or production database mutation was used.
- T075 reuses the additive T015 schema and requires no new migration or destructive cleanup.
- Recovery stops new debt approvals while preserving existing append-only override and audit evidence; overpayment stays blocked until a separately approved policy is implemented.

# T076 Immutable Invoice Snapshot Model

## Scope

- Added tenant-filtered `PropertyInvoice`, `PropertyInvoiceLine` and `PropertyInvoicePaymentAllocation` entities mapped to the additive T015/V22 schema.
- Final invoices are created directly as immutable snapshots with customer/property JSON, subtotal, tax, fee, discount, total, paid, refunded, balance, finalizer and finalization time.
- Invoice construction validates exact integer VND equations: `subtotal + tax + fee - discount = total` and `paid - refunded = total - balance`.
- Invoice lines preserve type, source charge identity, code/name/description, quantity, unit price, tax, discount, total and usage dates. Discount lines store one positive magnitude while ordinary lines must reconcile their full price equation.
- Payment allocations bind one finalized invoice to one successful Property Commerce debit, reject refunds/credits, cross-property or cross-reservation evidence, and cannot exceed the source transaction amount.
- Added repositories for invoice lookup/locking, ordered line reads and unique transaction allocations. The new Property Commerce model remains separate from the legacy `invoices` compatibility entity/table.

## Immutability and Tenant Rules

- All three financial snapshot entities use Hibernate immutability plus lifecycle guards that reject update and delete operations.
- Property ownership is non-null on invoice, line and allocation rows and is protected by three request-activated Hibernate tenant filters.
- A finalized invoice cannot be recalculated from mutable hotel, room, service or customer records; T077 will populate these snapshots only from the locked authoritative folio.

## Automated Validation

Focused command from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=PropertyInvoiceModelTest,TenantFilterArchitectureTest' -DforkCount=0 test
```

Result:

- Invoice model/equation/immutability/allocation tests: 7 passed
- Tenant-filter architecture tests: 3 passed
- Total: 10 passed
- Failures: 0
- Errors: 0
- Skipped: 0

Fresh Spring context command with a process-only test secret:

```powershell
$env:JWT_SECRET='test_secret_for_context_validation_only_32_chars'
.\mvnw.cmd '-Dtest=BackendApplicationTests' -DforkCount=0 test
```

Result: 1 context test passed with no failures, errors or skips. Spring Data discovered 55 repositories and validated the new entity mappings and derived queries.

## Safety and Recovery

- T076 performs no invoice finalization, checkout mutation, provider call, production credential use, real-money operation or production database change.
- The schema remains the additive V22 migration from T015; no legacy invoice data is deleted or rewritten.
- Recovery disables new Property Commerce invoice creation while retaining all finalized snapshots and allocations as immutable financial evidence.
