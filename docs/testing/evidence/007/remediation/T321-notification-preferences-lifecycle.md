# T321 Notification Preferences and Lifecycle Evidence

## Scope and policy

- Mandatory event classes are `ACCOUNT_SECURITY`, `BOOKING`, `PAYMENT`, `REFUND`, `INVOICE` and `SUPPORT`. Their in-app channel is always enabled and cannot be disabled through the API or UI.
- Mandatory-event email remains user-configurable because the durable in-app record is authoritative. `MARKETING` is optional and both channels are disabled by default until explicit consent.
- The policy changes delivery channels only; it does not change booking, payment, refund, invoice or account state and does not invent any financial calculation or outcome.
- `NotificationService.sendUserNotificationOnceIfEnabled()` gives optional producers a preference-aware, duplicate-safe path. The existing mandatory producer methods remain source-compatible.
- Customer history is split into active and soft-archived views. Archive/restore, mark-read, paging and unread count are scoped to the authenticated user and the configured 365-day visibility window. Retention hides expired rows but does not delete them.
- The settings UI uses labelled fieldsets, native checkboxes, disabled/checked mandatory controls, live save status, error alerts, keyboard-operable tabs and responsive history controls.

## Validation

| Layer | Command | Status |
|---|---|---|
| Backend standard focused command | `backend/.\\mvnw.cmd -q "-Dtest=CustomerNotificationLifecycleControllerTest,CustomerNotificationServiceTest,NotificationServiceReliabilityTest,NotificationPreferenceServiceTest,NotificationDeliveryDispatcherTest,NotificationIdempotencyIntegrationTest,NotificationLifecycleDataJpaIntegrationTest" test` | BASE BLOCKED before T321 compilation by the known missing `SubscriptionPlanDTO` / `SubscriptionCatalogService` base sources |
| Backend focused compile + Surefire | Compile T321 notification/preference/controller sources and seven focused tests with `javac --release 21`, then run direct Surefire for the listed tests | PASS: 22/22 |
| Customer HTTP integration source | Compile `CustomerNotificationControllerIntegrationTest.java` against the focused classes | PASS; runtime full-context execution remains blocked by the unrelated base catalog compilation boundary |
| Angular service/inbox/layout | `frontend/npm test -- --watch=false --include=src/app/core/services/customer-notification.service.spec.ts --include=src/app/features/client/notifications/customer-notifications.component.spec.ts --include=src/app/layout/client-layout/client-layout.spec.ts` | PASS: 3 files, 11/11 |
| Playwright settings/archive journey | `frontend/npx playwright test e2e/notification-preferences-lifecycle.spec.ts --config=playwright.cross-cutting.config.ts --project=chromium` | PASS: 1/1 |
| Angular production build | `frontend/npm run build` | PASS; existing CSS-budget and STOMP/SockJS CommonJS warnings only |
| Static patch check | `git diff --check -- <T321 scoped files>` | PASS; line-ending warnings only |

Assertions cover mandatory versus optional defaults, locked mandatory in-app settings, explicit marketing opt-in, authenticated-user preference persistence, preference-aware suppression, active/archive/restore lifecycle, visibility retention, foreign-row non-enumeration, actual H2 repository isolation, accessible controls and the complete browser interaction.

## Migration and recovery

- `V91__notification_preferences_archive.sql` conditionally adds `archived_at`, a user/archive/created index and the preference table with user foreign key, enum checks and unique `(user,event_class,channel)` rows.
- Migration risk: additive and non-destructive. Existing notification rows remain active; preference defaults are computed by policy and no bulk update is performed.
- Forward recovery: revert UI/API evaluation while retaining archive timestamps and preference rows for a corrected release. Do not drop stored preferences or archived history during rollback.
- Permission/tenant isolation: every endpoint derives `userId` from `CustomUserDetails`; no request body/query field can select another user. Foreign and expired notification ids share `NOT_FOUND` behavior.
- External systems: no production credential, real email or real-money action was used.
