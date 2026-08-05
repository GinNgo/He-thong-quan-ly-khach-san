# T336 - Property revenue export UI

## Outcome

The property revenue page now exposes CSV, Excel and PDF only when the signed-in user has `REPORT:EXPORT`. Export responses retain the server filename, canonical checksum and row count instead of replacing them with client-derived metadata.

## Permission and behavior evidence

- The client permission check controls presentation only; the backend export endpoint still requires `REPORT:EXPORT` and verifies assigned-property scope.
- While an export is running, all export buttons are disabled and the live status region announces progress.
- Success displays the server-provided filename, row count and SHA-256 checksum without clearing the loaded report.
- Export failures use a dedicated status message, leaving report loading/error state independent.
- The browser journey downloads the server filename and verifies the 120-row/checksum metadata for the selected property.

## Verification

| Check | Result |
|---|---|
| `FinancialReportingSecurityIntegrationTest` | PASS, 3/3 |
| Revenue report service and property revenue component | PASS, 5/5 |
| Focused Chromium property filter/export/IDOR journey | PASS, 1/1 |
| `npm run build -- --configuration production` | PASS |

Visual evidence: `T336-property-revenue-export-feedback.png`.

No production credentials, real payments, destructive migrations or shared aggregate files were used.
