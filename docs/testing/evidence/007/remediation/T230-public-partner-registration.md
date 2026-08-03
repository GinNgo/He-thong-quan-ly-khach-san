# T230 Public Partner Registration

Date: 2026-08-04

## Scope

- Replaces the nested controller request with a validated flat DTO.
- Keeps the public endpoint anonymous-only; authenticated customer conversion is reserved for T231.
- Collects canonical province, ward and address fields and validates active ward ancestry server-side.
- Creates a normalized user, complete DRAFT hotel row and PENDING owner mapping in one transaction.
- Derives city and country on the server and sets required hotel identity, contact, source and operation fields.
- Does not create a legacy BASIC subscription or other financial entitlement.

## Validation and isolation

- Email is normalized before duplicate checks and constrained to 320 characters.
- Password follows the shared 8-256 character policy.
- Phone, names and address have explicit validation and normalized whitespace.
- The service rejects inactive/wrong-type locations and wards outside the selected province before persistence.
- Concurrent database identity conflicts map to the stable registration conflict contract.
- Authenticated callers receive a forbidden response on the anonymous route; no password-bearing conversion is allowed.

## Verification

1. Backend isolated focused harness:

   - `PropertyRegistrationControllerTest`
   - `PropertyRegistrationServiceTest`
   - `PropertyRegistrationRollbackIntegrationTest`

   Result: 7 tests passed, 0 failed. Coverage includes valid DTO binding, validation errors, authenticated-path denial, canonical persistence, duplicate email, invalid ward ancestry and transaction rollback after a forced owner-mapping failure.

2. Frontend focused component suite:

   `npx vitest run src/app/features/client/partner-register/partner-register.component.spec.ts --config <ignored focused Angular harness>`

   Result: 4 tests passed, 0 failed. Coverage includes province/ward loading, required canonical fields, normalized payload shape, draft redirect and duplicate field-error presentation.

3. Repository check:

   `git diff --check`

   Result: passed.

## Baseline build constraint

The normal full Maven lifecycle remains blocked by an unrelated base gap where `PlatformBillingController` references absent `SubscriptionPlanDTO` and `SubscriptionCatalogService`. The focused backend harness compiled the current production sources with temporary target-only compatibility stubs, executed all T230 tests and then cleaned those generated artifacts.

## Rollback

The change is application-only and schema-neutral. Reverting the task commit restores the previous endpoint. Any DRAFT records created after deployment should be retained or closed through the property lifecycle rather than deleted.
