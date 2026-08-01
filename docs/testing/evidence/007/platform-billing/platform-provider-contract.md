# T102 Platform Provider Contract

## Scope

- Added a Platform Billing callback contract test using the real signed simulator adapter and callback orchestration.
- Verified the callback binds to the system-owned merchant, exact VND amount and platform order reference.
- Verified accepted evidence creates a `SUBSCRIPTION_PURCHASE` Platform Billing transaction before subscription application.

## Negative Cases

- Invalid callback signature returns `CALLBACK_SIGNATURE_INVALID`.
- A correctly signed callback for another merchant returns `CALLBACK_MERCHANT_MISMATCH`.
- A correctly signed callback for another amount returns `CALLBACK_AMOUNT_MISMATCH`.
- A correctly signed callback for another order reference returns `CALLBACK_REFERENCE_MISMATCH`.
- Every rejected case leaves the attempt pending, the order unpaid and creates no ledger/order mutation.

## Automated Validation

Commands from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=PlatformProviderContractTest' -DforkCount=0 test
.\mvnw.cmd '-Dtest=PropertyProviderContractTest,PlatformProviderContractTest' -DforkCount=0 test
```

Result on 2026-08-01:

- `PlatformProviderContractTest`: 4 passed, 0 failed, 0 errors, 0 skipped.
- Shared property/platform provider contracts: 20 passed, 0 failed, 0 errors, 0 skipped.
- VNPay, MoMo, ZaloPay and simulator adapter bindings remain green alongside the platform-specific system-merchant contract.

## Environment and Recovery

- Tests use generated simulator HMAC evidence and synthetic system merchant identifiers only.
- No sandbox/production network call, credential, database mutation or real-money operation is used.
- Schema and rollback: N/A; T102 changes tests/evidence only.
