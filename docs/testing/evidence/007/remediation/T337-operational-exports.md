# T337 - Non-financial operational exports

## Outcome

Property operators can export four approved CSV datasets: reservations, PII-minimized customers, rooms and housekeeping tasks. Every request is scoped to one assigned property and requires `REPORT:EXPORT`.

## Approved schemas and privacy

- Reservations include pseudonymous reservation/customer references, stay dates, guests, status and room number. Financial amounts and special requests are excluded.
- Customers include a pseudonymous customer reference, masked email, masked phone and account status. Names and raw contact details are excluded.
- Rooms include operational inventory fields only: room reference/number/type, floor, status, housekeeping, maintenance and capacity.
- Housekeeping includes task/room/reservation references, status, pseudonymous assignee reference and lifecycle timestamps. Notes and assignee identity are excluded.
- Status and date filters run on the server. Assigned-property verification occurs before any dataset repository read, including foreign-property denial.
- Each response includes schema version, row count, SHA-256 checksum and server filename; required headers are exposed to the browser.

## Verification

| Check | Result |
|---|---|
| `OperationalExportServiceTest,OperationalExportControllerTest` | PASS, 4/4 |
| `management-inventory.component.spec.ts` | PASS, 2/2; existing maintenance command coverage retained |
| Chromium operational customer export | PASS, 1/1; selected property, masked schema, filename and metadata |
| `npm run build -- --configuration production` | PASS |

Visual evidence: `T337-operational-export-pii-minimized.png`.

No production credentials, real payments, destructive migrations or shared aggregate files were used.
