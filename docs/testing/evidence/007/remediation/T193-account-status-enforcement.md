# T193 Account Status Enforcement Evidence

Date: 2026-08-02
Base commit: `95309b1` (T192)
Backend profile: focused unit tests plus full `BackendApplication` H2 HTTP integration
Production credentials or provider operations: N/A

## Commands

```powershell
Set-Location backend
.\mvnw.cmd "-Dtest=AccountStatusPolicyTest,CustomUserDetailsServiceAccountStatusTest,JwtAuthFilterAccountStatusTest,AuthServiceTest" test
.\mvnw.cmd "-Dtest=AuthControllerIntegrationTest" test
.\mvnw.cmd "-Dtest=ChatChannelInterceptorTest,NotificationChannelInterceptorTest" test

Set-Location ..\frontend
npm test -- --watch=false --include="src/app/features/auth/login/login.component.spec.ts" --include="src/app/features/auth/login/login-account-status.component.spec.ts" --include="src/app/core/interceptors/error-interceptor.spec.ts"
```

## Results

| Layer / suite | Tests | Passed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| Account policy, user-details reload, JWT filter and auth service | 9 | 9 | 0 | 0 | 0 |
| `AuthControllerIntegrationTest` | 6 | 6 | 0 | 0 | 0 |
| Chat and notification channel interceptors | 14 | 14 | 0 | 0 | 0 |
| Angular login and global error interceptor | 13 | 13 | 0 | 0 | 0 |
| **Total** | **42** | **42** | **0** | **0** | **0** |

## Verified Boundaries

- Only normalized `ACTIVE` accounts are accepted; null, blank, `SUSPENDED`,
  `DISABLED`, `INACTIVE` and `PENDING` are rejected.
- Credential login and social-token issuance cannot generate a JWT for an existing
  non-active account.
- A JWT issued while an account was active returns HTTP 401 with stable code
  `ACCOUNT_DISABLED` on the next authenticated API request after the database status
  changes.
- New chat and notification WebSocket connections reload the account and reject the
  same disabled principal even when the bearer token remains cryptographically valid.
- Angular clears local authentication state and permissions, redirects protected
  routes with the stable reason and shows a bilingual account-status message instead
  of reporting an incorrect password failure.

## Scope Boundary

The application currently uses stateless access JWTs and has no refresh-token or
server-session registry. Therefore status-based invalidation occurs on the next HTTP
authentication reload or realtime reconnect. Proactively disconnecting an already-open
WebSocket and durable token-family revocation remain AUTH-006/CROSS-002 scope.
