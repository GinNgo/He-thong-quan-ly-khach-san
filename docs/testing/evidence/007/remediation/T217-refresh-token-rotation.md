# T217 Refresh Token Rotation Evidence

Date: 2026-08-03
Capability: AUTH-005 refresh token and silent renewal

## Implemented Contract

- Successful login responses issue a refresh token through an
  `HttpOnly`, `SameSite=Strict` cookie scoped to `/api/auth`.
- Raw refresh tokens are returned once and never persisted; the database stores
  a unique SHA-256 hash, user owner, family, status and lifecycle timestamps.
- Refresh rotation keeps the original family expiry, serializes access with a
  pessimistic database lock and issues one child token.
- Reuse of a rotated token records detection and revokes the active family.
- Expired sessions are persisted as expired and return stable error code
  `REFRESH_TOKEN_EXPIRED`.
- Refresh requires `X-Refresh-Request: 1`, credentialed requests and the
  configured CORS origin allowlist.
- The Angular client shares one refresh request across concurrent `401`
  responses, stores the renewed access token and retries each protected call.
- Authentication endpoints are excluded from refresh retry to prevent loops.
- Architecture and production cookie requirements are documented in
  `docs/architecture/refresh-token-rotation.md`.

## Executed Verification

### Backend service and HTTP

```powershell
backend/.\mvnw.cmd "-Dtest=RefreshTokenServiceTest,AuthRefreshTokenIntegrationTest,AuthControllerIntegrationTest,AuthExceptionIntegrationTest" test
```

Result: 23/23 passed.

Coverage includes hash-only issuance, rotation, replay detection, family
revocation, expiry, missing request marker, login regression, malformed/expired
access tokens and stable authentication errors.

### Angular

```powershell
npm test -- --watch=false --include=src/app/core/interceptors/auth-refresh.interceptor.spec.ts --include=src/app/core/interceptors/jwt-interceptor.spec.ts --include=src/app/core/services/auth-session-lifecycle.spec.ts --include=src/app/core/auth/access-token-session.store.spec.ts
```

Result: 10/10 passed across four focused spec files.

The concurrency test sends two protected requests, returns `401` for both,
asserts exactly one credentialed refresh request, then verifies both calls retry
with the same renewed bearer token.

### Production build

```powershell
npm run build
```

Result: passed. Existing non-blocking warnings remain for one component CSS
budget and the STOMP/SockJS CommonJS dependencies.

## Recovery and Residual Scope

- Migration `V49__refresh_token_rotation.sql` is additive. Forward recovery is
  to correct configuration/application code and apply a later migration; it does
  not require destructive rollback.
- Set `AUTH_REFRESH_COOKIE_SECURE=true` behind production HTTPS. Local HTTP uses
  `false` only for development and tests.
- Explicit logout/session revocation, realtime disconnect and invalidation of an
  already-issued access token are AUTH-006/T218.
- No production credentials, external provider traffic or real-money path was
  used.
