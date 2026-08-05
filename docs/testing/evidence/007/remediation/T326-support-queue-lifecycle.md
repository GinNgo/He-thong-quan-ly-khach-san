# T326 Support Queue Lifecycle Evidence

## Scope and behavior

- `/admin/chat` exposes property-safe queue filters for status, assignment and SLA state, plus visible claim, unassign, escalate and reopen actions.
- Queue mutations require the current conversation `version`. A stale claim returns the stable `409 CONCURRENT_MODIFICATION` contract; the UI preserves the operator's context, reports the conflict and reloads authoritative queue state.
- Customer messages reset the response SLA deadline. A support reply records the first response, assigns an unowned conversation to the responder and clears the pending-response SLA state.
- REST sends persist before publication. Customer and support clients then receive the saved message through principal-scoped STOMP queues; the browser journey verifies both customer-to-support and support-to-customer delivery.
- Accepted and denied support actions append immutable `support_conversation_events` records. Explicit unassign and reopen transitions keep the conversation history intact.

## Authorization and tenant isolation

- Customer conversation access continues to derive ownership from `CustomUserDetails`; a request cannot select another customer identity.
- Support list/history/mutation operations retain `AI_CHAT:VIEW` or `AI_CHAT:CREATE` and restrict non-system operators to active property assignments.
- Cross-property conversations use a not-found response and record a denied audit event without leaking another tenant's queue item.
- Realtime support recipients are resolved from the conversation property, with system administrators included. Legacy unscoped conversations are visible only to system administrators.
- No production credential, external provider message, real-money operation or production database was used.

## Validation

| Layer | Command or fixture | Status |
|---|---|---|
| Standard Maven lifecycle | Repository compile from the inherited base | BASE BLOCKED by missing platform-billing `SubscriptionPlanDTO` and `SubscriptionCatalogService`; no compatibility source is committed |
| Focused backend compile | Compile the 447 current main sources with the dependency classpath and temporary compatibility types outside the final source set | PASS; temporary types were removed before validation/staging |
| Backend focused suites | `backend/.\mvnw.cmd -q surefire:test '-Dtest=ChatChannelInterceptorTest,ChatControllerTest,ChatServiceTest,ChatControllerIntegrationTest,ChatWebSocketIntegrationTest'` | PASS: 30/30 (`8` interceptor, `4` controller, `8` service, `6` HTTP/CORS, `4` embedded STOMP) |
| Angular service and queue UI | `frontend/npm test -- --watch=false --include=src/app/core/services/chat.service.spec.ts --include=src/app/features/admin/chat-dashboard/chat-dashboard.spec.ts` | PASS: 2 files, 14/14 |
| Real browser HTTP/STOMP journey | `CAPTURE_T326_EVIDENCE=1 frontend/npx playwright test e2e/support-queue-lifecycle.spec.ts --config playwright.cross-cutting.config.ts` | PASS: Chromium 1/1 in two isolated customer/support contexts |
| SQL Server migration | Run V59 then V60 twice against isolated SQL Server 2022 database `T326Queue` | PASS twice; hotel/version/events/two indexes/legacy conversation/backfill assertions = `1/1/1/2/1/1` |
| Angular production build | `frontend/npm run build` | PASS; T326 chat CSS is within budget. Remaining property-payment CSS and STOMP/SockJS CommonJS warnings are outside T326 |
| Static patch check | `git diff --check -- <T326 scoped files>` | PASS; line-ending notices only |

The real browser run first exposed two gaps that focused mocks could not prove: rebuilding CONNECT headers prevented Spring's user registry callback, and the support REST reply body omitted the DTO-required `conversationId`. The interceptor now retains the original mutable accessor, the client sends the complete contract, and regression tests cover both behaviors.

## Visual evidence

- Filtered tenant support queue: `docs/testing/evidence/007/remediation/assets/T326-support-queue.png`
- Optimistic conflict notice and authoritative reload: `docs/testing/evidence/007/remediation/assets/T326-conflict-recovery.png`
- Unassign, escalate and reopen lifecycle: `docs/testing/evidence/007/remediation/assets/T326-lifecycle-actions.png`
- Customer receives the persisted support reply through the private queue: `docs/testing/evidence/007/remediation/assets/T326-realtime-customer.png`

## Migration and recovery

- `V93__tenant_support_queue_lifecycle.sql` is additive: it adds property, reservation, assignment, SLA, lifecycle, version and legacy-scope columns; creates support event storage, constraints and queue indexes; and backfills existing rows without deleting message content.
- The migration is rerunnable. Dynamic DDL/DML avoids SQL Server compile-time references to columns that do not exist before the conditional add.
- Forward recovery is preferred: retain the additive schema and deploy corrected filters, constraints or backfill logic. Existing conversation/message rows remain readable by the V59 compatibility paths.
- Dropping columns, foreign keys or event history after operators use the queue is destructive and was not attempted.
