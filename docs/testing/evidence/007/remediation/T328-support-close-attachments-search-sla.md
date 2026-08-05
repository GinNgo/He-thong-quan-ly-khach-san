# T328 Support Close, Attachments, Search and SLA Evidence

## Implemented behavior

- Support operators can search the already tenant-scoped queue by subject, customer, property, reservation, conversation id or last message while retaining status, assignment, SLA and property filters.
- Close and reopen require a bounded reason and the current optimistic version. Accepted transitions persist timestamps/reasons and immutable `CLOSED` / `REOPENED` events; another assigned agent is denied and audited.
- Conversation DTOs expose created, activity, assignment, escalation, close, reopen, first-response and last customer/support response timestamps without weakening the existing SLA state contract.
- Customer and support surfaces list and upload attachments. The backend accepts only PDF, PNG, JPEG and UTF-8 text, limits size, validates content signature, declared MIME and filename extension, stores SHA-256, and serves authenticated downloads with `nosniff` plus checksum headers.
- Attachment ownership follows the conversation tenant. Cross-property list/download attempts return privacy-safe `404` and append a denied support event.

## Executable validation

| Layer | Command / scope | Result |
|---|---|---|
| Backend focused compile and tests | Compile changed chat/attachment sources with Java 21 `-parameters`, then run `ChatControllerTest,ChatServiceTest,ChatControllerIntegrationTest` through Surefire | PASS: 28/28; includes scoped search, reason/version lifecycle, audit events, timestamps, signature/MIME rejection, checksum headers and cross-tenant attachment denial |
| Angular chat clients and both surfaces | `npx ng test --watch=false --progress=false` with chat service, support dashboard and customer widget specs | PASS: 3 files, 23/23 |
| Browser support journey | `CAPTURE_T328_EVIDENCE=1 npx playwright test e2e/support-close-attachment-search.spec.ts --config playwright.cross-cutting.config.ts --project=chromium --timeout=90000` | PASS: 1/1; search, safe upload, close reason and reopen reason are visible and asserted |
| SQL Server migration | Apply `V95__support_close_search_attachments.sql` twice to isolated `T326Queue`, then run `backend/tools/t328-support-sqlserver-validation.sql` | PASS twice: lifecycle columns `6`, attachment table `1`, indexes `2`, checks `2`, foreign keys `3` |
| Angular production build | `npm run build` | PASS; chat component budgets pass. Remaining property-payment CSS and STOMP/SockJS warnings are unrelated to T328 |
| Static patch check | `git diff --check -- <T328 scoped files>` | PASS; line-ending notices only |

The branch baseline still has unrelated full-Maven compiler gaps in Platform Billing (`SubscriptionPlanDTO` and `SubscriptionCatalogService`). T328 therefore uses the established focused Java compile plus Surefire harness and does not claim those sources are repaired. Browser APIs are deterministic route fixtures; persistence, tenant isolation and attachment validation execute against the Spring/H2 integration context, while V62 executes twice against SQL Server 2022.

## Visual evidence

- Search result, checksum-backed attachment and audited closed state: `docs/testing/evidence/007/remediation/assets/T328-reasoned-close-attachment.png`
- Reopened conversation with renewed SLA deadline: `docs/testing/evidence/007/remediation/assets/T328-reopened-sla-search.png`

## Migration and recovery

- V62 is additive and rerunnable. It adds lifecycle fields plus tenant-linked attachment records with explicit foreign keys, type/size checks and lookup indexes.
- Forward recovery is required after attachments or lifecycle reasons are used: retain records and deploy corrected validation/index logic. Dropping attachment bytes, checksums or audit reasons would destroy support evidence and was not attempted.
- No production credential, external message, destructive migration or financial transaction was used.
