# T237 Imported Property Claim Request

Date: 2026-08-04
Implementation commit: `758b425`

## Scope

- Replaces the untyped claim payload with one normalized request contract for `EMAIL`, `PHONE` and `BUSINESS_LICENSE` verification.
- Requires trimmed verification data up to 1000 characters, converts a blank note to null and limits a nonblank note to 500 characters.
- Returns explicit requester/admin DTOs and keeps the admin approve/reject behavior on the existing typed claim service.
- Limits accepted persisted requester claims with a configurable default of three requests per 15 minutes.
- Returns HTTP 429 with stable code `PROPERTY_CLAIM_RATE_LIMITED`, `retryable=true`, correlation id and `Retry-After`.
- Provides an authenticated requester dialog with duplicate-submit protection, safe validation/error text, session-expiry login return and keyboard/focus management.

## Authorization And Isolation

- The requester id comes only from `CustomUserDetails.userId`; the request body cannot select or impersonate an account.
- Claim creation remains authenticated. Anonymous users are sent to `/login?returnUrl=/hotel/{id}` before the dialog opens, and an HTTP 401 during submission follows the same safe recovery path.
- Admin list, approve and reject endpoints retain `PROPERTY_CLAIM_VIEW`, `PROPERTY_CLAIM_APPROVE` or `SUPER_ADMIN` authorization.
- Claim responses use explicit property/user summaries and do not expose passwords, roles, subscriptions or tenant graphs.
- Evidence-file upload is `N/A - not approved`. This task does not reuse public avatar storage or invent storage, malware-scanning, retention or access policy.

## Verification

1. Backend production compilation:

   - Production sources compiled successfully with target-only compatibility stubs for unrelated missing base subscription classes.

2. Backend focused suites:

   - Controller, request validation, service, rate limiter and repository persistence coverage passed.
   - Result: 27 tests passed, 0 failed, 0 errors, 0 skipped.
   - A post-clock regression run passed 11 tests, including exact-window expiration, retry rounding and persisted accepted-request counting.
   - The strict query boundary is `createdAt > cutoff`; a request exactly at the cutoff is expired.
   - Concurrent quota enforcement is intentionally deferred to T240/PROP-SUB-014, which owns database invariants and request/approval race tests.

3. Frontend focused suites:

   - Typed claim service, requester hotel detail and admin claim component specs passed.
   - Result: 14 tests passed, 0 failed.
   - Tests cover payload trimming, safe 400/409/429 feedback, `Retry-After`, anonymous and expired-session redirects, duplicate submission, initial focus, Tab/Shift+Tab trapping, Escape close and focus restoration.

4. Angular development build:

   - Development build passed.
   - The only warning was the unrelated baseline NG8107 warning in `client-layout.html`.
   - Temporary compatibility-only i18n stubs were removed before commit.

5. Repository check:

   - `git diff --check`: passed.
   - Generated `backend/target` content was absent before commit.
   - The whole Maven test-compile remains blocked by unrelated missing subscription classes in the branch baseline; focused source/test compilation used no production credentials or database.

## Migration And Recovery

- `V73__property_claim_request_rate_limit_index.sql` is additive. It verifies the existing table/columns and creates only `IX_property_claim_requests_requester_created` on requester, creation time and id.
- The migration does not delete, rewrite or backfill claim, ownership, property, booking or financial data.
- Safe forward recovery: apply migrations through V73, then verify the index exists before enabling claim traffic at scale.
- Safe rollback: revert application code while retaining the index. Dropping the optional index can be scheduled separately after confirming no deployed version depends on its query performance.
- V73 was reviewed and repository behavior was tested, but it was not executed against an external SQL Server in this task environment.

## Shared-File Handoff

- The coordinator should mark T237 complete in `specs/007-payment-billing-completion/tasks.md` and merge PROP-SUB-010 evidence into the master inventory and traceability matrix.
- Those three shared aggregate files were intentionally not edited on this parallel branch.
