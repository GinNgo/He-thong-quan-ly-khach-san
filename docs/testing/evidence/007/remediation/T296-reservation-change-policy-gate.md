# T296 Reservation Amendment Lifecycle Evidence

Status: `COMPLETE_VERIFIED`

Implementation commit: `58d75a4 feat(T296): complete reservation amendment lifecycle`

## Approved Policy

- Only `PENDING_PAYMENT` and `CONFIRMED` reservations are editable.
- Customer access requires reservation ownership. Staff access requires the dedicated
  `RESERVATION_AMEND` permission and property access.
- Every quote re-locks room types, rechecks capacity and availability, and creates an
  expiring inventory hold. Structural changes are blocked while physical rooms are assigned.
- Price increases require one exact successful `AMENDMENT_DELTA` payment and its matching
  immutable ledger effect before the reservation changes.
- Price decreases create an idempotent refund only for paid excess above the repriced deposit.
  A refund that needs source selection or splitting fails `POLICY_NOT_CONFIGURED`.
- The original discount is preserved as fixed VND, capped by the new gross price. The original
  deposit-policy identity/version is preserved and only its current projection is repriced.
- The property-local arrival cutoff uses `Asia/Ho_Chi_Minh` by default. Safe defaults are a
  1440-minute cutoff and 15-minute quote TTL; demo/e2e profiles use 5 minutes and 2 minutes.

## Implemented Boundary

- Additive migrations `V53` and `V54` create immutable amendment snapshots, quote/payment/refund
  links, indexes, checks and the dedicated permission. A destructive-keyword scan found no
  `DROP`, `TRUNCATE` or `DELETE` statement.
- Customer and management APIs expose context, quote, quote status, delta-payment attempt and
  apply commands with ownership/tenant checks and idempotency keys.
- Availability excludes the current reservation while subtracting active amendment holds, and
  room types are pessimistically locked in deterministic order.
- Customer profile and admin reservation management share one responsive server-priced workspace
  with quote countdown, requote, delta-payment polling, refund-pending state and retry-stable keys.
- Operational audit events retain quote and applied before/after snapshots. The reservation,
  reservation detail, pending hold, deposit projection and refund request change in one transaction.

## Focused Validation

| Validation | Result |
|---|---|
| Isolated backend main compilation | PASS; 429 production source files compiled after excluding only unrelated baseline BOM files and the independently incomplete platform billing controller from the temporary copy |
| Backend focused JUnit suite | PASS 46/46: deposit snapshot, amendment policy/service, exact delta attempt/expiry, single-source refund policy and tenant-filter architecture |
| Frontend focused Angular/Vitest suite | PASS 15/15: customer/admin endpoint separation, quote rendering/2-minute expiry, requote invalidation, duplicate-submit protection, customer read integration and dedicated permission visibility |
| `git diff --cached --check` before source commit | PASS |
| Migration destructive-keyword scan | PASS |

The normal worktree Maven compile remains blocked before T296 sources by pre-existing UTF-8 BOMs
in `UserController.java` and `UserService.java`. The normal Angular production build reaches the
compiler and remains blocked by pre-existing missing `LocaleService` and `PublicI18nService`
modules used by payment-panel/invoice work. Focused T296 builds/tests compile the complete changed
surface and the isolated backend suite compiles all production sources relevant to this branch.

## Safety

- No production credentials, merchant configuration or real-money transaction was used.
- Production payment remains disabled and no production migration was executed.
- Unsupported refund splitting/source selection fails closed rather than inventing a financial rule.
