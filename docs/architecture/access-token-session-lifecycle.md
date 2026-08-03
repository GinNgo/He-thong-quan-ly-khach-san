# Access Token Session Lifecycle

Date: 2026-08-03
Decision scope: AUTH-004 / T216

## Decision

The Angular client stores the short-lived access token in tab-scoped
`sessionStorage`. It does not persist access tokens in `localStorage`.

This is an interim session design for the current stateless JWT backend:

- a legacy `localStorage` token is migrated once into `sessionStorage`;
- malformed tokens and tokens without a finite JWT `exp` claim are rejected;
- expired tokens, including tokens within the five-second clock-skew window, are
  removed before an Authorization header can be attached;
- `AuthService` schedules local session cleanup for the token expiry instant;
- logout and stale-session recovery clear both current and legacy token keys;
- user metadata may remain in `localStorage` for UI restoration, but it is not an
  authorization source and is removed whenever no valid access token exists.

## Security Boundary

`sessionStorage` limits token persistence to the current browser tab and removes
the previous long-lived local-storage artifact. It does not make a bearer token
immune to script execution in an already compromised page. Content security,
dependency hygiene and output encoding remain required XSS controls.

AUTH-005/T217 now renews this access-token flow with a rotating refresh token in
an `HttpOnly`, `SameSite=Strict` cookie. Hash-only persistence, reuse detection
and family revocation are documented in `docs/architecture/refresh-token-rotation.md`.
Explicit logout invalidation and realtime disconnect remain AUTH-006/T218.

## Client Invariants

1. An invalid or expired access token never reaches an API request header.
2. A valid legacy token is migrated without extending its original expiry.
3. A token is scoped to one browser tab and disappears when that tab closes.
4. Expiry clears token, user metadata and in-memory authentication state.
5. Client roles and permissions control presentation only; backend authorization
   remains authoritative.

## Verification

Focused Angular tests cover JWT inspection, migration, expired/malformed cleanup,
interceptor behavior, logout and scheduled expiry. Browser tests cover successful
session creation/logout and denial of an expired legacy session. Backend HTTP
tests cover malformed and cryptographically valid expired tokens.

Executable results are recorded in
`docs/testing/evidence/007/remediation/T216-access-token-session.md`.
