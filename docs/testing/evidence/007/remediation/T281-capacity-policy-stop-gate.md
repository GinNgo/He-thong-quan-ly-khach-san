# T281 Capacity Policy Stop Gate

Task: `T281`
Capability: `PUB-013`
Status: `BLOCKED_POLICY`

## Decision

Implementation is stopped because no approved artifact defines the legacy adult, child and total-capacity semantics required by this task. Choosing a normalization would change which rooms can be sold and which bookings are accepted.

## Conflicting Current Semantics

- `V1__unicode_search_inventory.sql` backfills legacy `max_children = NULL` to `0`, but no artifact says whether zero means explicitly child-free or unknown legacy data.
- Search SQL/Java, detail availability and the frontend do not apply the same fallback for `null` and `0` capacity values.
- Omitted guest parameters are capacity-neutral in canonical search, default to two adults in the hotel-detail UI, and are handled differently by reservation validation.
- Search/detail multiply adult capacity by room quantity, while booking only requires at least one adult overall; no approved rule requires one adult per room.

## Required Policy Decisions

1. Define `maxChildren = 0` and `null` independently: explicit child-free, unknown/fallback, or unsellable.
2. Define omitted guest fields: reject, capacity-neutral, or canonical defaults.
3. Define whether `adultCount >= roomQuantity` is mandatory.
4. Define whether all-null/all-zero capacity is rejected or receives a documented fallback.

## Verification

- Read-only review covered `spec.md`, `plan.md`, constitution, `tasks.md`, domain inventory, current evidence, migration and search/detail/reservation implementations.
- No `Active Parallel Claims` table exists in this worktree, and no T281 completion evidence or competing claim was found.
- No code, migration or executable behavior was changed.

## Resume Condition

Resume only after the decisions above are approved and versioned. Then implement one shared capacity policy across search, detail availability and locked reservation validation, followed by null/zero, boundary, mixed-age and quantity tests.
