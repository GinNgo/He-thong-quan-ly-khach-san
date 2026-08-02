# T189 Stable Error Envelope And Retry Semantics Evidence

Date: 2026-08-02
Base commit: `c116cfd6f2cbb9285b6b48754ef39528183f3cc1`
Backend profile: `test`
Backend database: H2 `create-drop`; Flyway disabled
Frontend runner: Angular test builder / Vitest
Production credentials or provider operations: N/A

## Commands

```powershell
Set-Location backend
.\mvnw.cmd -q '-Dtest=FinancialErrorContractTest,AuthExceptionIntegrationTest,AuthControllerIntegrationTest,PropertyPaymentControllerTest,PlatformPaymentCallbackControllerTest,InvoiceAccessIntegrationTest,EndpointSecurityArchitectureTest' test

Set-Location ..\frontend
.\node_modules\.bin\ng.cmd test --watch=false --no-progress `
  --include "src/app/shared/financial/financial.models.spec.ts" `
  --include "src/app/core/interceptors/financial-request.interceptor.spec.ts" `
  --include "src/app/core/interceptors/error-interceptor.spec.ts"
```

## Result

| Layer / suite | Tests | Passed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| `FinancialErrorContractTest` | 2 | 2 | 0 | 0 | 0 |
| `AuthExceptionIntegrationTest` | 7 | 7 | 0 | 0 | 0 |
| `AuthControllerIntegrationTest` | 4 | 4 | 0 | 0 | 0 |
| `PropertyPaymentControllerTest` | 4 | 4 | 0 | 0 | 0 |
| `PlatformPaymentCallbackControllerTest` | 2 | 2 | 0 | 0 | 0 |
| `InvoiceAccessIntegrationTest` | 6 | 6 | 0 | 0 | 0 |
| `EndpointSecurityArchitectureTest` | 1 | 1 | 0 | 0 | 0 |
| Angular shared error models/interceptors | 14 | 14 | 0 | 0 | 0 |
| **Total** | **40** | **40** | **0** | **0** | **0** |

The backend assertions cover 400, 401, 403, 404, 409 and 500 responses, financial error
compatibility, validation-ready field errors, correlation propagation/sanitization, security and
permission responses, secret-safe unexpected failures and existing payment/invoice error consumers.
The Angular assertions cover envelope recognition, retry/current-state preservation, correlation
and idempotency headers and stable forbidden-route reason codes.

## Contract Repairs

- Added one shared `ApiErrorResponse` for controller advice, Spring Security and permission
  interception: `status`, `code`, `message`, `correlationId`, `fieldErrors`, `retryable`,
  `currentState` and `path`.
- Added bounded correlation-id normalization and the matching `X-Correlation-ID` response header.
- Classified validation, malformed body, missing/invalid parameter, conflict, concurrency,
  integrity, authorization, not-found, unsupported method/media and unexpected failures.
- Redacted unexpected exception messages and kept generic 500 errors non-retryable so legacy
  mutations are never replayed blindly.
- Converted local authentication and partner-registration error paths away from raw text bodies.
- Kept provider callback acknowledgement bodies unchanged where an external provider protocol
  requires a provider-specific response.
- Completed `docs/audit/system/FULL_SYSTEM_ERROR_EXPECTATION_CATALOG.md` with client retry and
  current-state rules for common and financial error codes.

## Checksums

| Artifact | SHA-256 |
|---|---|
| `ApiErrorResponse.java` | `48faad45e85287188866c3df2a0de6a09a52ba1fb352c9cb0bf6f19841bacba8` |
| `CorrelationIdSupport.java` | `3580350ca9b1c3b379655f7e77c51a3ffef193946f8ccf2a9ee51c790e18d7ca` |
| `GlobalExceptionHandler.java` | `86e604ef6cd5a775350616d4d0d29b4cefd90519742a8a03289f8a5e330e88d7` |
| `PermissionInterceptor.java` | `0badcc31a57001898955eab73f52c945436b4a2866cc61be9f131b4e53dee1ec` |
| `AuthExceptionIntegrationTest.java` | `1dd985ae868691e56b712b3b4bde99bf8885de525046e5e11bafc923eea05421` |
| `financial.models.ts` | `421e79df315b1b8744bb66f80e827f60675b6da80641400e61b33e13e032c5c9` |
| `financial-request.interceptor.ts` | `8f69c672213213d4db0061b558ca96817733d5b027c0f52839f0bc47e1d0b4b6` |
| `error-interceptor.ts` | `1aa156811ca1521e31451903f12462d3aef53f83fd8bcb630618db658fad119c` |
| Error expectation catalog | `22e4fbd7b9931caed60c0a337cd241cd79e4c491129f6725a4d3f2b48658da9e` |
| Financial contract Surefire XML | `affb684176b46f195e6bea787644a47b53647b51af9e9533902a903fd9ef0db0` |
| Auth exception Surefire XML | `da8792b18b0e0b4b39a8dc7cd378e98f3477b652817fef84bdea28765ac04d` |
| Auth HTTP Surefire XML | `ae42219bd6f599a9bde9bb6a85a1b4a2d06c1a722cecf1714f1be5730dbc9580` |

## Remaining Cross-Cutting Scope

- T190/CROSS-041 still owns structured correlation across logs, STOMP, jobs, metrics and health.
- T191/CROSS-033 still owns component-level cancellation, duplicate-submit and replay behavior for
  every mutation; T189 defines the response contract but does not invent idempotency support.
- UI loading/error/empty-state completion remains CROSS-032.
