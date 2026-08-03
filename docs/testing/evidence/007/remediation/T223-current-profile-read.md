# T223 - Current profile read evidence

## Scope

AUTH-011 remediation for authenticated self-profile isolation, role/property/SaaS
context projection, and explicit client loading, error, retry and empty states.

## Verification

| Check | Result |
|---|---|
| `backend/.\mvnw.cmd -q "-Dtest=UserControllerCurrentProfileTest,UserCurrentProfileServiceTest" test` | 4/4 passed: controller 2/2 and service 2/2 |
| `frontend/npm test -- --watch=false --include "src/app/features/client/profile/profile-current-read.component.spec.ts" --include "src/app/features/client/profile/profile-email-verification.component.spec.ts"` | 5/5 passed: current-profile states 3/3 and email regression 2/2 |
| `frontend/npm run build` | Passed; existing property-payment CSS budget and STOMP/SockJS CommonJS warnings remain |

## Evidence Notes

- `/api/users/me` resolves the user id from `CustomUserDetails`; no target user id
  is accepted from route, query or request body.
- The read service returns role, assigned-property, subscription, limits, usage,
  unread-message and pending-booking context in one read-only transaction.
- The client retains a visible loading state, distinguishes API failure from an
  empty body, and offers a retry without clearing the authenticated session.

## Recovery

- No schema or configuration change is introduced.
- Safe rollback removes the focused tests and restores the previous profile
  presentation; backend data and authentication state are unaffected.
