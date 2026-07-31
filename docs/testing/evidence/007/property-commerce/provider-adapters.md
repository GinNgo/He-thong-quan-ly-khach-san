# T055 Shared Property Payment Provider Adapters

## Scope

- Added stateless shared-SPI adapters for VNPay, MoMo, ZaloPay and the signed simulator.
- Added a case-insensitive adapter registry that fails closed for unknown providers.
- Extended the verification request with server-owned credentials, attempt expiry and callback receipt time.
- Kept credentials and callback signatures redacted from the verification request string representation.
- Added normalized callback output for provider event identity, transaction identity, reference, amount, currency, occurrence time and provider status metadata.

## Verification Rules

- VNPay verifies the HMAC-SHA512 canonical callback, merchant, integer VND amount, currency, reference, success status and provider transaction identity.
- MoMo verifies the official HMAC-SHA256 callback field order, partner code, amount, reference and successful transaction identity.
- ZaloPay verifies the HMAC-SHA256 MAC over the exact raw `data` value, then validates app identity, amount, reference and transaction identity.
- The simulator verifies a deterministic HMAC-SHA256 payload and only accepts explicit terminal callback statuses.
- All adapters bind provider data to the persisted expected merchant, amount, currency and reference before returning an accepted result.
- Expired attempts are rejected at the shared verification boundary.
- Missing credentials return retryable `PROVIDER_UNAVAILABLE`; signature and binding failures are non-retryable stable financial errors.

## Automated Validation

Command from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=PaymentProviderAdaptersTest,PaymentEnvironmentGuardTest,PropertyPaymentAttemptServiceTest' -DforkCount=0 test
```

Final result:

- Tests run: 15
- Failures: 0
- Errors: 0
- Skipped: 0
- Build: SUCCESS

## Safety and Remaining Work

- No external provider endpoint, production credential, production merchant or real-money transaction was used.
- Provider secrets remain server-side inputs to verification and are not persisted or logged by these adapters.
- Property-scoped vault/secret-reference storage and sandbox credential provisioning remain deployment configuration work.
- T061 remains open for the complete cross-provider signature, merchant, amount, currency, reference and expiry contract matrix.

## Recovery

- Rollback is application-only: remove adapter registration and keep provider callbacks disabled.
- Existing attempts and financial evidence require no data rewrite because this task adds no migration and performs no ledger mutation.
