# T231 Existing-Customer Partner Conversion

Date: 2026-08-04

## Scope

- Adds authenticated `POST /api/partner/convert` while preserving the anonymous-only `/api/partner/register` contract from T230.
- Resolves the converting account strictly from `CustomUserDetails.userId`; username, email or account identifiers from the request are never authoritative.
- Accepts only property name, province id, ward id and address for conversion.
- Creates the property as `DRAFT`/`INACTIVE` with a `PENDING` owner mapping in one transaction and does not grant owner role or subscription entitlement before approval.
- Switches the Angular form to property-only conversion for signed-in users and redirects them to registration status.

## Security and tenant boundary

- Anonymous callers and authenticated principals without the authoritative custom user context are denied.
- The service locks the current account, requires it to remain active and verifies that its normalized email still belongs to the same user id.
- JSON account or credential fields such as email, password, full name or phone are rejected rather than ignored.
- Conversion never encodes or changes a password, profile or role and derives property contact data from the authenticated account.
- A forced owner-mapping failure rolls back the new property while preserving the pre-existing customer account.

## Verification

1. Backend isolated focused harness:

   - `PropertyRegistrationControllerTest`: 7 passed.
   - `PropertyRegistrationServiceTest`: 5 passed.
   - `PropertyRegistrationRollbackIntegrationTest`: 2 passed.

   Result: 14 tests passed, 0 failed. Coverage includes authoritative principal id, anonymous and generic-principal denial, strict DTO rejection, normalized identity ownership, no credential mutation, canonical location validation and transactional rollback.

2. Frontend focused Angular harness:

   - `partner-register.component.spec.ts`: 5 passed.
   - `partner-registration.service.spec.ts`: 2 passed.

   Result: 7 tests passed, 0 failed. Coverage includes property-only signed-in requests, hidden credential controls, authenticated status redirect and unchanged anonymous registration behavior.

3. Repository check:

   `git diff --check`

   Result: passed.

## Baseline build constraint

The normal Maven lifecycle remains blocked by the unrelated base gap where `PlatformBillingController` references absent `SubscriptionPlanDTO` and `SubscriptionCatalogService`. The backend focused harness compiled production sources with target-only compatibility stubs, executed the T231 tests and cleaned all generated artifacts. The normal Angular builder remains blocked by unrelated missing base i18n services; the focused Angular harness executed only the T231 component and client tests.

## Rollback

The change is schema-neutral. Reverting the task commit removes the conversion endpoint and signed-in form branch. DRAFT properties already created through the endpoint should be retained or closed through the governed property lifecycle rather than deleted.
