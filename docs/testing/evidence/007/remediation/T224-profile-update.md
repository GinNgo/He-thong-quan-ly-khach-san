# T224 Profile Update Evidence

**Task**: T224 / AUTH-012
**Branch**: `codex/ui-functional-audit-polish`
**Baseline commit**: `4181cee`
**Executed**: 2026-08-04 (Asia/Saigon)

## Implemented contract

- `PUT /api/users/me` accepts a dedicated validated `ProfileUpdateRequest` instead of the broad administration DTO.
- Full name and phone values are Unicode-normalized, trimmed and whitespace-collapsed before persistence.
- Avatar writes accept only application-managed `/api/public/uploads/{filename}` paths or absolute HTTPS URLs without user-info. `javascript:`, `data:`, `file:`, protocol-relative, HTTP and traversal paths are rejected before persistence.
- Direct profile writes cannot change email. Email changes remain pending until the one-time verification flow succeeds.
- A verified email change updates `users.email` and the email-based `users.username` together, revokes sessions and permits login only with the new identity.
- Duplicate active, pending or username identities return `409 EMAIL_IDENTITY_CONFLICT` without changing the account.
- The Angular client now has a typed profile-update payload and blocks unsupported phone characters before submission.

## Automated validation

| Layer | Command | Result |
|---|---|---|
| Backend service/HTTP/regression | `backend/.\mvnw.cmd -q "-Dtest=UserProfileUpdateServiceTest,UserProfileUpdateIntegrationTest,EmailVerificationServiceTest,EmailVerificationControllerIntegrationTest" test` | PASS, 24/24 tests |
| Angular profile update/regression | `frontend/npx ng test --no-watch --coverage=false --include=src/app/features/client/profile/profile-update.component.spec.ts --include=src/app/features/client/profile/profile-email-verification.component.spec.ts --include=src/app/features/client/profile/profile-current-read.component.spec.ts` | PASS, 7/7 tests |
| Angular production build | `frontend/npm run build` | PASS |

The production build retains only pre-existing warnings: the property-payment configuration CSS budget and CommonJS STOMP/SockJS dependencies.

## Security and ownership

- Authorization remains `isAuthenticated()` and the backend resolves the target user exclusively from `CustomUserDetails.userId`; callers cannot select another profile id.
- Invalid profile fields return `400 VALIDATION_FAILED`; unsafe avatar URLs return `400 INVALID_REQUEST`; duplicate email identities return `409 EMAIL_IDENTITY_CONFLICT`.
- Email and login identity changes remain server-owned and verification-gated. The old access session is revoked after confirmation.
- No production credentials, real-money paths or external provider configuration were used.

## Data and recovery

- Migration: N/A. T224 changes validation and mutation rules over existing `users` columns.
- Rollback: revert the T224 commit. Existing profile rows remain readable; no data backfill or destructive operation is required.
- Forward recovery: clients that previously submitted unsupported avatar schemes must upload an image through the managed endpoint or use an HTTPS image URL.

## Remaining boundary

T225 still owns image signature/dimension verification, replaced-file cleanup and production object-storage documentation. T224 secures only the profile field that references an avatar.
