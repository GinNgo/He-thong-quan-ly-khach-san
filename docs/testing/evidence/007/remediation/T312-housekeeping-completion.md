# T263/T312 Housekeeping Completion and Room Release

Status: `COMPLETE_VERIFIED`

## Implementation contract

- Canonical completion endpoint: `POST /api/housekeeping/tasks/{taskId}/complete`.
- Completion requires the dedicated `HOUSEKEEPING:APPROVE` action, an active housekeeping role/property mapping, and ownership of an `IN_PROGRESS` task.
- The request carries `expectedVersion`; stale non-terminal commands fail before mutation, while a replay against an already completed task returns the stored terminal result without another room/task write.
- Completion locks both task and room, marks the task `COMPLETED`, and changes the room to `CLEAN`.
- A room is released to `AVAILABLE` only when maintenance is `NONE`; `MAINTENANCE` and `OUT_OF_SERVICE` continue to own the operational room status.
- The legacy management completion route delegates to the same canonical service and permission contract so it cannot bypass ownership, version, or replay rules.

## Required evidence

- Backend focused tests: own-assignee completion, wrong assignee rejection, cross-property hiding, stale version rejection, idempotent replay, and maintenance-safe completion.
- Frontend focused tests: optimistic completion request, assignee/permission button visibility, successful completed-state rendering, and maintenance-blocked release messaging.
- Commands and final pass counts are recorded after validation.

## Source delivered

- Canonical backend completion: `backend/src/main/java/com/hotel/housekeeping/HousekeepingQueueService.java` and `HousekeepingController.java`.
- Compatibility route delegation: `backend/src/main/java/com/hotel/controllers/ManagementPortalController.java`.
- Room-release projection: `backend/src/main/java/com/hotel/dtos/HousekeepingTaskDTO.java`.
- Existing-role permission upgrade: `backend/src/main/resources/db/migration/V56__housekeeping_completion_permission.sql` plus the development initializer merge.
- Staff UI and typed client: `frontend/src/app/features/management/housekeeping/` and `frontend/src/app/core/services/housekeeping.service.ts`.
- Focused service, permission, tenant HTTP, client and component tests are included with the source.

## Validation results

Maven and Angular were run sequentially by the root coordinator.

```text
cd backend
.\mvnw.cmd -q "-Dtest=HousekeepingQueueServiceTest,HousekeepingCompletionPermissionTest,TenantIsolationIntegrationTest" test
```

```text
cd frontend
npm test -- --watch=false --include='src/app/core/services/housekeeping.service.spec.ts' --include='src/app/features/management/housekeeping/housekeeping.component.spec.ts'
```

Focused result: backend `17/17` passed (`12` service, `2` permission, `3` selected tenant HTTP tests); Angular `2` files / `7` tests passed.

The broader `TenantIsolationIntegrationTest` class also ran once and reported `23/24`; its only failure was the pre-existing invoice isolation assertion expecting legacy generation `404` while T309 now returns `409`. The three housekeeping tenant methods were rerun explicitly and all passed, so that unrelated invoice regression is not hidden or counted as T312 evidence.

## Migration recovery note

- Forward: V56 only ORs mask `37` (`VIEW | UPDATE | APPROVE`) into the `HOUSEKEEPING` role/function row; it does not remove any existing permission.
- Rollback, if the completion code is rolled back: clear only bit `32` from the `HOUSEKEEPING/HOUSEKEEPING` permission row after confirming no later feature uses that approve action. Preserve bits `1` and `4`; do not delete the role or function.
