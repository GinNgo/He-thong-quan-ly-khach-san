# T308 - Debt and Overpayment Policy Stop Gate

Task: T308 / STAY-018  
Branch: `codex/stay-lifecycle`  
Status: `BLOCKED_EXTERNAL`

## Safe behavior already present

- Checkout derives outstanding and overpaid balances from the authoritative folio.
- Debt checkout requires `RESERVATION_DEBT_OVERRIDE:APPROVE`, a non-trivial reason,
  a current locked recheck and immutable override/audit evidence.
- Overpayment remains fail-closed with `OVERPAYMENT_REQUIRES_RESOLUTION`; a debt
  override cannot bypass it.
- Existing focused tests cover permission, tenant ownership, stale-state recheck,
  idempotent settled behavior, invalid reason/amount and the overpayment block.

## Missing approved decisions

No versioned owner-approved policy was found for:

- whether excess property payment is refunded to the original method, retained as
  guest credit, or handled through another accounting liability;
- partial versus full resolution, provider/manual handling and original-payment allocation;
- approval roles, monetary limits, dual control and required evidence for debt overrides;
- accounting timing and the effect on invoice, property revenue and customer balance.

Implementing any of these choices would invent a financial policy and can change
the customer/property monetary result. This matches the explicit project stop gate.

## Required unblock input

Provide a versioned policy approved by the product/finance owner covering the
decisions above. After approval, T308 still requires HTTP/tenant/concurrency tests,
frontend resolution states and exact ledger/invoice reconciliation before it can
be promoted to complete.

No code, migration, credential or real-money action was performed for this gate.

