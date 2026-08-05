# T238 Claim Approval And Owner Activation

Date: 2026-08-04
Implementation commit: `4198fb5`

## Scope

- Routes imported-property approval through the canonical T234 property approval workflow instead of mutating claim, property and ownership independently.
- Requires the exact imported pre-state `DRAFT / IMPORTED_PENDING_REVIEW / ACTIVE`, one pending OWNER mapping matching the claimant and zero active owners.
- Pessimistically locks the claim, property and pending-owner rows used by this approval path.
- Commits claim `APPROVED`, property `ACTIVE / APPROVED / ACTIVE`, primary active ownership, `PROPERTY_OWNER`, reviewer metadata, tenant audit, in-app notification and email outbox evidence in one transaction.
- Extends the explicit claim DTO with the non-sensitive property lifecycle `status` so the admin client can verify all three canonical fields.
- Adds inline admin confirmation, same-row busy protection, safe conflict errors and queue-refresh protection while approval is in flight.
- On the next authoritative login/profile refresh, exposes only active assignments and uses the granted owner role for property-targeted management navigation.

## Authorization And Isolation

- Admin approval remains restricted to `PROPERTY_CLAIM_APPROVE` or `SUPER_ADMIN`; the reviewer id comes only from the authenticated principal.
- Claimant, property and ownership ids are loaded from the locked claim and matching persistence rows, not from caller-controlled owner data.
- Multiple pending owners, a mismatched pending owner, an existing active owner or a tampered lifecycle triplet fail closed without mutation.
- Pending and inactive mappings are excluded from `assignedProperties`; the client no longer treats any raw assignment as owner authority.
- Competing-owner replacement, transfer and subscription-responsibility policy are not invented. Those decisions remain the T241 stop gate.

## Verification

1. Backend production compilation:

   - 456 production sources compiled successfully with target-only compatibility stubs for six unrelated missing catalog classes in the branch baseline.

2. Backend focused suites:

   - Controller authorization/DTO, canonical workflow, claim service, persistence/rollback, privacy and current-profile suites passed.
   - Result: 44 tests passed, 0 failed, 0 errors, 0 skipped.
   - Persistence coverage proves role and active tenant access after authoritative login, excludes an unrelated property, and verifies claim/property/mapping/role/audit/notification/outbox rollback on audit, outbox or claim-save failure.
   - Unit coverage rejects ambiguous/mismatched pending ownership, existing active ownership, null/tampered lifecycle states and non-pending claim replay.
   - Current-profile coverage proves a pending claimant receives no active management assignment and remains `PENDING`.

3. Frontend focused suites:

   - Admin claim queue and client layout specs passed.
   - Result: 8 tests passed, 0 failed.
   - Tests cover typed canonical triplet validation, duplicate approval protection, safe 409 text, refresh blocking during approval, property-targeted navigation, no profile-refresh feedback loop and pending-claimant navigation isolation.

4. Angular development build:

   - Development build passed after the final independent-review fixes.
   - Temporary compatibility-only i18n stubs were removed before commit.

5. Repository check:

   - `git diff --check`: passed.
   - Generated `backend/target` content was removed with Maven clean.
   - The ordinary branch baseline still lacks six unrelated catalog classes, so focused compilation used temporary target-only stubs; no T238 source or test failure remains.

## Transaction And Recovery

- T238 adds no migration and performs no destructive or production database action.
- Safe rollback: revert the application commit. Existing approved claims and durable audit/notification rows should remain historical evidence; do not manually downgrade ownership or delete evidence without a separately reviewed recovery decision.
- Safe forward recovery for a failed approval is to correct the exact claim/property/mapping invariant and retry while the claim remains `PENDING`. Transaction tests prove partial activation does not commit.
- Request-vs-approval phantom prevention, normalized aggregate lock ordering and database uniqueness constraints remain explicitly assigned to T240.

## Shared-File Handoff

- The coordinator should mark T238 complete in `specs/007-payment-billing-completion/tasks.md` and merge PROP-SUB-011 evidence into the master inventory and traceability matrix.
- Those three shared aggregate files were intentionally not edited on this parallel branch.
