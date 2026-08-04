# T297 - Customer Cancellation Policy Gate

Task: T297 / STAY-003  
Branch: `codex/stay-lifecycle`  
Status: `BLOCKED_EXTERNAL`

The existing owner-bound cancellation command and focused unit/UI coverage are retained.
Completing the requested reason/confirmation, concurrent refund recovery and real browser
cancellation-to-refund journey depends on the versioned cancellation window/fee/refund
policy tracked by T298. Without it, the system cannot truthfully calculate the amount to
refund or retain after cancellation.

A pre-existing `stash@{0}` named `wip T297 awaiting cancellation fee policy` remains
untouched and unapplied. Applying it before policy approval could change customer and
tenant money outcomes. A disposable authenticated browser/provider fixture is also absent,
as independently recorded by T316.

Unblock with an owner-approved versioned T298 policy plus a non-production provider/browser
fixture. Then add HTTP ownership/IDOR, concurrent replay, provider failure/retry and exact
ledger/refund assertions before promotion. No migration, provider call or real-money action
was performed.
