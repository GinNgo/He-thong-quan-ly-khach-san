# T314 - Favorites / Wishlist Evidence

Date: 2026-08-04
Scope: STAY-026 owner-bound favorites for customer accounts only.

## Source

- `backend/src/main/java/com/hotel/favorites/CustomerFavorite.java` stores the customer owner, public hotel target and UTC creation time.
- `backend/src/main/java/com/hotel/favorites/CustomerFavoriteRepository.java` scopes reads/deletes by `customer_id` and returns only `APPROVED` + `ACTIVE` hotels.
- `backend/src/main/java/com/hotel/favorites/FavoriteService.java` locks the owner row during add, reuses an existing record on replay, validates public eligibility and never accepts an owner id from the request body.
- `backend/src/main/java/com/hotel/favorites/FavoriteController.java` exposes `GET/POST/DELETE /api/favorites` and requires `hasAuthority('CUSTOMER')`; the authenticated principal supplies the owner id.
- `frontend/src/app/core/services/favorite.service.ts` coalesces list loads, deduplicates local state and clears it on logout.
- `frontend/src/app/features/client/favorites/favorite-button.component.ts` provides login return-url behavior and card/detail save/remove controls.
- `frontend/src/app/features/client/favorites/favorites-page.component.ts` provides authenticated list, loading, retry/error and empty states.

## Migration and Recovery

- `backend/src/main/resources/db/migration/V53__customer_favorites.sql` is additive and creates `customer_favorites`, foreign keys, the `(customer_id, hotel_id)` uniqueness constraint and lookup indexes.
- No existing rows are rewritten and no production database was touched. Forward recovery is to apply V53 after validating the users/hotels foreign-key preconditions.
- Rollback is release-level: deploy the previous application version while retaining the additive table; any data removal requires a separately approved, recoverable migration and is intentionally not included here.

## Executed Tests

Backend focused commands (quiet shared-target rerun):

```powershell
cd backend
$cp = (Get-Content -Raw target/t169-classpath.txt).Trim()
javac -encoding UTF-8 -proc:none -cp "target/classes;target/test-classes;$cp" -d target/test-classes src/test/java/com/hotel/favorites/FavoriteServiceTest.java src/test/java/com/hotel/favorites/CustomerFavoriteRepositoryTest.java src/test/java/com/hotel/favorites/FavoriteControllerTest.java
./mvnw.cmd "-Dtest=FavoriteServiceTest,CustomerFavoriteRepositoryTest,FavoriteControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" surefire:test
```

Result: 7 tests passed (repository 2, service 3, controller 2), Surefire build success. The targeted compile avoids unrelated full-testCompile failures elsewhere in the shared dirty worktree; the controller role-gate assertion covers the negative non-customer authorization contract.

Frontend focused command:

```powershell
cd frontend
npm test -- --watch=false --include=src/app/core/services/favorite.service.spec.ts --include=src/app/features/client/favorites/favorite-button.component.spec.ts --include=src/app/features/client/favorites/favorites-page.component.spec.ts
```

Result: 3 test files, 7 tests passed.

Frontend compile command:

```powershell
npm run build -- --configuration development
```

Result: Angular application bundle generated successfully.

## Acceptance Checks

- Owner isolation: list and delete queries always include the authenticated `customer_id`; another customer cannot read or remove the record.
- Replay safety: the service locks the owner row and the database uniqueness constraint prevents duplicate `(customer_id, hotel_id)` rows.
- Public eligibility: add rejects non-approved or non-active hotels; list excludes them without exposing private property data.
- UI coverage: cards and detail hero expose a heart control; `/favorites` is behind `clientAuthGuard` and renders loading, error/retry, empty and populated states.
- Role boundary: staff/admin accounts are denied by the controller's `CUSTOMER` authority gate.
