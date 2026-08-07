# T317 Notification History and Mark-Read Evidence

## Scope and contract

- `GET /api/notifications?page={zeroBasedPage}&size={pageSize}` returns only retained system notifications and notifications owned by the authenticated principal.
- History is ordered by `createdAt DESC, id DESC`, uses a configurable retention window, clamps page size to a safe maximum, and returns page metadata plus the retained unread count.
- `POST /api/notifications/{id}/read` is idempotent for a visible notification, returns the shared stable `404 NOT_FOUND` envelope for a missing or retention-expired notification, and returns `403 ACCESS_DENIED` without mutation for another user's notification.
- Both endpoints retain the existing `REPORT:VIEW` backend permission boundary. This task does not change WebSocket destinations or reconnect behavior (T318 scope).

## Validation

Source and test implementation is complete. Root validation is intentionally serialized with the other active workstreams to avoid concurrent writes to `backend/target` and Angular test output.

| Layer | Command | Status |
|---|---|---|
| Backend service + HTTP | `backend/.\mvnw.cmd -q "-Dtest=NotificationServiceTest,NotificationControllerIntegrationTest" test` | PASS: 14/14 |
| Angular client + history UI | `frontend/npm test -- --watch=false --include=src/app/core/services/notification.service.spec.ts --include=src/app/layout/admin-layout/admin-layout.notification.spec.ts` | PASS: 2 files, 9/9 |
| Static patch check | `git diff --check -- <T317 scoped files>` | PASS; line-ending warnings only |

Focused assertions cover safe page bounds, deterministic ordering, retention filtering, retained unread count, admin/staff row ownership, permission denial, idempotent mark-read, stable missing/expired `NOT_FOUND`, no mutation on cross-user denial, load-more deduplication and recoverable mark-read UI errors.

## Migration and recovery

- Database migration: N/A. Retention is enforced as a read/mark visibility window over the existing `notifications.created_at` column.
- Forward recovery: adjust `app.notifications.retention-days` or revert the notification-only source changes. Existing notification rows are not deleted or rewritten by the retention query.
