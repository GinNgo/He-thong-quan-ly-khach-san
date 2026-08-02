# T188 Cross-Cutting HTTP And WebSocket Evidence

Date: 2026-08-02
Base commit: `a5237516bf9c23f7950ba834a0feffef2cd5b5e7`
Profile: `test`
Database: H2 `create-drop`; Flyway disabled
WebSocket transport: local random-port SockJS/STOMP
Production credentials or external messages: N/A

## Commands

```powershell
Set-Location backend
.\mvnw.cmd -q '-Dtest=ChatControllerIntegrationTest,NotificationControllerIntegrationTest,NotificationWebSocketIntegrationTest' test
.\mvnw.cmd -q '-Dtest=ChatControllerTest,ChatChannelInterceptorTest,NotificationChannelInterceptorTest' test
```

## Result

| Suite | Tests | Passed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| `ChatControllerIntegrationTest` | 4 | 4 | 0 | 0 | 0 |
| `NotificationControllerIntegrationTest` | 5 | 5 | 0 | 0 | 0 |
| `NotificationWebSocketIntegrationTest` | 1 | 1 | 0 | 0 | 0 |
| `ChatControllerTest` | 2 | 2 | 0 | 0 | 0 |
| `ChatChannelInterceptorTest` | 6 | 6 | 0 | 0 | 0 |
| `NotificationChannelInterceptorTest` | 6 | 6 | 0 | 0 | 0 |
| **Total** | **24** | **24** | **0** | **0** | **0** |

The integration suites execute the real `BackendApplication`, Spring Security, MVC, JPA,
an embedded WebSocket server and the production STOMP interceptors. They cover unauthenticated
and forbidden HTTP access, notification ownership, tenant-scoped support history/reply/assignment,
denied-action audit, an authenticated admin STOMP subscription, missing/invalid destination
authorization, personal queues and client-publish denial.

## Harness Repairs

- Pinned all three failing integration suites to `BackendApplication`, eliminating ambiguous
  nested `@SpringBootConfiguration` discovery.
- Added only a non-secret, annotation-scoped property-payment encryption key.
- Kept the notification WebSocket test on an isolated H2 database, local random port and
  deterministic E2E test accounts.
- Re-ran controller and both channel-interceptor suites so HTTP/STOMP positive and negative
  permissions are evidence-backed, not inferred from the successful application startup.

## Checksums

| Artifact | SHA-256 |
|---|---|
| `ChatControllerIntegrationTest.java` | `96df24d96bce9b66023f2b810cdec50292585d5772f25150e2283a0d64ff6843` |
| `NotificationControllerIntegrationTest.java` | `f8ac168dd9aaed4c7a89387c45ce48a6adaa7eb461b581b0a7d0d9c15f8d8168` |
| `NotificationWebSocketIntegrationTest.java` | `aff63a3e294ef2159e51586a1efa629a76fb4c502884ddbe3cd9c25f4c2d3178` |
| `ChatControllerTest.java` | `925241fe5a2057029cb5e821033372e1170bcc921b0b5e56baf2e10f4092c4e6` |
| `ChatChannelInterceptorTest.java` | `f147344ff27b6e7d81e30565425d4d8777d3b945e7b09b24eb357e0e7f257633` |
| `NotificationChannelInterceptorTest.java` | `0de529845ef828d0c3a655a76671895165155eae4d397083f01990f621dd1679` |
| Chat HTTP Surefire XML | `b14b819213ffb3a90118912c79999b13ece4c70ca262c45d93b9034434fb8d99` |
| Notification HTTP Surefire XML | `a72ba28a0e6a511d0b6ec35d283518c4fdc80c986966898fbd815cb66772d432` |
| Notification WebSocket Surefire XML | `8b72e6bce31d595189f478d98f1f4d469c86aceee01499ef9d8a0d883f07a2ec` |
| Chat controller Surefire XML | `2805787b214c5a59f78bf45aa08a6e31999842e50fee0900dc57d71af16d96d5` |
| Chat interceptor Surefire XML | `6fa07abfc0450f7229d2e3b3b522d438a3da64b1fab8485aa37084d8d64e8b30` |
| Notification interceptor Surefire XML | `3072df94633d284b7359f2873c77667cb4ac855067159478aa47940c4f942c29` |

## Remaining Cross-Cutting Scope

- Customer notification inbox/UI, reconnect replay and expired-token integration remain
  CROSS-002 through CROSS-005 work.
- Real two-user browser chat, duplicate-safe delivery/read state and close/attachment/SLA work
  remain CROSS-011, CROSS-015 and CROSS-016.
- Production SMTP, Facebook and Zalo credentials remain blocked external scope; no real message
  or provider was used by T188.
