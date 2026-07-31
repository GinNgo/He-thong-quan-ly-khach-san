# T054/T060 Property Payment Attempt Creation

## Scope

- Added `PropertyPaymentAttemptService` for authorized reservation owners and property roles.
- Added persisted idempotency acquisition/replay through the shared financial idempotency service.
- Added exact server-owned deposit and remaining-balance calculation from the booking financial summary.
- Added immutable masked receiver snapshots, bilingual instructions, environment, expiry and unique transfer content.
- Added a locked repository lookup for in-progress idempotency recovery.
- Added V31 to fail on duplicate historical transfer content before creating a tenant-scoped unique index.

## Enforced Rules

- Requests select only reservation, purpose and configured method; they cannot submit an amount or receiver account.
- `DEPOSIT` uses the remaining required deposit after successful payments/refunds.
- `BALANCE` uses the current positive server-derived remaining balance.
- Unsupported purposes fail until their authoritative folio sources are implemented.
- Equivalent idempotency replays return the original attempt; conflicting payload reuse is rejected.
- Disabled/incomplete configuration, disabled methods, invalid production readiness and unavailable environments fail closed.
- Manual/QR/local attempts start in `PENDING_VERIFICATION`; online-provider attempts start in `PENDING`.
- Receiver JSON contains masked/public identity only and snapshots bilingual instructions for stable replay.
- Transfer content replaces the required `{paymentCode}` placeholder with a UUID-backed booking code and is limited to 160 characters.
- V31 adds unique `(hotel_id, unique_transfer_content)` enforcement without deleting or rewriting financial rows.

## Automated Validation

Command from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=PropertyPaymentAttemptServiceTest,PropertyPaymentModelTest,FinancialMigrationIntegrationTest' -DforkCount=0 test
```

Final validated result:

- Tests run: 16
- Failures: 0
- Errors: 0
- Skipped: 0
- Build: SUCCESS

## Permissions and Environment

- Reservation owners may create attempts for their booking.
- System administrators or actors assigned to the booking property may create attempts on behalf of the customer.
- Cross-account/cross-property requests return resource-not-found semantics before idempotency or financial reads.
- No provider callback, external network call, production credential or real-money transaction was executed.

## Schema and Recovery

- Migration: `V31__property_attempt_transfer_content_uniqueness.sql`.
- Preflight: deployment stops if duplicate non-null transfer content exists for a property.
- Forward recovery: review duplicate rows as financial evidence, assign a new content only through an approved corrective migration, then rerun V31.
- Rollback: disable new attempt creation and revert service registration. Do not drop the unique index or delete attempt evidence without a separately approved database recovery plan.
