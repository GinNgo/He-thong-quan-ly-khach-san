# T305 / STAY-014 Adjustment Remediation

Date: 2026-08-04
Scope: tenant-scoped surcharges, approved negative adjustments, duplicate protection, localized type parsing and visible adjustment history.

## Implemented

- `SurchargeService` now requires a persisted `FinancialIdempotencyService` identity for surcharge and negative-adjustment mutations in production. Replays resolve the original tenant-owned charge line; in-progress duplicates fail with `CONCURRENT_MODIFICATION`.
- Surcharge and negative-adjustment commands carry a canonical typed reason, immutable description/amount snapshot, correlation ID and idempotency key. Negative adjustments still require `INVOICE_ADJUST:APPROVE` in addition to surcharge-create permission.
- Type parsing accepts canonical English values plus Vietnamese/ASCII aliases (for example `trả phòng muộn` and `bồi hoàn dịch vụ`) and rejects unsupported values.
- `GET /api/management/reservations/{reservationId}/charges/adjustments` returns tenant-authorized append-only history with type, reason, amount, timestamp, actor ID and approval marker.
- Checkout adjustment UI keeps the same idempotency key across retries and renders surcharge/discount/adjustment lines as immutable approved history from the authoritative folio.

## Evidence

| Check | Result | Notes |
|---|---|---|
| `frontend/npm run build -- --configuration development` | PASS | Angular application bundle generated successfully. |
| Angular targeted unit tests (checkout service/component) | PASS | `property-checkout.service.spec.ts` (5/5) and `reservation-checkout.component.spec.ts` (10/10), 15/15 total; Vitest bundle generated. |
| Backend focused tests | PASS | `mvn -q -Dtest=SurchargeServiceTest,PropertyCheckoutServiceChargeControllerTest test`; 13/13 tests passed after strict-stub fixture cleanup. |
| Backend `mvn -q -DskipTests compile` | PASS | Shared compile completed after T324 syntax repair. |

## Residual gate

No implementation or focused-test gate remains. Shared inventory/traceability/task files are updated centrally by the root agent.
