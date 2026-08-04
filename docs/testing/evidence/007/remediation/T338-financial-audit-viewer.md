# T338 - Financial append-only audit viewer

## Outcome

Financial audit events now have authorized tenant/system read APIs, bounded pagination/export and a dedicated viewer. The existing append-only writer and entity mutation guard remain unchanged.

## Isolation, privacy and policy

- Property users are forced to `PROPERTY_COMMERCE` events from their assigned property set. Foreign property ids and `PLATFORM_BILLING` context return the same privacy-safe not-found response before repository access.
- System administrators can query property and Platform Billing contexts, including explicit property filters.
- Raw provider and idempotency identities are never returned; the viewer receives deterministic truncated SHA-256 references.
- Nested metadata keys for credentials, authorization, account numbers, email, phone, address and full name are redacted again at read time.
- The policy endpoint reports append-only storage, 2,555-day retention, a 10,000-row export maximum and the active redaction policy. No delete endpoint is exposed.
- Search pages are capped at 100 rows and sorted by occurrence/id descending; CSV neutralizes formula-leading values.

## Verification

| Check | Result |
|---|---|
| `FinancialAuditQueryServiceTest` | PASS, 3/3 |
| `financial-audit.component.spec.ts` | PASS, 1/1 |
| Chromium tenant financial-audit viewer | PASS, 1/1; redaction, hashed references and hidden system filter |
| `npm run build -- --configuration production` | PASS |

Visual evidence: `T338-financial-audit-redacted-viewer.png`.

No production credentials, real payments, destructive migrations or shared aggregate files were used.
