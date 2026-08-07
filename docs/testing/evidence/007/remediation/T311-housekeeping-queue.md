# T311 / STAY-023 - Housekeeping queue, assignment and start

Status: `COMPLETE_VERIFIED`

## Scope

- Property-scoped queue and assignee list: `GET /api/housekeeping/tasks?propertyId=...`, `GET /api/housekeeping/assignees?propertyId=...`.
- Mutations: `POST /api/housekeeping/tasks/{id}/claim`, `/assign`, and `/start`.
- Dedicated `HOUSEKEEPING` function/role seed and management route `/management/housekeeping`.
- Stale assignment takeover after `app.housekeeping.assignment-stale-minutes` (default 30), optimistic version checks, and room `DIRTY -> CLEANING` transition at start.
- Explicit hotel ownership checks plus Hibernate `housekeepingTaskTenantFilter` remain in force.

## Source

- `backend/src/main/java/com/hotel/housekeeping/HousekeepingController.java`
- `backend/src/main/java/com/hotel/housekeeping/HousekeepingQueueService.java`
- `backend/src/main/java/com/hotel/entities/HousekeepingTask.java`
- `backend/src/main/java/com/hotel/repositories/HousekeepingTaskRepository.java`
- `backend/src/main/resources/db/migration/V54__housekeeping_queue_assignment.sql`
- `frontend/src/app/core/services/housekeeping.service.ts`
- `frontend/src/app/features/management/housekeeping/`
- `backend/src/main/java/com/hotel/controllers/ManagementPortalController.java` (allows the dedicated role to load management context)

## Focused checks

Command attempted:

```text
backend/.\mvnw.cmd -q -Dtest=HousekeepingQueueServiceTest test
```

Final result: `6/6` tests passed. The earlier shared compile blockers were repaired before this final focused run.

Angular command:

```text
npm test -- --watch=false --include='src/app/core/services/housekeeping.service.spec.ts' --include='src/app/features/management/housekeeping/housekeeping.component.spec.ts'
```

Result: `2` test files passed, `4` tests passed. Application bundle generation completed.

Implemented test coverage:

- own pending claim and idempotent replay;
- fresh versus stale assignment conflict/takeover;
- unknown/cross-property assignee rejection;
- cross-property task hidden as not-found;
- optimistic version conflict;
- assigned cleaner start and room state transition;
- Angular service query/body contract;
- Angular empty and error states.

## Remaining verification

- No focused implementation/test gate remains. A full browser tenant journey remains part of the final integrated Playwright gate rather than T311 completion.
- This implementation closes both duplicate inventory mappings `PROP-OPS-023`/T262 and `STAY-023`/T311.
