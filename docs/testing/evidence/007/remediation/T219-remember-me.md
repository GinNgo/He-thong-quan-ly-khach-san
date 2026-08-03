# T219 - Remember-me remediation

Date: 2026-08-03

## Decision

The login forms do not advertise a session option that the server cannot honor. The unsupported public and admin remember-me checkboxes were removed, and the login request type now contains only `username` and `password`. Session lifetime remains the existing fixed server policy until a separate short/long-session policy is explicitly approved.

## Verification

- `frontend/src/app/features/auth/remember-me-removal.spec.ts` verifies that both login forms omit the checkbox and that the public login request has no `rememberMe` field.
- Angular focused test: `npx ng test --no-watch --coverage=false --include='src/app/features/auth/remember-me-removal.spec.ts'`.
- Angular production build: `npm run build -- --configuration production`.

## Scope

No backend migration or lifetime change is required because the former field was ignored by `LoginRequest` and did not affect token or refresh-session lifetime.
