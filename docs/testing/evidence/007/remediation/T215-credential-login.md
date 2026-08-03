# T215 Credential Login Evidence

Date: 2026-08-03
Capability: AUTH-002 username/password login

## Implemented Contract

- `username` remains the request field for compatibility, but its public meaning is now "email or username".
- `CustomUserDetailsService` trims and lowercases the supplied identifier with `Locale.ROOT`, then resolves username first and email second without changing the canonical username stored in the JWT subject.
- Unknown users and wrong passwords both return the same non-retryable `401 UNAUTHORIZED` response and generic message, preventing account enumeration.
- Non-active accounts still return the distinct stable `ACCOUNT_DISABLED` code required by AUTH-003.
- Login identifiers and passwords have bounded request lengths.
- Browser logout clears both `token` and `user` local-storage entries. Server-side token revocation remains explicitly assigned to AUTH-006/T218.

## Executed Verification

### Backend

```powershell
backend/.\mvnw.cmd -q "-Dtest=AuthControllerIntegrationTest,CustomUserDetailsServiceAccountStatusTest" test
```

Result: 10/10 passed.

- Credential success returns a token and canonical username.
- Username lookup tolerates surrounding whitespace and case differences.
- Email lookup tolerates surrounding whitespace and case differences.
- Wrong password and unknown user share the stable enumeration-safe 401 contract.
- Suspended accounts are rejected with `ACCOUNT_DISABLED`.
- A previously issued JWT stops working after the account is disabled.

### Browser

```powershell
$env:LUXESTAY_E2E_WEB_URL='http://127.0.0.1:4217'
npx playwright test e2e/credential-login-logout.spec.ts --project=chromium --workers=1 --reporter=line
```

Result: 1/1 Chromium journey passed.

The journey submits a username through the public login form, verifies session creation and account-menu reachability, invokes logout, and proves both browser auth artifacts are removed.

## Residual Scope

- JWT expiry-aware behavior is AUTH-004/T216.
- Refresh-token rotation is AUTH-005/T217.
- Server-side logout/session revocation is AUTH-006/T218.
- Login throttling and lockout are AUTH-020/T232.
