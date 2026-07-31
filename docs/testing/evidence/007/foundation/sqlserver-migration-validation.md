# Feature 007 SQL Server Migration Validation

- Date: 2026-07-31 (Asia/Saigon)
- Worktree HEAD fingerprint: `ded38853bc1af3724512a3545713df6b64807ec1`
- SQL Server instance: `.\MSSQLSERVER01`
- SQL Server version: `17.0.1125.2`
- Actor/role: local implementation validation; no production role or credentials
- Database scope: two uniquely named isolated databases, dropped after each run
- Real money/provider calls: N/A

## Commands

```powershell
Set-Location backend
./tools/feature007-sqlserver-validation.ps1 -WithoutLegacyFinancialData
./tools/feature007-sqlserver-validation.ps1
```

## Fixtures

1. Clean Feature 007 fixture: required legacy application ownership tables exist, but there are no legacy payment or invoice rows.
2. Upgrade fixture: one unambiguous property payment and one paid legacy invoice map to one reservation/property.
3. Negative preflight fixture: one orphan payment references a missing reservation.

The repository's historical V1 migration still assumes the pre-Flyway application schema exists. This validation therefore proves the additive V21-V29 contract on clean and upgrade base fixtures; it does not claim that an entirely empty database can execute V1 onward. That historical bootstrap gap remains visible for the final clean-database release gate.

## Results

- Positive preflight passed for both clean and upgrade fixtures.
- V21-V29 executed successfully on SQL Server with explicit deterministic session settings.
- Re-executing V21-V29 succeeded without duplicate ledger, invoice, permission or schema effects.
- Clean fixture produced zero backfilled property ledger/invoice rows.
- Upgrade fixture produced exactly one `LEGACY:payments:1` ledger row and one `LEGACY-fixture-invoice-1` invoice row.
- All 11 financial permission functions were present; the super-admin seed inserted four platform/readiness permissions.
- The orphan fixture returned SQL error `51007` and performed no migration/backfill assignment.
- Both isolated validation databases were dropped after validation.

## Recovery

V21-V29 are additive. Forward recovery is to fix the failing preflight condition or add a new migration; do not delete financial evidence or edit an applied migration in production. For a failed deployment, disable Feature 007 writes, retain the created tables/evidence, restore from a verified backup only when required by the approved production runbook, and rerun after reconciliation.

## Artifact Checksums (SHA-256)

| Artifact | SHA-256 |
|---|---|
| `feature007-sqlserver-validation.ps1` | `58178CEF1116B19D61271F98E3C1B6ADF2D83D32F5BAECFFDD2ED16020C2863F` |
| `feature007_financial_preflight.sql` | `E9955F86B2D87BDE27266E3CDD7FD6D83E8B8114A6A485C7DC42A7F757F2654D` |
| `V21__property_commerce_foundation.sql` | `1A03C558F4161D0FAAFB4A9E025366EB9E6C76E12CA591289F1E66B2D6E0C0C6` |
| `V22__property_checkout_invoice.sql` | `12B330F839AA4ADF8B6026453A64D244E1E0B1F756DB87B5BB2115E18DB6A7B4` |
| `V23__property_refund_audit.sql` | `A557DF4F3DBCE037C8B97E0F6A29B867F77E84B79266997AD7B3F27E151CEF12` |
| `V24__platform_billing_foundation.sql` | `2541CD7FE17AE1A766B65AE62781B0276AC9CC31EEC81FA080CF88934E1DEFF9` |
| `V25__platform_contract_refund.sql` | `F587EE027A9555C4F48FDCC15D8319427A0030D9AD68A895DDE6F5640A49C181` |
| `V26__financial_context_backfill.sql` | `68AA519958D523E0A5832EBA8B8B3AE299CE0DA724469227738C3FF0B8FC09AB` |
| `V27__financial_integrity_indexes.sql` | `0BD30C7014267181C5B1A37FD16D6353C25015DCAA6B524F3CC55869E856DFC4` |
| `V28__financial_permissions.sql` | `9D350272F7BD2AF178D82226550E0291DE247540A96F768D2E9E78396D55DDD1` |
| `V29__financial_idempotency.sql` | `33ED1C5164D9C9E97A9186FFFC4B2FD5AF7B268FDAB41FCE0A20F319D4E90371` |
