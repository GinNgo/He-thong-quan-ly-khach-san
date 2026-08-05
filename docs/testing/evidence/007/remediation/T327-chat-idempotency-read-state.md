# T327 Chat Idempotency and Read-State Evidence

## Implemented behavior

- Customer and support sends accept a stable `clientMessageId`; the database identity is conversation, sender and client id.
- A timeout retry returns the original server message. Reusing the id with different content returns `409 CLIENT_MESSAGE_ID_REUSED`.
- Conversation-level pessimistic locking serializes duplicate sends before the conversation version/SLA audit mutation, so concurrent retries create one message and one lifecycle mutation.
- Delivery state is monotonic: `PERSISTED -> DELIVERED -> READ`. Only the recipient or an authorized tenant support actor can acknowledge a message; sender and cross-user attempts are hidden as not found.
- Both chat surfaces reuse the client id after a retryable failure and render `Da gui`, `Da nhan` and `Da doc`. Acknowledgements from background conversations cannot leak into the selected conversation.

## Executable validation

| Layer | Command / scope | Result |
|---|---|---|
| Backend focused compile and tests | Compile changed chat sources/tests with Java 21 `-parameters`, then run `ChatControllerTest,ChatServiceTest,ChatControllerIntegrationTest` through Surefire | PASS: 24/24, including HTTP replay/conflict, two concurrent duplicate sends, concurrent delivered/read monotonicity and cross-user/tenant denial |
| Angular service and two chat surfaces | `npx ng test --watch=false --progress=false` with the three focused chat specs | PASS: 3 files, 20/20 |
| Two-context browser journey | `CAPTURE_T327_EVIDENCE=1 npx playwright test e2e/chat-idempotency-read-state.spec.ts --config playwright.cross-cutting.config.ts --project=chromium --timeout=90000` | PASS: 1/1; a persisted timeout retry reused one client id and one server id, while customer/support status labels advanced through sent/delivered/read |
| SQL Server migration | Apply `V94__chat_message_idempotency_and_read_state.sql` twice to isolated test database `T326Queue` in `luxestay-cross-cutting-t326-sql` | PASS twice: 4/4 columns, unique filtered identity index, delivery index, state check/default and zero null states |
| Angular production build | `npm run build` | PASS; chat status styling remains inside the component budget after selector reuse. The existing property-payment CSS and STOMP/SockJS CommonJS warnings are outside T327 |
| Static patch check | `git diff --check -- <T327 scoped files>` | PASS; line-ending notices only |

The branch baseline still has unrelated full-Maven compiler gaps in Platform Billing (`SubscriptionPlanDTO` and `SubscriptionCatalogService`). T327 therefore uses the same focused Java 21 compile plus Surefire harness as the preceding cross-cutting tasks and does not claim those unrelated sources are repaired.

## Visual evidence

- Initial persisted acknowledgement: `docs/testing/evidence/007/remediation/assets/T327-customer-sent.png`
- Recipient delivery acknowledgement: `docs/testing/evidence/007/remediation/assets/T327-customer-delivered.png`
- Customer sees the support read receipt: `docs/testing/evidence/007/remediation/assets/T327-customer-read.png`
- Support receives and reads the customer message: `docs/testing/evidence/007/remediation/assets/T327-support-read-customer-message.png`
- Support sees its own reply read by the customer: `docs/testing/evidence/007/remediation/assets/T327-support-read.png`

## Safety and compatibility

- V61 is additive, rerunnable and forward-recovery safe; it retains client identities and acknowledgement timestamps.
- Existing history, queue, tenant not-found behavior and STOMP destinations remain compatible.
- No production credential, real-money action or destructive migration is used.
