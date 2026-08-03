# T218 Logout And Token Revocation Evidence

Date: 2026-08-03
Capability: AUTH-006 logout and token revocation

## Implemented Contract

- `POST /api/auth/logout` requires the same-origin `X-Logout-Request: 1` marker,
  revokes the presented refresh-token family, invalidates all access tokens issued
  before the logout instant and clears the path-scoped `HttpOnly` refresh cookie.
- The additive `V50__auth_session_revocation.sql` migration adds `users.auth_revoked_at`.
  `JwtAuthFilter` compares the signed token `iat` to that server-owned timestamp and
  returns the stable `SESSION_REVOKED` 401 contract for already-issued tokens.
- Angular logout clears tab-scoped access tokens, user/permission metadata and
  broadcasts a logout event. Chat and notification services deactivate their STOMP
  clients immediately; the server-side refresh family remains revoked even when a
  client is offline.
- Logout is idempotent for a missing/expired cookie: the cookie is still cleared,
  while an authenticated bearer can identify the user when no refresh cookie is
  available.

## Verification

```powershell
backend/.\mvnw.cmd '-Dtest=AuthRefreshTokenIntegrationTest,AuthControllerIntegrationTest,RefreshTokenServiceTest' '-DforkCount=0' test
```

Result: 17/17 passed after the T218 changes. The regression coverage includes
login/refresh behavior, logout cookie clearing, refresh-family reuse rejection after
logout, server rejection of an already-issued access token, malformed/expired token
handling and existing registration/login security checks.

```powershell
frontend/npx ng test --no-watch --coverage=false --include='src/app/core/services/auth-session-lifecycle.spec.ts' --include='src/app/core/interceptors/auth-refresh.interceptor.spec.ts'
cd frontend; npm run build
```

Result: Angular focused lifecycle/refresh suite 5/5 passed and production build
completed. Build warnings are pre-existing CSS budget/CommonJS warnings.

## Safety And Recovery

- V50 is additive and forward-only; no production migration or credentials were used.
- If a deployment exposes a schema mismatch, stop the application rollout, apply V50
  through the normal Flyway pipeline, and resume after the auth integration suite is
  green. Do not remove `auth_revoked_at` or reset refresh-token rows manually.
- Existing sessions are not retroactively revoked by the migration because the new
  column is nullable; only an explicit logout (or a later security policy) sets the
  revocation timestamp.
