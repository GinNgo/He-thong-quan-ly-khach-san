# T313 - Verified Post-Stay Reviews Evidence

Task: T313 / STAY-025  
Branch: `codex/stay-lifecycle`  
Implementation commit: `c09aedd`

## Implemented

- Customer review creation is bound to the authenticated owner and a reservation
  in `CHECKED_OUT`; other customers receive not-found semantics.
- `uq_stay_reviews_reservation` plus a locked reservation and stable conflict
  mapping enforce one review per completed stay under retry/concurrency.
- Ratings use the product's existing 1-10 public score scale. Published averages
  and counts are recomputed under a pessimistic property lock.
- Daily submission limit, length/range validation, link rejection and repeated-
  character abuse detection reject obvious automated misuse.
- Property staff receive tenant-scoped list, moderation and response commands
  protected by `REVIEW` VIEW/APPROVE/UPDATE masks.
- Customer profile exposes a localized VI/EN review form only after checkout and
  shows existing review state. Admin and management routes expose a responsive
  localized property queue with hide/publish reasons and property responses.

## Validation

Isolated backend snapshot:

```text
mvnw.cmd -Dtest=StayReviewServiceTest test
```

Result: `5/5` passed; backend compile also passed. Coverage includes owner/IDOR,
completed-stay and duplicate rules, daily/content abuse controls, concurrent
unique-conflict mapping, tenant moderation/response and aggregate refresh.

Isolated frontend snapshot:

```text
ng test --include stay-review.service.spec.ts --include review-management.component.spec.ts --include profile-booking-read.component.spec.ts
ng build --configuration development
```

Result: `7/7` passed and Angular development build passed.

The normal backend worktree remains blocked before tests by unrelated BOM errors
in shared `UserController.java` and `UserService.java`; these files were not edited.

## Migration and recovery

V57 is additive: it creates `stay_reviews`, indexes, foreign/check/unique
constraints and the `REVIEW` permission. Forward recovery is to fix the additive
script and rerun on a non-production fixture. Rollback before production data is
to remove the new permission rows and drop `stay_reviews`; after reviews exist,
export/archive them before any approved rollback. No destructive migration or
production database action was executed.

