# T265 - Service catalog CRUD UI

## Scope and result

- Branch/worktree: `codex/property-operations` in the dedicated external worktree.
- Starting commit: `fdd0300`.
- Result: `/admin/services` and `/management/services` share a complete property-aware create/edit/deactivate experience.

## Behavior evidence

- The screen resolves the active property, loads its tenant services plus read-only system templates and exposes loading, empty, retry, saving and API-error states.
- Create/edit forms provide Vietnamese and English names/descriptions, normalized code, integer VND price and supported status fields.
- Create payloads do not trust or embed `hotelId`; selected property scope is sent separately through the client contract.
- System templates remain read-only. Tenant service actions are independently visible for `HOTEL_SERVICE:CREATE`, `UPDATE` and `DELETE`.
- Deactivation requires explicit confirmation and reuses the catalog lifecycle endpoint rather than removing the row from local state optimistically.
- The management shell exposes a permission-aware `/management/services` route to the same component.

## Verification

```text
npm test -- --watch=false --include=src/app/features/admin/service-management/service-management.spec.ts
```

- PASS: 6 tests, 0 failures.
- Covers property-scoped load, valid create payload, invalid input, update, confirmed deactivation, API failure and denied controls.
- The Angular compile used temporary public-i18n compatibility sources required by unrelated parallel client work; they were removed before staging.

## Migration and recovery

- Database migration: N/A.
- Forward recovery: the screen can be removed from the management route/menu without changing catalog data or API behavior.
- No production data or credentials were used.
