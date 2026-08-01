# T128 Property Revenue API

## Endpoint

`GET /api/management/reports/property-revenue`

Required query parameters are ISO dates `from` and `to`. Optional filters are `basis`, `propertyId`, `provider`, `method`, `transactionType`, `roomType` and `zoneId`.

The controller resolves the current property from authenticated access, validates an explicitly requested property through `PropertyAccessService`, normalizes local dates to an inclusive/exclusive instant range and returns the shared report result including detail rows and reconciliation issues.

## Security and Validation

- Requires `REPORT` view permission.
- Caller-supplied property IDs are accepted only after authenticated accessible-property validation.
- A system user with no current property must provide an accessible property ID.
- Invalid date ranges, recognition basis and time zone fail before report generation.
- No property ID is accepted in a request body or used as an unvalidated tenant source.

## Automated Validation

Command from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=PropertyRevenueControllerTest' -DforkCount=0 test
```

Result on 2026-08-02: 3 passed, 0 failed, 0 skipped; build succeeded.

No production credential, external provider, migration or real-money operation was used.
