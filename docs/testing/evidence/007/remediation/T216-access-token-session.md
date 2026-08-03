# T216 Access Token Session Evidence

Date: 2026-08-03
Capability: AUTH-004 access-token session and expiry handling

## Implemented Contract

- Access tokens are stored in tab-scoped `sessionStorage`, not persistent
  `localStorage`.
- Valid legacy local-storage tokens migrate once without changing their JWT
  expiry; malformed or expired legacy tokens are deleted.
- JWT payload inspection requires a finite `exp` claim and applies a five-second
  expiry skew before treating a token as usable.
- The HTTP interceptor never attaches malformed or expired bearer tokens.
- `AuthService` clears token, user metadata and in-memory authentication state at
  the expiry instant, on explicit logout and when restoring stale state.
- `ForbiddenComponent` uses the centralized logout lifecycle rather than deleting
  one token key directly.
- The session decision and residual cookie/refresh boundary are documented in
  `docs/architecture/access-token-session-lifecycle.md`.

## Executed Verification

### Angular

```powershell
npm test -- --watch=false --include='src/app/core/auth/access-token-session.store.spec.ts' --include='src/app/core/interceptors/jwt-interceptor.spec.ts' --include='src/app/core/services/auth-session-lifecycle.spec.ts'
```

Result: 8/8 passed across three focused spec files.

Coverage includes valid storage, legacy migration, malformed/expired cleanup,
Authorization header inclusion/exclusion, stale restoration, explicit logout and
automatic cleanup at the JWT expiry instant.

### Backend HTTP

```powershell
backend/.\mvnw.cmd "-Dtest=AuthControllerIntegrationTest,AuthExceptionIntegrationTest" test
```

Result: 16/16 passed.

The suites include public login regression plus protected-endpoint rejection for
malformed tokens, correctly signed expired tokens and tokens issued to accounts
that become disabled.

### Browser

```powershell
$env:LUXESTAY_E2E_WEB_URL='http://localhost:4201'
npx playwright test e2e/credential-login-logout.spec.ts e2e/access-token-session.spec.ts --project=chromium
```

Result: 2/2 Chromium journeys passed.

The journeys prove tab-scoped token creation and complete logout cleanup, plus
redirect and artifact cleanup when an expired legacy session attempts to open a
protected customer route.

## Residual Scope

- Rotating refresh tokens, single-flight silent renewal and secure HttpOnly
  refresh cookies are AUTH-005/T217.
- Server-side session-family revocation and realtime disconnect on logout are
  AUTH-006/T218.
- No production credentials, provider traffic or real-money path was used.
