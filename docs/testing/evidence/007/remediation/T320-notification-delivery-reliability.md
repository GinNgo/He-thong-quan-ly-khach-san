# T320 Notification Delivery Reliability Evidence

## Scope and contract

- Equivalent personal events use a normalized, unique `event_key`. SQL Server executes `MERGE ... WITH (HOLDLOCK)` so concurrent application instances either create one immutable notification or load the existing row without a uniqueness failure.
- `NotificationService.sendUserNotificationOnce()` joins the caller transaction. The notification and its unique delivery-outbox row therefore commit or roll back together with the producer transaction.
- The scheduled dispatcher locks due rows, publishes only privacy-safe DTOs to `/user/queue/notifications` or `/topic/admin/notifications`, and appends a delivery-attempt row for success, retry or dead-letter outcomes.
- Retry delay is bounded exponential backoff; attempt count, next attempt, delivered time and error class are durable. Payloads, messages, tokens and recipient data are not copied into attempt errors.
- The customer STOMP client emits a reconciliation signal after an actual reconnect. Inbox rows and the layout unread badge then reload the persisted REST state so missed transient frames do not remain invisible.

## Validation

| Layer | Command | Status |
|---|---|---|
| Backend standard focused command | `backend/.\\mvnw.cmd -q "-Dtest=NotificationServiceReliabilityTest,NotificationDeliveryDispatcherTest,NotificationIdempotencyIntegrationTest" test` | BASE BLOCKED before T320 compilation: `PlatformBillingController` references missing base-branch `SubscriptionPlanDTO` and `SubscriptionCatalogService` |
| Backend focused compile + Surefire | Build dependency classpath, compile the 11 T320 main sources and 3 focused tests with `javac --release 21`, then run `backend/.\\mvnw.cmd -q surefire:test "-Dtest=NotificationServiceReliabilityTest,NotificationDeliveryDispatcherTest,NotificationIdempotencyIntegrationTest"` | PASS: 8/8, including four simultaneous equivalent producers producing one notification and one outbox row |
| Angular customer service/inbox/layout | `frontend/npm test -- --watch=false --include=src/app/core/services/customer-notification.service.spec.ts --include=src/app/features/client/notifications/customer-notifications.component.spec.ts --include=src/app/layout/client-layout/client-layout.spec.ts` | PASS: 3 files, 8/8 |
| Playwright recovery journey | `frontend/npx playwright test e2e/notification-reliability.spec.ts --config=playwright.cross-cutting.config.ts --project=chromium` | PASS: 1/1 |
| Playwright discovery | Same command with `--list` | PASS: 1 test discovered |
| Angular production build | `frontend/npm run build` | PASS; existing CSS-budget and STOMP/SockJS CommonJS warnings only |
| Static patch check | `git diff --check -- <T320 scoped files>` | PASS; line-ending warnings only |

Focused assertions cover atomic producer delegation, four-way concurrency, single notification/outbox persistence, personal versus admin destinations, privacy-safe realtime DTOs, successful attempt audit, transient retry scheduling, missing-notification dead lettering, inbox/unread reconciliation and persisted browser recovery.

No temporary catalog/subscription source, Maven include or test stub is present in the worktree or commit. The direct Surefire path uses compiled focused sources only to work around the unrelated incomplete base-branch catalog compilation boundary.

## Migration and recovery

- Migration `V90__notification_delivery_outbox.sql` is additive: it conditionally adds `notifications.event_key`, the filtered unique index, delivery outbox and append-only attempt table. It does not delete or rewrite notification data.
- Forward recovery: stop the dispatcher with `app.notifications.delivery-scan-ms` deployment configuration or revert the code while preserving PENDING/RETRY rows for a corrected forward release. Do not drop outbox/attempt tables during rollback.
- Permission/tenant isolation: producers resolve the persisted user id/username internally; customers only consume their authenticated user destination and own-user REST reconciliation from T319.
- External systems: no production credential, external provider or real-money action was used.
