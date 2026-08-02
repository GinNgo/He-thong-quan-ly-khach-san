# T185 Claim And Subscription HTTP Evidence

Date: 2026-08-02
Base commit: `0eb85d315a2850f58df4f54d9b31a2aa397fae44`
Profile: default MVC test slice
Database: N/A for controller slices; focused service tests use Mockito repositories
Production credentials or real-money operations: N/A

## Commands

```powershell
Set-Location backend
.\mvnw.cmd -q '-Dtest=PropertyClaimControllerIntegrationTest,SubscriptionControllerIntegrationTest' test
.\mvnw.cmd -q '-Dtest=PropertyClaimServiceTest,SubscriptionCatalogServiceTest' test
```

## Result

| Suite | Tests | Passed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| `PropertyClaimControllerIntegrationTest` | 7 | 7 | 0 | 0 | 0 |
| `SubscriptionControllerIntegrationTest` | 6 | 6 | 0 | 0 | 0 |
| `PropertyClaimServiceTest` | 1 | 1 | 0 | 0 | 0 |
| `SubscriptionCatalogServiceTest` | 2 | 2 | 0 | 0 | 0 |
| **Total** | **16** | **16** | **0** | **0** | **0** |

The HTTP suites execute real Spring Security filters and controller serialization. They cover unauthenticated denial, claim approval authority, principal-owned claim identity, malformed JSON rejection, claim lifecycle response states, public plan serialization, authenticated subscription/features/usage reads and entitlement usage serialization. The focused service tests cover quota rollback plus catalog and usage lifecycle calculation.

## Harness Repairs

- Pinned both `@WebMvcTest` classes to `BackendApplication`, eliminating ambiguous nested `@SpringBootConfiguration` discovery.
- Mocked only `TenantFilterInterceptor` in the MVC slice because it requires JPA infrastructure outside the controller-test boundary; its `preHandle` is explicitly configured to continue the request.
- Preserved the existing `SubscriptionCatalogService` mock and unauthenticated usage assertion while extending deterministic response fixtures.
- Kept the claim response entity behavior unchanged; safe claim DTO/privacy remediation remains PROP-SUB-013 and is not claimed by T185.

## Checksums

| Artifact | SHA-256 |
|---|---|
| `PropertyClaimControllerIntegrationTest.java` | `071d6eeddcafc30daa53874925506fb8a1889f254e2b1042913cb6e23b98d0e1` |
| `SubscriptionControllerIntegrationTest.java` | `c9336acee10a75628bcfce28af14eadc565fd8c2d3963359f4de57d4a81c603b` |
| `PropertyClaimServiceTest.java` | `17ab743d9ded2ead2a679bb94b16c5a4a0dfe62e2f83c7aad03a12e38a62e97c` |
| `SubscriptionCatalogServiceTest.java` | `57c84ca54824ed168501cb308ca58ff7b3fb6d9b7b5aae92c58c5988d64bb410` |
| Claim controller Surefire XML | `077057c016beec6440265ae6785f151359fcf7c261abe9020a4d2024e6216c79` |
| Subscription controller Surefire XML | `8820408a512ad3ff2b8828a92772994f1d0f7713e665bcbaa94e8bfbb880ad31` |
| Claim service Surefire XML | `5d4b5939323998f45e772a93374ab77b7600ae0bf8bfbb4df2369bce58f3a728` |
| Catalog service Surefire XML | `109c47a121f491f55a98039087e50366ad7d06b6fa1fd85f80ed26c43a7fa4d1` |

## Remaining Property And Subscription Scope

- Claim input validation, safe DTO serialization and approval/rejection state-machine work remain PROP-SUB-010 through PROP-SUB-013.
- Legacy versus Platform Billing entitlement ownership remains PROP-SUB-019 through PROP-SUB-021.
- Production payment, provider credentials and unapproved downgrade/refund policy remain outside T185.
