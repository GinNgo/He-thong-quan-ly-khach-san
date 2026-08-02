# T209 Legacy In-Stay Service Charge Reconciliation

## Scope

- Removed the reservation-list legacy service dialog and its `POST /api/reservations/{id}/services` client mutation.
- Retired the legacy endpoint with `410 Gone` and a successor link to the Property Commerce charge-line endpoint.
- Added `legacy_service_item_id` to immutable `reservation_charge_lines` and V43 backfilled valid active legacy rows with a filtered unique guard.
- Updated folio calculation to skip a legacy row only when its exact legacy id is represented by an authoritative charge line; unrelated legacy rows remain readable during migration.
- Defaulted omitted service usage time at the authoritative controller boundary to the server's UTC clock.

## Automated Validation

Backend command from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=FolioCalculationServiceTest,LegacyServiceChargeRetirementTest,PropertyCheckoutServiceChargeControllerTest,LegacyServiceChargeMigrationTest,ReservationChargeServiceTest,ReservationServiceTest' -DforkCount=0 test
```

Result: 30 tests passed, 0 failures, 0 errors.

Frontend command from `frontend/`:

```powershell
npm test -- --watch=false --include=src/app/core/services/reservation-lifecycle.service.spec.ts --include=src/app/features/admin/reservation-management/reservation-lifecycle-permissions.spec.ts --include=src/app/core/services/property-checkout.service.spec.ts
```

Result: 10 tests passed across 3 files. The Angular build emitted only the existing `NG8107` optional-chain warning in `ClientLayout`.

## SQL Server Evidence

An isolated SQL Server 2022 container database `LuxestayT209Reconcile` was created with one valid active legacy service row (`id=51`, total `150000` VND). V43 was executed twice, then verified:

- Exactly one charge line references `legacy_service_item_id=51`.
- The reconciled total remains `150000` VND.
- `UX_charge_lines_legacy_service_item` exists.
- The second execution created no duplicate row.

The disposable database was dropped after verification. No application database, production credential or real payment was used.
