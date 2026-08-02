# T184 Auth HTTP Integration Evidence

Date: 2026-08-02
Base commit: `fb69a900d7127b9518bc99281a2994b601b7d9a0`
Profile: `test`
Database: H2 `create-drop`; Flyway disabled
Production credentials or real-money operations: N/A

## Command

```powershell
Set-Location backend
.\mvnw.cmd -q -Dtest=AuthControllerIntegrationTest test
```

## Result

- Tests: 4
- Passed: 4
- Failures: 0
- Errors: 0
- Skipped: 0
- Surefire time: 103.784 seconds

The suite executes a deterministic registration/login journey, wrong-password denial, request-validation rollback and invalid-token denial through the full `BackendApplication` MockMvc context.

The constructor-selection repair was also checked with:

```powershell
.\mvnw.cmd -q '-Dtest=PropertyRefundServiceTest,PlatformRefundServiceTest' test
```

Result: 5/5 passed (3 Property Commerce and 2 Platform Billing refund service tests).

## Harness Repairs

- Pinned `@SpringBootTest` to `BackendApplication` so nested test configurations are not selected.
- Added only a non-secret test property for property-payment encryption.
- Marked the production constructors on the refund orchestrator and both refund services for Spring injection; package-private clock constructors remain available to unit tests.
- Replaced timestamp fixtures with fixed usernames and targeted cleanup.

## Checksums

| Artifact | SHA-256 |
|---|---|
| `AuthControllerIntegrationTest.java` | `625004165b8fc8bc4d4b1937a62dadedb69b04ea43d90c18b6d609c5ae3126e6` |
| `RefundProviderOrchestrator.java` | `44a38c412cb6550f91261c1b6056f020b6910aa37f6659baf84a70636a8daa15` |
| `PropertyRefundService.java` | `d00c0153a7827ea4276cf58ba11749699e511931e9491b18f71ab429af7b79c9` |
| `PlatformRefundService.java` | `b60ba7b19b3a2af8274b3ab76ecf00b4d9082ad7cc3daa4d33b553ea2039beba` |
| Surefire XML | `fb00cd39dbf18a657891e72f6cfb4e603ae8b8fdf6a652ef4e959f01909d885c` |

## Remaining Auth Scope

- Duplicate registration normalization and stable conflict responses remain in AUTH-001.
- Disabled-account enforcement remains the P0 AUTH-003 task.
- Browser login/logout, refresh/revocation and production OAuth/SMTP evidence remain separate tasks or external gates.
