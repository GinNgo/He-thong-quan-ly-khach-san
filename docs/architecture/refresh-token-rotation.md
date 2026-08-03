# Refresh Token Rotation

Date: 2026-08-03
Decision scope: AUTH-005 / T217

## Decision

Authenticated browser sessions use two credentials with different exposure and
lifecycle rules:

- the access token remains tab-scoped in `sessionStorage` and is attached as a
  bearer token only while its JWT expiry is valid;
- the refresh token is a 32-byte random value returned only in an `HttpOnly`,
  `SameSite=Strict` cookie scoped to `/api/auth`;
- only the SHA-256 hash of the refresh token is stored in the database;
- every login starts a token family and every successful refresh rotates to one
  child token without extending the family's original expiry;
- replay of a rotated token records reuse and revokes every active token in the
  same family.

The cookie `Secure` flag is environment-controlled through
`AUTH_REFRESH_COOKIE_SECURE`. Local HTTP development uses `false`; production
deployment must use HTTPS and set it to `true`.

## Rotation Boundary

`POST /api/auth/refresh` requires both the refresh cookie and
`X-Refresh-Request: 1`. Credentialed CORS is restricted to the configured
`CORS_ALLOWED_ORIGINS` allowlist, so a cross-site form cannot silently satisfy
the custom-header boundary.

Rotation runs in one transaction. The server hashes the presented value, loads
the candidate, acquires a pessimistic lock by identifier, then checks expiry,
state and account status before issuing a replacement. Expiry and reuse evidence
commit even though the endpoint returns a stable authentication error.

## Client Renewal

The Angular interceptor handles a protected request's `401` response only when
an authenticated client session exists. `AuthService` shares one in-flight
refresh observable across concurrent failures, stores the renewed access token,
and retries each original request with the new bearer token. Authentication
endpoints are excluded to prevent recursive refresh loops.

## Residual Boundary

T217 revokes refresh-token families on detected reuse. Explicit logout,
administrator/session revocation, realtime disconnect and prevention of reuse of
an already-issued access token remain AUTH-006/T218. No production credential or
production enablement is introduced by this decision.

## Verification

Backend service and HTTP tests cover issuance, hash-only persistence, successful
rotation, replay-family revocation, expiry, request-marker validation and auth
regressions. Angular tests cover token expiry plus concurrent single-flight
renewal and retry behavior. Executed results are recorded in
`docs/testing/evidence/007/remediation/T217-refresh-token-rotation.md`.
