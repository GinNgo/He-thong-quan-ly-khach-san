# T291 Mixed Room-Type Cart Policy Stop Gate

Task: `T291`
Capability: `PUB-026`
Status: `BLOCKED_CAPACITY_AND_FINANCIAL_POLICY`

## Decision

The mixed room-type cart cannot be implemented without inventing how guests are allocated across lines and how a versioned multi-line quote is calculated, expired and refreshed. Those decisions are explicitly blocked by T281 and T282.

## Structural Dependency

- Current create request accepts one room type and creates one detail plus one active hold.
- The hold model/repository permits only one active hold per reservation; mixed lines need an additive contract keyed by reservation and room type with atomic consume/release/expiry.
- T289 validated DTO/server snapshot work and T290 same-type atomicity must precede the broader transaction.

## Required Decisions

- Guest allocation across line quantities, including adult-per-room and null/zero capacity rules.
- Duplicate-line normalization, line/quantity caps and canonical idempotency identity.
- Versioned per-line and combined quote components, rounding, TTL and stale-line recovery.
- Partial-unavailable UX while guaranteeing zero partial reservation/detail/hold/payment persistence.

## Safety Boundary

No multi-hold migration, multi-line endpoint, client cart, quote arithmetic or partial booking is introduced. Any future implementation must lock room types in deterministic ID order, require one sellable property, validate every line before persistence and roll back all lines on failure.

## Resume Condition

Approve T281/T282, complete T290, then design the additive multi-hold migration and canonical multi-line request/quote/idempotency contract with concurrency, rollback, tenant and browser evidence.
