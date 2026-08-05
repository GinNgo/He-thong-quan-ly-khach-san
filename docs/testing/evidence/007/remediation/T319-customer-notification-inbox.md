# T319 Customer Notification Inbox Evidence

## Scope and contract

- `GET /api/customer/notifications?page={page}&size={size}` returns only notifications whose `user_id` matches the authenticated principal. Broadcast/admin and other-user rows are excluded.
- `GET /api/customer/notifications/unread-count` returns the same principal-scoped unread count, and `POST /api/customer/notifications/{id}/read` is idempotent for an owned row.
- A missing or foreign notification id returns the same privacy-safe `404 NOT_FOUND` behavior without mutating the foreign row; the customer DTO does not expose `userId`.
- `/notifications` provides responsive loading, error, empty, pagination, unread and mark-read states. The client layout displays the unread badge and subscribes only to `/user/queue/notifications` with the current bearer token.
- Booking/payment, invoice, refund and support notification classes expose actionable customer deep links. Durable outbox delivery, reconnect reconciliation and lifecycle preferences remain T320/T321 scope.

## Validation

| Layer | Command | Status |
|---|---|---|
| Backend service + HTTP | `backend/.\\mvnw.cmd -q "-Dtest=CustomerNotificationServiceTest,CustomerNotificationControllerIntegrationTest" test` | PASS: 6/6 |
| Angular service + inbox + client layout | `frontend/npm test -- --watch=false --include=src/app/core/services/customer-notification.service.spec.ts --include=src/app/features/client/notifications/customer-notifications.component.spec.ts --include=src/app/layout/client-layout/client-layout.spec.ts` | PASS: 3 files, 7/7 |
| Playwright customer journey | `frontend/npx playwright test e2e/customer-notifications.spec.ts --project=chromium` | PASS: 1/1 |
| Playwright discovery | `frontend/npx playwright test e2e/customer-notifications.spec.ts --list` | PASS: 1 test discovered |
| Angular production build | `frontend/npm run build` | PASS; existing bundle/CommonJS warnings only |
| Static patch check | `git diff --check -- <T319 scoped files>` | PASS; line-ending warnings only |

Focused assertions cover unauthenticated denial, own-user filtering, broadcast exclusion, privacy-safe DTO shape, unread count, deep-link mapping, own-row mutation, foreign-row non-enumeration/no mutation, customer-only REST paths, the personal STOMP destination, responsive inbox states and visible unread badge behavior.

The backend base branch currently references catalog/subscription sources that are absent from the branch. The focused Spring run used temporary test-only catalog stubs and a temporary Maven test include solely to compile the isolated T319 suites; all temporary files and the Maven include were removed after the successful run and are not part of this task or commit.

## Permission, tenant and recovery notes

- Permission/tenant isolation: authenticated customer identity is derived from `CustomUserDetails`; no caller-supplied user/property id is accepted. Repository reads and mutations include the principal user id.
- Database migration: N/A. T319 uses the existing `notifications` table without rewriting rows.
- Forward recovery: revert the customer controller/service/client route while retaining existing notification data. The UI falls back to a recoverable error state if the customer endpoints are unavailable.
- External systems: no production credentials, external messages or real-money operation were used.
