# T298 - Cancellation Fee Policy Stop Gate

Task: T298 / STAY-004  
Branch: `codex/stay-lifecycle`  
Status: `BLOCKED_EXTERNAL`

No versioned owner-approved policy defines cancellation windows, timezone/cutoff basis,
fee amount or percentage, VND rounding, taxes/fees, deposit treatment, partial-stay cases,
manual overrides, provider allocation, or the effect on tenant revenue and invoices.
There is consequently no lawful snapshot to persist at quote/booking time and no truthful
refund preview to show before customer confirmation.

Implementing a convenient demo rule would invent financial policy and can change both the
customer refund and tenant settlement. The current behavior is not treated as an approved
policy merely because legacy cancellation may request a full refund.

Unblock with a versioned policy approved by product/finance owners. Implementation then
requires immutable booking snapshots, authoritative server preview/recheck, tenant and IDOR
tests, concurrent cancellation/replay tests, exact VND ledger/refund reconciliation and
localized customer/staff UI. No code, migration or money movement was performed.
