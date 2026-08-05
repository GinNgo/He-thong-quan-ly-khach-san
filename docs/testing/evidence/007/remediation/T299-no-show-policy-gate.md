# T299 - No-Show Policy Stop Gate

Task: T299 / STAY-005  
Branch: `codex/stay-lifecycle`  
Status: `BLOCKED_EXTERNAL`

The dedicated `RESERVATION_NO_SHOW:UPDATE` authorization boundary exists, but no
owner-approved policy defines when a staff actor may declare no-show or what follows.
Missing decisions include property timezone and arrival threshold, grace/late-arrival
handling, retained/refunded amount, deposit and tax treatment, room/hold release timing,
invoice/revenue effects, override authority and reversal/correction behavior.

Enabling the UI or choosing consequences without these decisions could prematurely release
inventory or retain/refund the wrong amount. A short demo threshold is still a financial and
operational policy, so it is not invented here.

Unblock with a versioned product/operations/finance-approved policy. Then implement a
reasoned audited command with locked server-time recheck, exact permission and tenant
isolation, idempotent replay, early denial, concurrent invocation, late-arrival correction,
financial reconciliation and localized UI tests. No runtime code, migration or financial
mutation was added.
