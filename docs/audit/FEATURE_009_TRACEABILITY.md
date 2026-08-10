# Feature 009 Traceability

| Scope | Requirements | Planned evidence |
|---|---|---|
| Dynamic function permissions | FR-001 through FR-009, FR-013 through FR-018 | Permission matrix, revocation, route/menu, tenant and audit tests |
| Operational tasks | FR-010 through FR-012 | Assignment, revocation, reassignment and concurrency tests |
| Booking VNPay | FR-019 through FR-027 | Checkout URL, IPN validation, replay, mismatch and booking-effect tests |
| Subscription VNPay | FR-019 through FR-029 | Order snapshot, platform merchant, replay, recovery and entitlement tests |
| Accountant reconciliation | FR-030 | Property/platform separation, exact VND totals, export and denial tests |

## Boundaries

- Production payment enablement and real-money execution are excluded.
- Destructive migration and ambiguous legacy financial backfill require separate approval.
- Existing unrelated worktree changes are preserved.

