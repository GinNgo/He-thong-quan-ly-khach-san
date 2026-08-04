# T236 Registration Review History And Notifications

Date: 2026-08-04

## Scope

- Reuses `operational_audit_events` as the canonical append-only property transition history instead of creating a second history table.
- Exposes newest-first history through `GET /api/partner/properties/{id}/history` and `GET /api/admin/properties/{id}/history`, bounded to 100 events.
- Returns only the transition id/type, safe `OWNER` or `ADMIN` actor kind, reviewer note, four canonical before/after state fields and occurrence time. Raw actor ids, correlation ids and raw JSON are excluded.
- Adds an optional trimmed approval note with a 500-character maximum and presents history from owner registration status, the pending approval queue and lifecycle property management.
- Persists transition audit, in-app notification and email outbox records in the same transaction; WebSocket delivery occurs only after commit.
- Keeps email delivery disabled by default. Disabled delivery leaves durable rows `PENDING`; enabled delivery uses pessimistic claims, stale-claim recovery, bounded exponential retry and terminal dead-letter evidence.

## Authorization And Isolation

- Owner history resolves the authenticated account only from `CustomUserDetails.userId` and requires an exact `OWNER` mapping for the requested property.
- Inactive rejected owner mappings can read their own history; unrelated property ids return not-found rather than disclosing ownership.
- Admin history requires `ADMIN` or `SUPER_ADMIN` plus `PROPERTY_LIFECYCLE/VIEW`; approval actions remain separately permissioned.
- History DTOs never expose reviewer/owner ids, tenant internals, correlation ids or serialized audit state.
- Lifecycle notifications use only active assigned users. Ownership snapshots use the real active OWNER mapping state and do not mutate assignments.

## Verification

1. Backend production compilation:

   - Production sources compiled successfully with target-only compatibility stubs for the unrelated base subscription classes.

2. Backend focused suite:

   - Approval/lifecycle workflow services and persistence, owner/admin controllers, history service, email outbox and dispatcher passed.
   - Result: 64 tests passed, 0 failed, 0 errors, 0 skipped.
   - Real repository tests prove commit persists transition, audit, notification and outbox together; outbox failure rolls all durable effects back and produces no WebSocket push.
   - Commit produces one after-commit WebSocket push. Invalid legacy email creates `DEAD_LETTER` plus immutable `RECIPIENT_INVALID` evidence without rolling back the transition.
   - Duplicate legacy OWNER mappings do not break authorization because history checks relationship existence rather than loading a single mapping row.

3. Frontend focused suite:

   - Six service/component spec files passed.
   - Result: 38 tests passed, 0 failed.
   - Tests cover canonical event typing, nullable legacy snapshots, UTC handling, safe errors/retry, optional approval note validation and duplicate-request protection.

4. Angular development build:

   - `npx ng build --configuration development`: passed.
   - Two temporary compatibility-only i18n stubs were removed and are not part of T236.

5. Repository check:

   - `git diff --check`: passed.
   - Generated backend `target/` content was removed with Maven clean.

## Migration And Recovery

- `V72__property_review_history_email_delivery.sql` is additive. It creates only `property_review_email_outbox`, `property_review_email_delivery_attempts`, indexes, invariant checks, foreign keys and an append-only update/delete trigger.
- The migration does not rewrite property, ownership, audit, notification, booking or financial data. No production credentials, production database or real email delivery were used.
- Safe forward recovery: apply V70, V71 and V72 in order, deploy with `app.mail.property-review-enabled=false`, verify queued rows, then enable delivery only with approved non-production/production mail configuration.
- Safe rollback: revert application code while retaining V72 tables and immutable attempts. Do not drop the tables during emergency rollback because they contain delivery audit evidence.
- SQL Server-specific DDL was reviewed and covered by entity/repository persistence tests, but V72 was not executed against an external SQL Server in this task environment.

## Cross-Branch Handoff

- T324 already owns a generic email-outbox implementation on another coordinator branch. T236 intentionally uses the isolated `com.hotel.propertyreview` package and `property_review_*` tables to avoid parallel write conflicts.
- The coordinator should decide whether to retain the isolated property-review queue or migrate it into the generic outbox after both branches merge. Any consolidation must preserve T236 audit-event linkage, append-only attempts, disabled-by-default behavior and existing queued rows.
- Shared `tasks.md`, master inventory and traceability matrix were intentionally not edited on this branch.
