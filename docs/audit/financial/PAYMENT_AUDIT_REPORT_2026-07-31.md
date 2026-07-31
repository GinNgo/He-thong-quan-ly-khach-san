# Payment Audit Report - 2026-07-31 Baseline

## Scope

The audit covers Property Commerce, Platform Billing, provider callbacks, checkout/invoice/refund, reconciliation and cross-cutting tenant/security behavior. It uses the Feature 007 requirements and the current source tree; no production credentials or real-money transaction was used.

## Confirmed strengths

- Server-owned `PaymentSession` and reservation ownership binding exist.
- VNPay, MoMo, ZaloPay and simulator callback verification paths exist in some form.
- Reservation holds, payment-provider recovery and refund provider-attempt records exist.
- Existing tenant/IDOR integration tests cover several reservation, invoice, payment and housekeeping paths.

## Baseline gaps

- Property-specific bank/QR/deposit configuration is not complete.
- Financial data is not consistently classified and separated into property/platform ledgers.
- Hibernate tenant filters are declared on some entities but are not reliably activated for every request/transaction.
- Invoice, service, surcharge and correction snapshots are incomplete.
- Checkout and subscription billing still have caller-authority/idempotency/policy gaps.
- Analytics revenue is not reconciled to authoritative transactions.

## Baseline test evidence

- Backend: 218 tests, 24 failures, 40 errors, 0 skipped. Failed suites include admin authorization, payment, subscription, property search/discovery, notification, reservation concurrency and tenant isolation.
- Backend package: compile/JAR succeeded; Spring Boot repackage failed because `backend/target/backend-0.0.1-SNAPSHOT.jar` was locked.
- Frontend unit command: timed out after 600 seconds without a result.
- Frontend production build: passed after approximately 480 seconds; emitted CommonJS warnings for `@stomp/stompjs` and `sockjs-client`.
- Playwright discovery: 86 tests across 17 files.

## Required disposition

All `PARTIAL`, `PLACEHOLDER`, `BROKEN` and `MISSING` findings remain open in Feature 007 tasks. Production payment remains disabled.
