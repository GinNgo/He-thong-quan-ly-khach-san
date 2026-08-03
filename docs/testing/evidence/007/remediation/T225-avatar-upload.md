# T225 Avatar Upload Evidence

## Scope

- Authenticated upload is bound to the JWT user and cannot target another account.
- JPEG/PNG/WebP signatures and dimensions are verified before persistence.
- Stable error codes avoid leaking filesystem paths or parser details.
- Replacements delete the old managed file after commit; failed persistence deletes the new file.
- Public reads reject traversal and return a detected media type with `nosniff`.

## Automated results

| Layer | Command | Result |
|---|---|---|
| Backend service | Selective JUnit launcher for `FileUploadServiceTest` | 7/7 passed |
| Angular service | `npm test -- --watch=false --include=src/app/core/services/user-avatar-upload.service.spec.ts` in isolated worktree with the existing i18n directory mounted | 1/1 passed |
| Spring MVC integration | `AvatarUploadIntegrationTest` source added; full Maven test compilation is currently blocked by unrelated T309 invoice signature drift and incomplete subscription/entitlement classes in the dirty worktree | BLOCKED_EXTERNAL |

The integration test remains checked in so it can be run unchanged after the active T309/platform-billing work is reconciled. No production credentials, object-storage credentials or real-money flow were used.

## Recovery notes

The change is additive at the service/API layer and does not alter database schema. Local files written by a failed association are removed immediately; replacement cleanup is registered for transaction completion. Production object storage requires an adapter/outbox implementation and deployment approval before enabling it.
