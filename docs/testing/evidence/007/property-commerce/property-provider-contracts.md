# T061 Property Provider Contracts

## Scope

- Added one parameterized contract suite for VNPay, MoMo, ZaloPay and the signed simulator.
- Uses deterministic local fixtures and provider-specific signing formats.
- Exercises the same shared adapter SPI used by Property Commerce callback orchestration.
- Makes no network request and uses no real merchant, production secret or real-money transaction.

## Contract Matrix

Each provider must:

- accept a correctly signed callback and normalize provider, event, amount, currency and reference evidence;
- reject an invalid signature with `CALLBACK_SIGNATURE_INVALID`;
- fail closed with `PROVIDER_UNAVAILABLE` when server credentials are missing;
- reject merchant mismatch with `CALLBACK_MERCHANT_MISMATCH`;
- reject expected or normalized callback amount mismatch with `CALLBACK_AMOUNT_MISMATCH`;
- reject expected or normalized callback currency mismatch with `INVALID_CURRENCY`;
- reject expected or normalized callback reference mismatch with `CALLBACK_REFERENCE_MISMATCH`;
- reject at the exact expiry instant with `ATTEMPT_EXPIRED` while accepting one nanosecond before expiry.

The fixtures cover VNPay HMAC-SHA512 canonical query signing, MoMo HMAC-SHA256 ordered fields, ZaloPay HMAC-SHA256 raw `data`, and simulator HMAC-SHA256 canonical payload signing.

## Automated Validation

Command from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=PropertyProviderContractTest,PaymentProviderAdaptersTest' -DforkCount=0 test
```

Final result:

- New provider contract tests: 16
- Existing adapter regression tests: 6
- Total tests: 22
- Failures: 0
- Errors: 0
- Skipped: 0
- Build: SUCCESS

## Remaining Work

- T062 remains open for database-backed replay and concurrent callback delivery.
- T063 remains open for database-backed manual confirmation permission, audit and tenant isolation.
- Production payment remains disabled and requires separate readiness approval.

## Recovery

- This task changes tests and evidence only; there is no runtime or database rollback.
