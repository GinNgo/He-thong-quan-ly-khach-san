# T214 - Credential Registration

Date: 2026-08-03
Task: AUTH-001 / Credential registration

## Delivered

- Registration identifiers are trimmed and lower-cased before persistence; display names and optional phone values are whitespace-normalized.
- `RegistrationConflictException` maps duplicate username and email races to stable HTTP 409 codes with field-level errors.
- `RegisterRequest` adds bounded username, email, name and phone validation.
- User accounts now have an optimistic version and named user/role uniqueness constraints in `V48__credential_identity_constraints.sql`.
- The migration fails closed when normalized legacy duplicates or duplicate user-role assignments require manual remediation.
- The register screen normalizes email/name input and displays structured API field errors instead of rendering an object value.

## Verification

| Command | Result |
|---|---|
| `backend/.\\mvnw.cmd -q clean "-Dtest=CredentialRegistrationIntegrationTest,AuthServiceTest" test` | Passed: 5 HTTP registration tests and 6 service tests (11/11). |
| `frontend/npx ng test --watch=false --include src/app/features/auth/register/register-registration-contract.spec.ts` | Passed: 2/2 Angular registration contract tests. |

The HTTP suite covers normalized persistence, duplicate username, duplicate email,
malformed input without persistence, and concurrent same-identity registration where
exactly one request succeeds and the other receives 409.

## Migration Recovery

`V48` is additive and forward-only. If preflight detects ambiguous normalized identities,
stop the migration, export the conflicting IDs for manual owner-approved remediation, and
rerun after identities are resolved. Recovery is a forward deployment of the previous
application version; no user or role rows are deleted by this migration.

Production credentials, production databases and real provider traffic were not used.
