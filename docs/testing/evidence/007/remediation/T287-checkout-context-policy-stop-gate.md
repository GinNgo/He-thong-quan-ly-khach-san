# T287 Checkout Context Policy Stop Gate

Task: `T287`
Capability: `PUB-020`
Status: `BLOCKED_FINANCIAL_POLICY`

## Decision

The current URL-controlled hotel/name/price/estimate display authority is unsafe, but the task explicitly requires a backend quote/context contract. A truthful quote identity, total, expiry and stale-price decision cannot be created until the T282 financial policy is approved.

## Current Boundary

- Checkout requires caller-controlled display fields from the URL and can show forged identity/price/estimated total.
- Reservation creation already reloads and locks the server room type/property, capacity, availability and base price; forged URL values do not control persisted booking price.
- No public booking-context/quote endpoint, identity, version, expiry or stale-catalog confirmation contract exists.

## Missing Decisions

- Approved quote components and exact VND rounding.
- Quote identity/version and TTL.
- Reject-versus-refresh behavior for expired or catalog-stale quotes and customer confirmation of changed totals.
- T281 capacity fallback/default rules used by the context response.

## Verification

- Read-only review covered task/spec/constitution, checkout route/component, reservation service and current DTO/controller surface.
- No Active Parallel Claims table or T287 completion evidence exists.
- No URL field was promoted to server authority and no financial behavior was changed.

## Resume Condition

After T281/T282 decisions, add a server-authoritative context/quote endpoint, remove required display authority from production URLs, visibly reject/refresh stale context, and prove forged URL, reload and back-navigation behavior through API, frontend and browser tests.
