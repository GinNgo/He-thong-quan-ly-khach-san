# T222 - Email verification and verified email change evidence

## Scope

AUTH-010 remediation for local-account email verification, resend/expiry rules
and authenticated email changes that do not replace the current identity until
the new address is confirmed.

## Policy Decision

- Existing accounts are backfilled as verified to preserve current behavior.
- New credential registrations start unverified; social accounts continue to
  rely on provider-verified email evidence.
- Unverified accounts may browse and book. This preserves the current product
  behavior; enabling a restrictive booking gate requires separate owner
  approval because it materially changes conversion and support workflows.

## Verification

| Check | Result |
|---|---|
| `backend/.\mvnw.cmd -q "-Dtest=EmailVerificationServiceTest,EmailVerificationMailerTest,EmailVerificationControllerIntegrationTest,CredentialRegistrationIntegrationTest,AuthControllerIntegrationTest" test` | 26/26 passed: service 7/7, mail template 1/1, HTTP 4/4, registration 5/5 and auth regression 9/9 |
| `frontend/npm test -- --watch=false --include "src/app/features/auth/verify-email/verify-email.component.spec.ts" --include "src/app/features/client/profile/profile-email-verification.component.spec.ts" --include "src/app/features/auth/register/register-email-verification.component.spec.ts"` | 5/5 passed |
| `frontend/npm run build` | Passed; existing property-payment CSS budget and STOMP/SockJS CommonJS warnings remain |

## Evidence Notes

- V52 adds only additive columns, a filtered pending-email uniqueness index and
  a purpose-scoped token table. Raw verification tokens are never persisted.
- Confirmation locks token and user rows, rejects expired/replayed tokens and
  rechecks committed/pending email plus login-identity uniqueness.
- `PUT /api/users/me` cannot bypass verification: non-email fields update while
  the current email remains unchanged. The authenticated email-change endpoint
  stores `pending_email` and sends a one-time link to the requested address.
- Verified email changes update an email-based username and revoke access and
  refresh sessions with reason `EMAIL_CHANGE`.
- Mail delivery is disabled in automated integration tests; template escaping
  is verified in-process. Real SMTP inbox evidence remains AUTH-015/CROSS-010.

## Recovery

- Safe rollback is application-level: deploy the previous application version;
  the additive columns/table may remain unused without changing existing rows.
- Do not destructively drop V52 in a shared database. Correct defects with a
  forward migration, preserving token and verification audit evidence.
