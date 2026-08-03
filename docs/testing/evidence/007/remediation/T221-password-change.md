# T221 - Authenticated password change evidence

## Scope

AUTH-009 remediation for authenticated password changes: validated request
contract, one 8-256 character policy across registration/reset/change UIs and
backend DTOs, stable wrong-current-password errors and session revocation.

## Verification

| Check | Result |
|---|---|
| `backend/.\\mvnw.cmd '-Dtest=PasswordChangeServiceTest,PasswordChangeControllerIntegrationTest,PasswordResetServiceTest,CredentialRegistrationIntegrationTest' '-DforkCount=0' test` | 17/17 passed: password change 6/6 plus reset and registration regression 11/11 |
| `frontend/npx ng test --no-watch --coverage=false --include='src/app/features/client/account-settings/account-settings.component.spec.ts' --include='src/app/features/auth/password-recovery-flow.spec.ts'` | 6/6 passed |
| `frontend/npm run build -- --configuration production` | Passed; existing property-payment CSS budget and STOMP/SockJS CommonJS warnings remain |

## Evidence Notes

- `ChangePasswordRequest` now rejects blank/oversized current passwords and
  new passwords outside the shared 8-256 character policy before mutation.
- An incorrect current password returns HTTP 400 with stable code
  `CURRENT_PASSWORD_INVALID`; the stored hash remains unchanged.
- A successful change updates the encoded password, sets the access-token
  revocation cutoff and revokes active refresh sessions with reason
  `PASSWORD_CHANGE`.
- Client and admin flows validate against the same frontend policy and clear
  local authentication after success so the user must sign in again.
