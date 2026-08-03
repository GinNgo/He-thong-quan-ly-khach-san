# T227 - Authoritative auth permission and menu context

## Outcome

AUTH-019 is complete. Credential, Google/Facebook social and refresh responses now use the same server-owned `CustomUserDetails` context. Permission masks are OR-aggregated from current role mappings, returned in deterministic structured form and used by the authenticated menu endpoint. Client-stored roles and masks remain presentation hints only.

## Security decisions

- Access tokens contain only the signed username subject; roles and permission masks are reloaded by `JwtAuthFilter` for every authenticated request.
- `/api/auth/my-menu` is protected by both the Spring Security `authenticated()` matcher and `@PreAuthorize("isAuthenticated()")`.
- Menu filtering reads VIEW masks from `CustomUserDetails`; it does not rehydrate or accept client/local-storage permission data.
- Only `SUPER_ADMIN`/`ROLE_SUPER_ADMIN` has the explicit menu bypass. Username and ordinary `ADMIN` values do not grant a client or server bypass.
- Angular permission checks use typed `{ function, actionMask }` entries and are documented as presentation-only.

## Automated evidence

Backend focused JUnit run:

```powershell
.\mvnw.cmd surefire:test "-Dtest=AuthServiceTest,SocialAuthPermissionContextTest,CustomUserDetailsPermissionAggregationTest,AuthMenuContextTest,JwtAuthFilterAuthorityReloadTest"
```

Result: **8 tests passed, 0 failed, 0 errors, 0 skipped**.

Covered behavior:

- Credential response includes the authoritative user id, sorted roles and structured permission masks.
- Social response uses the same principal and permission representation.
- Multiple role mappings OR-aggregate action masks; unknown function codes are ignored.
- Menu access requires authentication and filters by the server principal's VIEW mask.
- A spoofed `X-Permission-Masks` request header cannot add backend authority.

Angular focused run:

```powershell
npm test -- --watch=false --include=src/app/core/services/permission.service.spec.ts
```

Result: **2 tests passed, 0 failed**.

## Validation notes

- Main backend compilation succeeded after supplying existing uncommitted subscription compatibility sources in the isolated validation worktree.
- The repository-wide Maven `testCompile` remains blocked by unrelated uncommitted payment/refund/support/notification sources referenced by other tests. T227 tests were compiled selectively and executed with Maven Surefire to avoid claiming those other task scopes.
- Angular compilation required the existing uncommitted `core/i18n` sources in the isolated validation worktree. The focused T227 spec then passed.
- No schema or configuration migration is required. Rollback is the T227 commit; forward recovery is to preserve the server-reload invariant when adding future roles/functions.
