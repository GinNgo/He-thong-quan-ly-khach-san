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

# T077 Locked Invoice Finalization and Payment Allocation

## Scope

- Added transactional invoice finalization that requires `INVOICE/CREATE`, locks the reservation, validates tenant access and accepts only reservation/override identifiers rather than caller-authoritative money.
- Finalization returns an existing finalized invoice on retry; otherwise the reservation must remain `CHECKED_IN` and its folio is recalculated after the lock.
- A zero balance finalizes normally. Positive debt requires a persisted tenant/reservation-bound `DEBT` override whose amount still equals the locked folio balance. Negative balance remains blocked with `OVERPAYMENT_REQUIRES_RESOLUTION`.
- Invoice numbering is deterministic per property/reservation, and customer/property identity is serialized into secret-safe immutable JSON snapshots at finalization time.

## Line and Allocation Evidence

- Enriched authoritative folio lines with description, quantity, unit price, tax, discount and usage dates so invoice creation never reloads mutable room/service catalog pricing.
- Room snapshots use exact room-night quantity; legacy service snapshots preserve server-stored price/quantity/usage; append-only charge and reversal lines preserve their complete monetary evidence.
- Invoice finalization verifies every persisted line's economic effect against the signed folio effect and verifies the sum of all lines equals gross charges to one VND.
- Every successful Property Commerce debit is allocated exactly once at its immutable transaction amount, including deposits. Refunds/credits are excluded from allocation and retained in the invoice refund/credit header snapshot.
- A legacy successful payment without authoritative ledger rows fails closed instead of producing an invoice with untraceable payment allocation.
- Finalization appends a redacted audit event with total, paid, refund/credit, balance, line/allocation counts, source version and debt-override identity where applicable.

## Automated Validation

Combined command from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=InvoiceFinalizationServiceTest,PropertyInvoiceModelTest,FolioCalculationServiceTest,CheckoutOverrideServiceTest' -DforkCount=0 test
```

Result:

- Invoice finalization tests: 6 passed
- Invoice model regression: 7 passed
- Folio calculation regression: 5 passed
- Checkout override regression: 7 passed
- Total: 25 passed
- Failures: 0
- Errors: 0
- Skipped: 0

Fresh Spring context command with a process-only test secret:

```powershell
$env:JWT_SECRET='test_secret_for_context_validation_only_32_chars'
.\mvnw.cmd '-Dtest=BackendApplicationTests' -DforkCount=0 test
```

Result: 1 context test passed with no failures, errors or skips. Spring discovered 55 repositories and validated the finalization service plus the tenant-safe override lookup query.

## Safety and Recovery

- No room, housekeeping or reservation-status mutation is included yet; those atomic checkout boundaries remain assigned to T079/T080.
- No production credential, provider request, real-money operation, production mode or production database mutation was used.
- Finalized invoices, lines and allocations are immutable. Recovery disables new finalization while preserving existing evidence; transaction rollback removes a partially attempted invoice aggregate if any persistence boundary fails.

# T078 Append-Only Credit Notes and Adjustments

## Scope

- Added immutable, tenant-filtered `PropertyCreditNote` and `PropertyCreditNoteLine` entities plus repositories for post-finalization corrections.
- Added `CreditNoteService`, which requires `INVOICE_ADJUST/APPROVE`, resolves the authenticated actor, locks the finalized invoice and revalidates property access before any correction is persisted.
- The request supplies a reason and one or more exact positive integer-VND correction lines. The service derives the credit-note total from those lines instead of trusting a caller-supplied header total.
- Referenced invoice lines must belong to the same finalized invoice/property and must represent a positive charge. General unallocated credit lines remain allowed for approved goodwill or service-recovery corrections.
- Existing and requested credit-note amounts are checked under the invoice lock. Cumulative credit cannot exceed the finalized invoice total or the positive value of any referenced invoice line.
- The original invoice, invoice lines and payment allocations are never updated. Each accepted correction appends a note, immutable line snapshots and a redacted financial audit event containing safe invoice/note identifiers, total and line counts.

## Tenant Ownership Migration

- Added additive migration `V32__credit_note_line_tenant_ownership.sql` because the original V22 credit-note-line table lacked the constitution-required `hotel_id` column.
- V32 backfills ownership from the parent credit note, fails closed on null/conflicting note or invoice-line ownership, makes `hotel_id` non-null, adds the hotel foreign key and creates a tenant-leading index.
- The migration is repeat-safe and contains no delete, drop or production execution. `FinancialMigrationIntegrationTest` now tracks V21 through V32 and asserts the V32 backfill/fail-closed/recovery structure.

## Automated Validation

Focused command from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=CreditNoteServiceTest,PropertyCreditNoteModelTest,PropertyInvoiceModelTest,TenantFilterArchitectureTest,FinancialMigrationIntegrationTest' -DforkCount=0 test
```

Result:

- Credit-note service permission/tenant/cumulative-cap tests: 6 passed
- Credit-note model ownership/immutability tests: 3 passed
- Invoice model regression: 7 passed
- Tenant-filter architecture: 3 passed
- Financial migration architecture: 6 passed
- Total: 25 passed
- Failures: 0
- Errors: 0
- Skipped: 0

Fresh Spring context command with a process-only test secret:

```powershell
$env:JWT_SECRET='test_secret_for_context_validation_only_32_chars'
.\mvnw.cmd '-Dtest=BackendApplicationTests' -DforkCount=0 test
```

Result: 1 context test passed with no failures, errors or skips. Spring discovered 57 JPA repositories and validated both credit-note entities, repositories, locking query and tenant filters.

## Safety and Forward Recovery

- No production database, credential, provider, real-money operation or production payment mode was used.
- V32 was added to source and architecture-tested only; production migration remains a separate approval gate.
- If an upgrade fixture exposes null or conflicting ownership, V32 stops before the non-null constraint. Forward recovery is to correct the documented source ownership in an approved backup-protected maintenance window and rerun V32; do not delete credit-note evidence or rewrite applied migrations.
- Application recovery disables new credit-note issuance while retaining every finalized invoice and accepted note/line/audit row as immutable evidence.

# T079 Locked Aggregate Checkout Transaction

## Scope

- Replaced the legacy checkout path that accepted client payment amount/method/reference and wrote the mutable legacy invoice with the authoritative `InvoiceFinalizationService` result from T077.
- `ReservationService.checkout` now rejects caller-authoritative payment fields, accepts only an optional persisted debt-override identifier, locks the reservation and revalidates property access before checkout work begins.
- Checkout finalizes or reuses the authoritative immutable invoice before operational mutation. Outstanding balance, overpayment, stale override or missing ledger allocation therefore stops room/assignment/housekeeping changes.
- Added pessimistic assignment and room queries. Active assignments are locked in stable order, assigned rooms are locked by sorted ID, property ownership is revalidated and missing/concurrently changed rooms fail with `CONCURRENT_MODIFICATION`.
- Within the same Spring transaction, active assignments are released, locked rooms become `DIRTY`, pending housekeeping work is created where absent, the reservation becomes `CHECKED_OUT`, and the response uses the immutable Property Commerce invoice ID/number/status/total.
- The status-transition checkout path reuses the same locked aggregate method instead of maintaining a second financial/operational implementation.

## Automated Validation

Focused command from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=ReservationCheckoutTransactionTest,ReservationServiceTest,InvoiceFinalizationServiceTest' -DforkCount=0 test
```

Result:

- Locked checkout orchestration tests: 4 passed
- Reservation lifecycle/deposit/refund regression: 13 passed
- Authoritative invoice finalization regression: 6 passed
- Total: 23 passed
- Failures: 0
- Errors: 0
- Skipped: 0

Coverage includes the successful reservation/invoice/assignment/room/housekeeping sequence, debt-override forwarding, rejection of client payment totals, invoice failure before operational mutation and missing-room concurrent modification.

Fresh Spring context command with a process-only test secret:

```powershell
$env:JWT_SECRET='test_secret_for_context_validation_only_32_chars'
.\mvnw.cmd '-Dtest=BackendApplicationTests' -DforkCount=0 test
```

Result: 1 context test passed with no failures, errors or skips. Spring discovered 57 JPA repositories and parsed the new pessimistic assignment/room lock queries successfully.

## Safety and Recovery

- No production database, provider, credential, real-money operation or production payment mode was used.
- T079 adds no migration and does not delete or rewrite legacy invoice/payment rows. It only stops creating new legacy checkout financial evidence.
- Any unchecked persistence failure after invoice creation propagates through the same transaction so the invoice, assignment, room, housekeeping and reservation changes roll back together; exhaustive injected-boundary proof remains assigned to T084.
- T080 remains responsible for strengthening dirty-room and housekeeping creation to exactly-once behavior under retries/concurrency. Until then, checkout is serialized by the reservation/assignment/room locks and retains the existing pending-task existence guard.

# T080 Exactly-Once Checkout Operations

## Scope

- Added `CheckoutOperationsService` as a mandatory-participation (`Propagation.MANDATORY`) child of the locked checkout transaction.
- The service locks all reservation assignments in `ASSIGNED` or `RELEASED` state and locks their rooms in stable ID order. Only `ASSIGNED` rows transition to `RELEASED`, receive one release timestamp, and move their room to `DIRTY`.
- A retry after a committed `CHECKED_OUT` operation sees only `RELEASED` assignments and does not re-dirty a room, rewrite assignment evidence or create another housekeeping task. A missing release timestamp fails closed as concurrent/inconsistent state.
- Added `housekeeping_tasks.checkout_effect_key` and unique tenant-leading index `UX_housekeeping_checkout_effect`; checkout-generated tasks use `CHECKOUT:{reservationId}:ROOM:{roomId}`. This protects the economic/operational effect if another caller bypasses the service contract.
- `ReservationService` now delegates the operational phase to this service and permits a completed checkout retry to return the existing immutable invoice/room evidence without saving the reservation again.

## Automated Validation

Focused command from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=CheckoutOperationsServiceTest,ReservationCheckoutTransactionTest,InvoiceFinalizationServiceTest,FinancialMigrationIntegrationTest' -DforkCount=0 test
```

Result:

- Exactly-once operations/unit and transaction-boundary tests: 6 passed
- Checkout orchestration/retry tests: 5 passed
- Authoritative invoice finalization regression: 6 passed
- Financial migration architecture through V33: 7 passed
- Total: 24 passed
- Failures: 0
- Errors: 0
- Skipped: 0

Coverage includes initial dirty-room/task creation, completed retry without re-dirtying or duplicate task, existing effect-key reuse, cross-property denial, missing release evidence, mandatory transaction participation and orchestration rollback-before-reservation-save.

Fresh Spring context command with a process-only test secret:

```powershell
$env:JWT_SECRET='test_secret_for_context_validation_only_32_chars'
.\mvnw.cmd '-Dtest=BackendApplicationTests' -DforkCount=0 test
```

Result: pending final context run for V33 entity/repository discovery; no production database or migration execution was performed.

## Safety and Forward Recovery

- V33 is additive, repeat-safe and fails closed on duplicate non-null effect keys; it contains no delete/drop operation and remains subject to production migration approval.
- Existing housekeeping rows receive null effect keys and remain untouched. If a fixture has duplicate pre-existing keys, resolve the source evidence in an approved maintenance window and rerun V33; do not delete tasks as a shortcut.
- Application recovery disables checkout mutations while preserving reservation, invoice, assignment, room and housekeeping evidence. A failed transaction rolls back all changes together.

# T081 Property Commerce API Surface

## Scope

- Added management endpoints for server-priced service charges, typed surcharges/negative adjustments, authoritative checkout preview, debt override authorization and locked checkout under `/api/management/reservations/{reservationId}`.
- Added customer/property-safe invoice detail and immutable snapshot PDF endpoints under `/api/invoices/{invoiceId}`. PDF output is deterministic and contains only finalized invoice evidence.
- Added verified-recipient invoice email delivery and management credit-note issuance under `/api/management/invoices/{invoiceId}`. Credit notes remain append-only and are returned with their line evidence.
- Checkout rejects client-authoritative payment amount, method and transaction references; only a server-issued debt override can affect settlement.

## Validation

```powershell
.\mvnw.cmd -DskipTests compile
$env:JWT_SECRET='test_secret_for_context_validation_only_32_chars'
.\mvnw.cmd '-Dtest=BackendApplicationTests' -DforkCount=0 test
```

Results: compilation succeeded; Spring context test passed (`1/1`) with `57` JPA repositories and all T081 controllers/services loaded. No production payment, production database migration or real email provider was used.

# T082 Folio and Charge Unit Evidence

Focused command:

```powershell
.\mvnw.cmd '-Dtest=FolioCalculationServiceTest,ReservationChargeServiceTest,SurchargeServiceTest' -DforkCount=0 test
```

Result: `15/15` tests passed with zero failures, errors or skips. Coverage includes authoritative folio reconciliation, duplicate reversal/cross-property rejection, server-priced service snapshots, append-only corrections, typed surcharges and separate approval for negative adjustments.

# T083 Checkout Balance Evidence

Focused command:

```powershell
.\mvnw.cmd '-Dtest=CheckoutBalanceIntegrationTest' -DforkCount=0 test
```

Result: `3/3` tests passed. The boundary blocks underpayment with `OUTSTANDING_BALANCE`, blocks overpayment with `OVERPAYMENT_REQUIRES_RESOLUTION`, and requires a separate approved debt override before checkout can proceed.

# T084 Checkout Rollback Evidence

Focused command:

```powershell
.\mvnw.cmd '-Dtest=CheckoutRollbackIntegrationTest' -DforkCount=0 test
```

Result: `3/3` tests passed. Injected housekeeping, assignment and room persistence failures propagate without advancing later writes; the mandatory outer transaction remains responsible for rolling back earlier evidence.

# T085 Invoice Immutability Evidence

Focused command:

```powershell
.\mvnw.cmd '-Dtest=InvoiceImmutabilityIntegrationTest' -DforkCount=0 test
```

Result: `3/3` tests passed. Finalized invoices, invoice lines, payment allocations, credit notes and credit-note lines reject update/delete operations; payment allocation and post-finalization credit evidence remain separate append-only records.

# T086 Invoice Access and Delivery Evidence

Focused command:

```powershell
.\mvnw.cmd '-Dtest=InvoiceAccessIntegrationTest' -DforkCount=0 test
```

Result: `5/5` tests passed with zero failures, errors or skips. Coverage proves that the reservation customer and authorized property staff can read the finalized invoice, a different customer receives a non-enumerable `404`, PDF delivery uses the immutable invoice number, and email delivery is restricted to the verified invoice recipient.

No production email provider, production credential, production database migration or real-money operation was used.

# T087 Management Folio and Checkout Client Evidence

Implemented typed Angular clients for server-priced service/minibar charges, positive surcharges, separately authorized negative adjustments, authoritative checkout preview, debt override authorization, checkout and append-only credit notes. Checkout request types expose only the server-issued override identifier and do not expose caller-authoritative totals, payment methods or transaction references.

Focused unit command:

```powershell
npm test -- --watch=false --include src/app/core/services/property-checkout.service.spec.ts
```

Result: `5/5` tests passed. Production build command `npm run build` also completed successfully. Existing warnings remain for the property payment configuration component CSS budget and CommonJS WebSocket dependencies; neither warning originates from T087.
