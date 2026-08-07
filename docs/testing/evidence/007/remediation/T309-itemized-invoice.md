# T309 - Finalized Itemized Invoice Evidence

Date: 2026-08-04
Scope: canonical finalized property invoice view, PDF, email attachment and browser print paths.

## Source changes

- `PropertyInvoiceDocumentService` now renders immutable property/customer snapshots, room/service/minibar/surcharge/tax/fee/discount/adjustment lines, quantity, unit price, tax, discount, line totals, payment allocations, refund/credit snapshots, total, paid and balance.
- PDF output is deterministic for the same finalized snapshot, supports multiple pages, and exposes `X-Content-SHA256` plus a stable `ETag`.
- `PropertyInvoiceController` is the canonical detail/PDF/email owner and exposes finalized staff list and reservation lookup endpoints.
- Legacy `InvoiceController` no longer owns the ambiguous `/api/invoices/{id}` mapping. Its reservation POST compatibility route is read-only in effect: it returns an existing finalized invoice or `409`; it never calls mutable `InvoiceService.generateInvoice()` and never reads `reservation.totalAmount`.
- Admin and customer screens render the same finalized detail model. Both support itemized view, browser print, PDF download and invoice email states.

## Focused validation

### Frontend

Command:

```powershell
npm test -- --watch=false --include=src/app/core/services/invoice.service.spec.ts --include=src/app/features/admin/invoice-management/invoice-management.spec.ts --include=src/app/features/client/my-invoices/my-invoices.component.spec.ts
```

Result: 3 files, 12 tests passed.

Coverage includes canonical admin list/detail API calls, customer finalized list/detail, service/minibar lines, identity snapshots and print gating.

### Backend unit/model tests

Focused classes:

- `PropertyInvoiceDocumentServiceTest`: 1/1 passed; asserts deterministic bytes and itemized PDF text including snapshots, all line types, payment, refund/credit and reconciled totals.
- `LegacyInvoiceCompatibilityControllerTest`: 2/2 passed; proves the legacy write-shaped endpoint returns the existing finalized invoice and does not interact with the legacy repository.
- `InvoiceFinalizationServiceTest`: 6/6 passed.
- `InvoiceImmutabilityIntegrationTest`: 3/3 passed.
- `InvoiceAccessIntegrationTest`: 8/8 passed after adding the test slice's missing `OperationalMetrics` mock.

Quiet shared-target rerun: the four unit/model classes passed 12/12 and `InvoiceAccessIntegrationTest` passed 8/8 (20/20 backend total). The three frontend files were also rerun after source settled and passed 12/12.

## Known blockers and limits

- The invoice-focused unit/model, MVC access and Angular suites pass on a quiet shared target. A full repository build remains a separate final-worktree gate because unrelated feature agents are still changing subscription and other modules.
- Email delivery remains synchronous through the existing `EmailService`; transactional outbox/sandbox mailbox evidence belongs to T323/CROSS-008 and is not silently claimed complete here.
- SQL Server clean-migration and real browser execution require the final shared worktree and deterministic seeded environment; source and focused tests do not substitute for those gates. T309 therefore remains open until the sandbox email and complete customer/admin browser evidence required by the task are captured.

## Artifact fingerprint

`PropertyInvoiceDocumentServiceTest` compares two renders from the same fixture byte-for-byte and verifies a 64-character SHA-256 fingerprint. The controller also emits the checksum on PDF responses for downstream evidence capture.
