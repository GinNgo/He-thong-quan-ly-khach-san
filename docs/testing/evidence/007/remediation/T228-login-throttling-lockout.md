# T228 Login Throttling, Lockout and Auth Audit

Date: 2026-08-04

## Scope

- Adds account- and connection-IP-aware credential-login throttling.
- Locks known accounts after five failures in a 15-minute window and unlocks automatically after 15 minutes.
- Blocks an IP fingerprint after 20 failures in the same window.
- Returns generic `429 LOGIN_TEMPORARILY_BLOCKED` with `Retry-After`.
- Stores only HMAC-SHA256 account/IP fingerprints in append-only login-attempt evidence.
- Emits structured AUTH audit events and low-cardinality counters without account or network tags.

## Security decisions

- The HMAC key is configured by `app.auth.login.audit-secret` from `LOGIN_AUDIT_SECRET` or `JWT_SECRET`; tests use an explicit non-production value.
- Only `HttpServletRequest.getRemoteAddr()` supplies the network address. Caller-controlled forwarding headers are not trusted.
- Known and unknown account identifiers share the same generic block response.
- Existing authenticated sessions are not revoked by failed password attempts, avoiding a password-attempt denial-of-service against active sessions.
- The migration is additive and does not alter or delete existing authentication data.

## Verification

1. Backend isolated focused compilation and tests:

   `javac --release 21 ... LoginSecurityServiceTest.java OperationalMetricsTest.java`

   `mvnw.cmd surefire:test -Dtest=LoginSecurityServiceTest,OperationalMetricsTest`

   Result: 5 tests passed, 0 failed.

2. Frontend error-mapper tests:

   `npx vitest run src/app/core/auth/account-status-error.spec.ts --config vitest-base.config.ts`

   Result: 4 tests passed, 0 failed.

3. Frontend public/admin login presentation tests:

   `npx vitest run src/app/features/auth/login-throttling.component.spec.ts --config .angular/t228-vitest.config.ts`

   Result: 2 tests passed, 0 failed. The ignored temporary harness initializes Angular resources without changing production sources.

4. Repository checks:

   `git diff --check`

   Result: passed.

## Baseline build blockers

- The normal Maven lifecycle first failed on UTF-8 BOM bytes in the base `UserController.java` and `UserService.java`; this branch removes only those two BOM bytes.
- After that repair, the normal full compile reaches an unrelated base gap: `PlatformBillingController` references absent `SubscriptionPlanDTO` and `SubscriptionCatalogService`. The isolated harness compiles the T228 production dependency slice and executes the focused tests.
- The normal Angular unit builder reaches unrelated absent base i18n sources used by invoice/checkout files. The focused Vitest runs above execute the T228 helper and both login components directly.

## Rollback

- Disable the login guard wiring and remove V57 only before deployment. After deployment, retain the append-only evidence table and stop writing to it rather than deleting audit history.
