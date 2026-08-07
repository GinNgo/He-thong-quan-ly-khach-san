# T304 - Tenant-scoped service and minibar charges

## Design contract

- The locked reservation is authoritative for `hotel_id`; the request cannot select or override a property.
- The catalog is loaded only after checkout preview returns the authorized reservation `hotelId`.
- `POST /api/management/reservations/{reservationId}/charges/services` requires `Idempotency-Key`.
- The persisted idempotency identity is scoped to the reservation and hashes service ID, charge type, quantity, and usage time.
- An equivalent replay returns the original charge line with `replayed=true`; a changed payload with the same key fails with `IDEMPOTENCY_KEY_REUSED`.
- Tenant-owned catalog rows must match the reservation property. System templates remain available, while inactive or cross-property rows fail closed.
- Both `SERVICE` and `MINIBAR` are explicit UI choices and are persisted as immutable `reservation_charge_lines` snapshots.
- The active reservation checkout UI reads the authoritative folio and no longer preloads an ambiguous legacy/global service collection.

## Verification contract

- Backend service tests: server-owned price, tenant denial, persisted replay, conflicting payload, and no duplicate charge row.
- Backend controller test: idempotency/correlation propagation and replay response contract.
- Frontend service/component tests: explicit mutation header, reservation-property catalog request, SERVICE/MINIBAR selection, and retry-key reuse.
- Focused test commands and exact results are recorded below.

## Migration and rollback

- Schema migration: N/A. T304 reuses `financial_idempotency_records` and `reservation_charge_lines`.
- Rollback: revert the focused application changes. Existing persisted idempotency and charge records remain valid financial evidence and must not be deleted.

## Validation status (2026-08-04)

- Quiet backend rerun passed: `ReservationChargeServiceTest` 7/7, `PropertyCheckoutServiceChargeControllerTest` 3/3, and `ReservationChargeIdempotencyPersistenceIntegrationTest` 2/2 (12/12 total).
- The persistence tests prove an equivalent replay survives separate transactions with one charge row/one idempotency record, and a cross-property catalog ID fails before either record is persisted.
- Focused frontend rerun passed 3 files/15 tests: `hotel-service.service.spec.ts` 2/2, `property-checkout.service.spec.ts` 5/5, and `booking-checkout.component.spec.ts` 8/8.
- The prior shared-target and unrelated template blockers were transient; neither remains in the isolated focused rerun.
- No production database, provider, or credentials were touched.
