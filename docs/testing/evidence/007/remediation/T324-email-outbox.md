# T324 - Email outbox, retry, bounce and delivery audit

Date: 2026-08-04
Capability: `CROSS-009`
Task: `T324`

## Implemented scope

- `V55__email_outbox_delivery_audit.sql` is additive after the existing V54 housekeeping migration. It creates an idempotent `email_outbox` queue, due/failure indexes, and append-only `email_delivery_attempts` evidence.
- `com.hotel.emailoutbox` stores template key/version, request hash, bounded attempts, exponential backoff, terminal dead-letter state, manual retry count, provider message identity and sanitized error codes.
- `EmailOutboxWorker` polls due rows. `JavaMailEmailDeliveryAdapter` is fail-closed: delivery is disabled unless `MAIL_OUTBOX_DELIVERY_ENABLED=true` is explicitly configured for a sandbox/test adapter. No production credentials or live SMTP run was used.
- `EmailService` bridges registration, password-reset, booking-confirmation and invoice messages into the outbox with stable idempotency identities. Legacy two-argument construction remains only for isolated unit tests.
- `GET /api/admin/email-outbox/failures` and `GET /api/admin/email-outbox/{id}/attempts` require `AUDIT_LOG:VIEW`. Manual retry and bounce marking require `AUDIT_LOG:UPDATE`; tenant-assigned operators cannot see another property's rows. Recipient addresses are masked in the operator response.
- Angular route `/admin/email-outbox` renders loading, empty, error, masked-recipient, attempt-history and permission-aware retry states.

## Executed verification

| Area | Command | Result |
|---|---|---|
| Backend source | `backend\\mvnw.cmd -q -DskipTests compile` | PASS (exit 0, 87.8s) |
| Angular service/component | `npm test -- --watch=false --include=src/app/core/services/email-outbox.service.spec.ts --include=src/app/features/admin/email-outbox/email-outbox.component.spec.ts` | PASS: 2 files, 4 tests |
| Backend focused unit tests | `EmailOutboxServiceTest,EmailOutboxWorkerTest,JavaMailEmailDeliveryAdapterTest,EmailOutboxControllerContractTest,EmailServiceTest` | PASS: 10/10 tests |

## Safety and recovery

- Queue records and delivery attempts are append-only evidence; only queue state transitions are mutable and optimistic-versioned.
- Retry is bounded by `max_attempts` and terminal failures remain visible for manual review. Manual retry resets only the dispatch window and increments `manual_retry_count`; it does not delete delivery history.
- Migration is forward-only and no production database, SMTP credential or real-money flow was used.

## Remaining blocker

No T324 implementation or focused-test blocker remains. SMTP sandbox mailbox evidence remains tracked separately by `CROSS-010` and is intentionally not claimed here.
