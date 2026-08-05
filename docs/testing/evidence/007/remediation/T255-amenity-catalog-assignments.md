# T255 - Amenity catalog, assignments and indexed search

Date: 2026-08-04
Branch: `codex/property-operations`

## Implemented behavior

- Added a global localized amenity catalog with stable normalized code, Vietnamese
  and English names, category, icon, display order and `ACTIVE`/`INACTIVE` soft
  lifecycle. V81 seeds Wi-Fi, parking, breakfast, pool, gym, air conditioning,
  non-smoking and accessible-facility entries without duplicating reruns.
- Added property and room-type assignment aggregates with explicit property owner.
  Replacement requests require an exact duplicate-free set of active amenity IDs;
  invalid input is rejected before existing assignments are deleted.
- Added public catalog, system catalog lifecycle and property/room-type assignment
  endpoints. The same reusable Angular editor is reachable in the system property
  and room-type dialogs, the owner dashboard and owner room-type inventory.
- Public property search now accepts URL/query `amenityIds`. Every selected amenity
  must be active and assigned either to the property or to one of its active room
  types. Search responses combine property and active-room-type amenities without
  duplicates, and result cards render localized badges.
- The responsive filter sidebar loads the canonical catalog, preserves selected IDs
  in the URL, exposes removable chips and sends the IDs to the backend search API.

## Authorization and tenant isolation

- Global catalog create/update/deactivate requires `SUPER_ADMIN` in both controller
  and service. Property staff cannot mutate the system catalog.
- Assignment routes admit the existing management roles, then independently resolve
  the property or locked room type through `PropertyAccessService`. Cross-property
  room-type IDs fail before amenity lookup or assignment deletion.
- `PropertyAmenity` and `RoomTypeAmenity` carry non-null `hotel_id`, declare Hibernate
  tenant filters and are activated by the request interceptor. Clients never submit
  a property owner inside assignment rows.
- The database composite foreign key `(room_type_id, hotel_id)` prevents a room type
  from being associated through another property's assignment even for direct SQL
  writers.

## Verification

Backend focused command:

```text
backend\mvnw.cmd "-Dtest=AmenityServiceTest,PropertySearchAmenityTest,TenantFilterArchitectureTest" test
```

Result: 10/10 passed with zero failures, errors or skipped tests. Coverage includes
catalog normalization and system-admin denial, exact property ownership, duplicate
and inactive-ID rollback protection, cross-property room-type rejection, combined
public display, active property/room-type SQL predicates and tenant-filter wiring.

Frontend focused command:

```text
npm test -- --watch=false --include=src/app/core/services/amenity.service.spec.ts --include=src/app/shared/components/amenity-assignment/amenity-assignment.component.spec.ts --include=src/app/features/property-search/components/search-filter-sidebar/search-filter-sidebar.spec.ts --include=src/app/features/property-search/components/property-result-card/property-result-card.spec.ts --include=src/app/features/property-search/pages/property-search-page/property-search-page.spec.ts --include=src/app/features/admin/property-management/property-management.spec.ts --include=src/app/features/admin/room-type-management/room-type-management.spec.ts --include=src/app/features/management/dashboard/management-dashboard.component.spec.ts --include=src/app/features/management/inventory/management-inventory.component.spec.ts
```

Result: 17/17 passed across nine files. Coverage includes catalog/assignment HTTP
contracts, exact selection save, system catalog create, admin and owner reachability,
filter emission, URL-to-search round trip and localized result-card display.

Frontend compile command:

```text
npm run build -- --configuration development
```

Result: development bundle completed successfully.

SQL Server validation command:

```text
backend\tools\amenity-sqlserver-validation.ps1
```

Result: SQL Server 2022 catalog/ownership/index/idempotence validation passed. The
verified run applied V81 twice in a disposable database, confirmed exactly eight
active seed rows, verified property and room-type search indexes, rejected duplicate
property assignment and rejected a cross-property room-type assignment. The database
was dropped after verification.

## Migration and recovery

- `V81__amenity_catalog_assignments.sql` is additive. It creates the catalog and two
  assignment tables, filtered access indexes, unique ownership indexes and a
  composite room-type/property integrity key. It does not modify or delete existing
  property, room-type, booking or search data.
- Rollback is application-compatible: older code ignores the new tables. Forward
  recovery should use a new additive migration to correct catalog rows or assignments;
  dropping shared catalog/history tables is not required or authorized.
- No production credential, production database or customer booking was used.

Temporary subscription source snapshots, repository compatibility methods, Maven
test includes and copied Angular i18n sources used only to work around parallel-base
compile gaps were removed before staging.
