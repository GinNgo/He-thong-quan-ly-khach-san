# T296 Reservation Change Policy Stop Gate

Status: `BLOCKED_EXTERNAL` - no implementation or financial mutation was enabled.

## Required Decision

T296 changes booking dates, guest counts, room quantity or room type after the
original authoritative quote. That can change inventory, deposit requirements,
amount already collected, refund liability, discounts and the final invoice.
The repository does not contain an approved policy that determines those results.

Before implementation, the owner must approve a versioned policy covering:

1. Editable reservation states and the cutoff relative to arrival/check-in.
2. Whether a price increase creates a required payment before the change commits,
   an outstanding balance, or blocks the change.
3. Whether a price decrease creates a provider refund, property credit, retained
   fee, or another explicitly defined outcome.
4. Treatment of deposit requirements, cancellation/change fees and already
   finalized financial evidence.
5. Whether promotions, membership prices and sponsored discounts are preserved
   from the booking snapshot or re-evaluated during the new quote.

## Independent Discovery Paths

### 1. Feature 007 artifacts

Searched `spec.md`, `plan.md`, `research.md`, `data-model.md` and
`contracts/financial-api-contract.md` for reservation amendments, re-quote,
price-delta and change-refund rules. The artifacts define authoritative amounts,
refund balance safety and policy gates, but no reservation-change outcome.

### 2. System and financial documentation

Searched the stay inventory, API specification, ERD/UML, architecture and
financial audit documents. STAY-002 is explicitly recorded as missing. The API
spec also states that new booking aggregates require pricing and cancellation
rules rather than permitting them to be inferred.

### 3. Backend domain and tests

Searched reservation, availability, payment, refund, folio and policy source/tests.
The current reservation service has no amendment command or quote snapshot. The
legacy `refundSuccessfulPayments()` path refunds successful payments for full
cancellation; it does not define a partial price-decrease policy and cannot be
reused as one without approval.

### 4. Frontend and browser journeys

Searched Angular services/components and Playwright journeys. There is no reachable
reservation amendment UI or typed request. Existing cancellation/refund UI accepts
an explicit server-validated refund amount but contains no rule for booking-change
price deltas.

## Safety Result

- No migration was created.
- No reservation, payment, refund, invoice or ledger behavior was changed.
- No production credential or real-money provider was used.
- T296 remains incomplete until the policy above is approved.
