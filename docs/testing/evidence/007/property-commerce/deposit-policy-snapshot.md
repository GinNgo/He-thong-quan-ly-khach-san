# T050 Deposit Policy Snapshot Evidence

- Date: 2026-07-31
- Branch: `codex/ui-functional-audit-polish`
- Starting commit: `638bb93`
- Requirement: FR-015
- Scope: Immutable server-owned deposit policy value object only

## Implemented Contract

- Captures property ID, persisted configuration ID/version, policy type/value, authoritative booking total and calculated required deposit.
- Normalizes all money through scale-zero `VndMoney` and exposes only `VND` currency.
- Calculates `NONE`, `FIXED` and `PERCENTAGE` policies without accepting a client-authoritative deposit.
- Caps a fixed deposit at the booking total so the deposit cannot exceed the payable booking amount.
- Rejects a percentage result that would require fractional VND instead of inventing an undocumented rounding policy.
- Revalidates constructor input so a rehydrated/tampered required-deposit value cannot disagree with the captured policy.

## Automated Verification

Command:

```powershell
Set-Location backend
.\mvnw.cmd '-Dtest=DepositPolicySnapshotTest,PropertyPaymentConfigurationServiceTest,VndMoneyTest' test
```

Result: PASS - 14 tests, 0 failures, 0 errors, 0 skipped.

Coverage includes percentage, fixed cap, no-deposit, missing booking total, fractional-VND rejection, tamper rejection, source-configuration mutation, existing configuration validation and VND invariants.

## Other Layers

- Permission verification: N/A; this task introduces no endpoint or mutation service.
- Manual/browser verification: N/A; this task is a backend value object with no reachable UI.
- Schema/configuration migration: N/A for T050. Persistence integration belongs to T051, T052 and T054.
- Rollback/forward recovery: No data migration exists. Reverting the value object and its focused test removes this isolated task; later tasks must not persist snapshots until their aggregate mapping is implemented.
