# T325 Customer Support Chat and Persisted History Evidence

## Scope and behavior

- The authenticated customer can create and select multiple support conversations. The server always derives `customerId` from `CustomUserDetails`; no customer request field can select another account.
- Customer and support message history is paginated with deterministic `timestamp DESC, id DESC` repository ordering. Each returned page is normalized to chronological display order without changing page boundaries.
- The configured retention window defaults to 365 days, is clamped to 30-3650 days and filters both conversation visibility and message history without deleting records.
- Customer and support sends persist through REST, then publish the saved message to the private customer queue and authorized support topic. This preserves writes while STOMP reconnects and avoids optimistic message insertion.
- Customer realtime updates are accepted only for the selected `conversationId`; support dashboard selection, history and replies also use `conversationId`, so two conversations for the same customer remain distinct.
- Legacy `GET /api/chat/me/history`, STOMP destinations and support history paths remain available for additive compatibility.

## Authorization and isolation

- Customer conversation list/create/history/send endpoints require authentication and use the principal user id.
- `ChatService.requireOwnedConversation()` returns the same not-found outcome for a missing conversation and a conversation owned by another customer; focused tests verify both read and send IDOR attempts do not persist a message.
- Support list/history/reply endpoints retain `AI_CHAT:VIEW` and `AI_CHAT:CREATE` permission checks. Property/tenant queue filtering, claim and escalation are intentionally left to T326 rather than being invented in T325.
- No production credential, external provider message or real-money operation was used.

## Validation

| Layer | Command | Status |
|---|---|---|
| Standard Maven lifecycle | `backend/.\mvnw.cmd "-Dtest=ChatServiceTest,ChatControllerTest" test` | BASE BLOCKED before T325 compilation by missing `SubscriptionPlanDTO` and `SubscriptionCatalogService` in the inherited platform-billing source |
| Focused backend compile + Surefire | Compile T325 entities/repositories/DTOs/service/controller and focused tests with `javac --release 21`, then run `backend/.\mvnw.cmd "-Dtest=ChatServiceTest,ChatControllerTest" surefire:test` | PASS: 10/10 |
| Angular service/customer/support | `frontend/npm test -- --watch=false --include=src/app/core/services/chat.service.spec.ts --include=src/app/features/client/chat-widget/chat-widget.spec.ts --include=src/app/features/admin/chat-dashboard/chat-dashboard.spec.ts` | PASS: 3 files, 13/13 |
| Two-user browser journey | `CAPTURE_T325_EVIDENCE=1 PLAYWRIGHT_PORT=4221 frontend/npx playwright test e2e/support-chat-conversation-history.spec.ts --project=chromium --timeout=60000` | PASS: 1/1 with independent customer and support browser contexts |
| SQL Server migration | Run V59 twice against a temporary SQL Server 2022 container with legacy queue fixtures, then assert backfill, FK, index and unrelated-row preservation | PASS: 1 conversation, 2 scoped messages, 1 unrelated legacy message left unscoped; rerun created no duplicate conversation |
| Angular production build | `frontend/npm run build` | PASS; existing CSS budget and STOMP/SockJS CommonJS warnings only |
| Static patch check | `git diff --check -- <T325 scoped files>` | PASS; line-ending warnings only |

Focused assertions cover authenticated ownership, foreign-conversation read/send denial, multiple conversation selection, stable pagination, retention cutoff propagation, chronological page rendering, support permission enforcement, REST persistence plus websocket publication, conversation-aware realtime filtering and a complete customer-question/support-reply/customer-history browser flow.

## Visual evidence

- Customer selected-conversation history: `docs/testing/evidence/007/remediation/assets/T325-customer-conversation.png`
- Support conversation selection and reply: `docs/testing/evidence/007/remediation/assets/T325-support-conversation.png`

## Migration and recovery

- `V59__support_conversation_history.sql` conditionally creates `support_conversations`, adds nullable `chat_messages.conversation_id`, backfills one conversation per legacy customer that sent to the central queue, links only queue questions and replies to those customers, then adds the foreign key and paging index.
- The migration is additive and does not delete or rewrite message content. Unrelated legacy peer messages remain nullable/unscoped, as proven by the SQL Server fixture.
- Forward recovery is preferred: retain the new table/column and deploy corrected query or backfill logic. If application rollback is required, the prior customer history endpoint can continue reading legacy rows while new schema remains dormant.
- Destructive rollback (dropping the FK/index/column/table) is not authorized once new conversations exist and was not executed.
