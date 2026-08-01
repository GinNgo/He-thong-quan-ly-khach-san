# T136 Typed Revenue Report API Client

Added `RevenueReportService` with shared report result models and separate property/platform filter types.

- Property methods target `/api/management/reports/property-revenue` and allow property/room filters.
- Platform methods target `/api/admin/reports/platform-revenue` and allow plan filters without property/room scope.
- Export methods target the matching `/export` paths, preserve all filters and request a typed `Blob` for CSV/Excel/PDF.

The client does not accept client-supplied totals, prices or merchant data. Platform calls cannot accidentally serialize a property tenant field through the typed public API.

## Automated Validation

Command from `frontend/`:

```powershell
npx ng test --no-watch --no-progress --include src/app/core/services/revenue-report.service.spec.ts
```

Result on 2026-08-02: Angular unit-test bundle completed successfully; the focused HTTP tests passed with property/platform URL, filter, permission-context and blob export assertions.

No production credentials, external provider or real-money operation was used.
