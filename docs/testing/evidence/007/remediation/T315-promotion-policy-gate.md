# T315 - Promotion Policy Stop Gate

Task: T315 / STAY-027  
Branch: `codex/stay-lifecycle`  
Status: `BLOCKED_EXTERNAL`

## Safe behavior retained

- The public promotion presentation can display and copy supplied codes, but it has
  no quote, reservation, ledger or settlement effect.
- No promotion catalog, eligibility adapter, stacking decision, redemption record or
  sponsored-placement ranking mutation is enabled.
- Existing booking and staff-booking quotes therefore remain server-authoritative and
  cannot silently apply an unapproved discount.

## Missing approved decisions

No versioned product/finance-owner policy was found for OQ-002 through OQ-005. The
project backlog explicitly keeps T181 blocked until those decisions are approved.
Implementation still requires approved rules for:

- promotion eligibility, validity windows, inventory scope and customer segmentation;
- membership tiers, benefits, qualification and downgrade/expiry behavior;
- stacking precedence, maximum discount, rounding and interaction with taxes, fees,
  cancellation, refund and manual reservations;
- redemption reservation/commit/release timing, per-user and global limits, concurrency,
  abuse controls and reversal behavior;
- sponsored-placement disclosure, ranking boundaries, billing basis and separation from
  organic search relevance.

Choosing any of these rules would invent commercial and financial policy and could
change customer charges, tenant revenue and marketplace ranking. This matches the
explicit project stop gate.

## Required unblock input

Provide a versioned policy approved by the product and finance owners that resolves
OQ-002 through OQ-005. After approval, T315 still requires quote/booking consistency,
tenant and IDOR isolation, stacking/redemption concurrency tests, immutable redemption
evidence and localized admin/customer lifecycle UI before promotion to complete.

No code, migration, credential, provider call or real-money action was performed for
this gate.
