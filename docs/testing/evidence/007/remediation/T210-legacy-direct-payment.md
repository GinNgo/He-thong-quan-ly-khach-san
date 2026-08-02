# T210 Legacy Direct Payment Mutation

## Scope

- Retired `POST /api/payments` with `410 Gone`, `Deprecation: true`, and a successor link to the server-owned payment-attempt flow.
- Removed the admin reservation-list cash-payment action and the Angular client mutation. The checked-in row now opens the authoritative folio workspace.
- Removed `PaymentService.processPayment()` so no application service can create a successful payment from a caller-supplied amount or method.
- Added `V44__legacy_payment_reconciliation.sql`. Legacy ledger rows preserve the source method, status and transaction reference. Rows without an exact successful `payment_sessions` match are marked `legacy_reconciliation_required=1` and recorded in `financial_migration_exceptions`; refunds remain quarantined until an original transaction is linked.
- Folio, booking summaries, invoice allocations, refund eligibility and property revenue reporting ignore quarantined legacy evidence. Authoritative provider callbacks and manual confirmations remain the only settlement writers.

## Automated Validation

Backend command from `backend/`:

```powershell
.\mvnw.cmd -q '-Dtest=LegacyPaymentRetirementTest,LegacyPaymentMigrationTest,PaymentControllerIntegrationTest,PaymentServiceImplTest,BookingFinancialSummaryServiceTest,FolioCalculationServiceTest,InvoiceFinalizationServiceTest,PropertyRefundServiceTest,PropertyRevenueRepositoryTest' -DforkCount=0 test
```

Result: 44 tests passed, 0 failures, 0 errors.

Frontend command from `frontend/`:

```powershell
npm test -- --watch=false --include=src/app/features/admin/reservation-management/reservation-lifecycle-permissions.spec.ts --include=src/app/features/admin/reservation-management/reservation-management.spec.ts --include=src/app/core/services/property-payment.service.spec.ts
```

Result: 11 tests passed across 3 files. The Angular build emitted only the existing `NG8107` warning in `ClientLayout`.

The backend controller matrix covers a tampered amount/method payload, replay-shaped transaction input, missing authentication and the `FINANCE:CREATE` route contract. Existing authoritative payment tests cover server-owned amount derivation, method binding, cross-reservation transaction rejection and idempotent manual confirmation replay.

## SQL Server Evidence

An isolated SQL Server database `LuxestayT210_<pid>` was created and dropped after verification. Three legacy rows were inserted: one payment matched to a `SUCCEEDED` MOMO session, one direct CASH row without session evidence, and one negative refund without an original transaction link. V44 produced:

| Legacy row | `legacy_reconciliation_required` | Preserved reference |
| ---: | ---: | --- |
| provider-backed MOMO payment | `0` | `provider-tx` |
| direct CASH payment | `1` | `cash-tx` |
| negative legacy refund | `1` | `refund-tx` |

The exception table contained one `LEGACY_SETTLEMENT_UNVERIFIED` row and one `LEGACY_REFUND_UNLINKED` row. Re-running V44 is repeat-safe and does not delete or rewrite source payment rows.
