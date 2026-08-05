# T282 Pricing Policy Stop Gate

Task: `T282`
Capability: `PUB-014`
Status: `BLOCKED_FINANCIAL_POLICY`

## Decision

Implementation is stopped because the project has no approved, versioned public quote policy. The current hard-coded pricing multiplier cannot be promoted into a search-detail-checkout contract without inventing financial rules.

## Missing Required Decisions

- Tax rates, inclusive/exclusive treatment, jurisdiction and effective dates.
- Fee types, beneficiary, taxable basis and display requirements.
- Discount eligibility, stacking and precedence.
- Occupancy/date surcharges and exact VND rounding points.
- Policy version ownership, quote identity and quote expiry duration.
- Whether an expired or catalog-stale quote is rejected or refreshed, and how the customer confirms a changed total.

## Current Boundary

- Search/detail pricing is derived from mutable room-type base price and a fixed 15% multiplier.
- No persisted quote identity/version/expiry or canonical component snapshot exists.
- Downstream checkout-context and reservation-snapshot tasks must consume, not independently invent, this policy.

## Verification

- Read-only review covered spec, plan, constitution, task, inventory and the search/detail/reservation pricing flow.
- No `Active Parallel Claims` table exists in this worktree, and no T282 completion evidence or competing claim was found.
- No production credential, payment transaction, migration or executable pricing behavior was changed.

## Resume Condition

Resume only after the missing pricing decisions are approved and versioned. Then implement one server-authoritative quote service with identity, components, expiry and stale-price behavior; prove exact parity through search, detail, checkout and locked reservation snapshot tests.
