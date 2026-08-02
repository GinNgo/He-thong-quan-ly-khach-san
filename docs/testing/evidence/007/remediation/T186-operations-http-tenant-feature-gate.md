# T186 Operations HTTP, Tenant And Feature-Gate Evidence

Date: 2026-08-02
Base commit: `19386f0d211c6cbfbe3d9a307ea38e13bf4721a1`
Profile: `test`
Database: H2 `create-drop`; Flyway disabled
Production credentials or real-money operations: N/A

## Command

```powershell
Set-Location backend
.\mvnw.cmd -q '-Dtest=AdminUserControllerIntegrationTest,TenantIsolationIntegrationTest,FeatureGateIntegrationTest,EndpointSecurityArchitectureTest' test
```

## Result

| Suite | Tests | Passed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| `AdminUserControllerIntegrationTest` | 3 | 3 | 0 | 0 | 0 |
| `TenantIsolationIntegrationTest` | 10 | 10 | 0 | 0 | 0 |
| `FeatureGateIntegrationTest` | 3 | 3 | 0 | 0 | 0 |
| `EndpointSecurityArchitectureTest` | 1 | 1 | 0 | 0 | 0 |
| **Total** | **17** | **17** | **0** | **0** | **0** |

The suites execute the real `BackendApplication`, Spring Security, MVC interceptors, JPA repositories and H2 persistence. They cover staff-list/create permission behavior, unauthenticated management denial, room and room-type cross-tenant updates, housekeeping own/cross-property/super-admin behavior, customer invoice/payment IDOR denial and feature-gate allow/deny responses.

## Harness Repairs

- Pinned all three Spring integration suites to `BackendApplication`, eliminating nested `@SpringBootConfiguration` discovery collisions.
- Added only a non-secret, annotation-scoped property-payment encryption key so the test context cannot depend on production environment variables.
- Reused the production constructor-selection repair for `RefundProviderOrchestrator` from T184; successful full-context startup revalidates that dependency graph.
- Preserved the existing booking-payment idempotency header in the tenant IDOR test.

## Checksums

| Artifact | SHA-256 |
|---|---|
| `AdminUserControllerIntegrationTest.java` | `069bbacb19692fa8f81610f445cd63e6159fd457c9e244a7fcd435f70c2af7aa` |
| `TenantIsolationIntegrationTest.java` | `750e44dc5634af99ab8d0783552319dbfe4251ce1b5cef177a21620ed0fe1413` |
| `FeatureGateIntegrationTest.java` | `45fc109fa9aef94cc2997b35ac33537017b238e8130f6ba09cfd20ccd38a7b18` |
| `RefundProviderOrchestrator.java` | `44a38c412cb6550f91261c1b6056f020b6910aa37f6659baf84a70636a8daa15` |
| Admin-user Surefire XML | `848a6f4278726ea07afcc33ef90b6eb62950f86f9ac90cf41722d64873845bee` |
| Tenant-isolation Surefire XML | `6cf74cd21a54bdccb45354c2738304409d0604ccffa48520798ccd5a62c18ba6` |
| Feature-gate Surefire XML | `5bb3f3971dbdc967ff4f17d57486359c97e19f5cdb3919def470d5f9bd91ce70` |
| Security-architecture Surefire XML | `9b59606c45fbfc95a34c7e8bcda0bcb2cbd013357a5551774918339071b9d640` |

## Remaining Property Operations Scope

- Staff DTO/password/invitation, per-property quota and lifecycle defects remain PROP-OPS-001 through PROP-OPS-005.
- Action-mask parity remains PROP-OPS-026 and PROP-OPS-029; passing broad role/feature tests does not claim that gap complete.
- Housekeeping queue, dedicated permission, assignment, replay and UI remain PROP-OPS-023 and PROP-OPS-024.
- No production database, property cleanup, media migration or production credential was used.
