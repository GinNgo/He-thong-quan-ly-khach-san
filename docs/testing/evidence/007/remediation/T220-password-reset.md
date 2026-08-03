# T220 - Password reset evidence

## Scope

AUTH-008 forgot/reset password remediation: enumeration-safe request handling,
hashed single-use expiring tokens, rate limiting, reset email template and
session revocation after a successful reset.

## Verification

| Check | Result |
|---|---|
| `backend/.\\mvnw.cmd '-Dtest=PasswordResetServiceTest' '-DforkCount=0' test` | 6/6 passed |
| `backend/.\\mvnw.cmd '-Dtest=AuthControllerIntegrationTest' '-DforkCount=0' test` | 9/9 passed |
| `backend/.\\mvnw.cmd '-Dtest=PasswordResetControllerIntegrationTest' '-DforkCount=0' test` | 1/1 passed |
| `frontend/npm run build -- --configuration production` | Passed; existing CSS budget and CommonJS warnings remain |
| `frontend/npx ng test --no-watch --coverage=false --include='src/app/features/auth/password-recovery-flow.spec.ts' --include='src/app/features/auth/remember-me-removal.spec.ts'` | 5/5 passed |

## Evidence Notes

- `POST /api/auth/forgot-password` always returns HTTP 202 with the same generic
  message for known and unknown accounts; unknown accounts follow the same
  persistence and rate-limit path but never receive mail.
- Raw reset tokens are generated in memory and only SHA-256 hashes are stored
  in `password_reset_tokens` (V51). Active tokens are revoked before issuing a
  new one for the same fingerprint.
- Reset completion locks the token row, rejects expired/replayed tokens,
  updates the password through the configured encoder and revokes existing
  sessions with reason `PASSWORD_RESET`.
- Email delivery is feature-flagged and uses the configured reset URL/TTL; no
  real SMTP credentials were used during verification.
