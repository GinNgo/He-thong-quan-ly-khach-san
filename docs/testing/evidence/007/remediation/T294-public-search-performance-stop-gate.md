# T294 Public Search Performance Evidence

Task: `T294`
Capability: `PUB-029`
Status: `BLOCKED_PERFORMANCE_ACCEPTANCE_AND_RUNTIME`

## Implemented Bounded Projection

- Page enrichment batch-loads active room types, ordered property images and province compatibility instead of querying per hotel.
- Physical room pool and overlapping reserved quantity are grouped for all page room types, preserving the T280 availability policy.
- The lowest eligible room, displayed pricing, availability, gallery and province name are assembled from bounded maps while preserving existing eligibility, price-bound and ordering semantics.
- Frontend continues one search request per settled route, cancels stale requests and renders max page size 100 in authoritative API order.

## Focused Verification

| Suite / measurement | Result |
|---|---|
| `PropertySearchBoundedQueryIntegrationTest` | 1/1 PASS; exact prepared statements: page 1 undated `5`, page 100 undated `5`, page 100 dated `6` |
| `PropertySearchControllerIntegrationTest` | 22/22 PASS |
| `RoomAvailabilityPolicyTest` | 3/3 PASS |
| `RoomAvailabilityConsistencyIntegrationTest` | 2/2 PASS |
| Backend aggregate | 28/28 PASS |
| Property-search page frontend | 12/12 PASS |
| Angular development build | PASS |
| `git diff --check` | PASS |

The statement budget is constant with result count and room-type count in the representative H2 fixture; dated search adds one grouped reservation statement. Frontend tests prove one settled request, stale-result suppression and exact 100-card order/total rendering.

## Performance Stop Gate

- SQL Server is listening locally on port 1433, but this worktree has no authorized database credential and no `sqlcmd`; Docker CLI was not reliably responsive.
- No approved nationwide fixture cardinality/distribution, concurrency level or public-search p95 threshold exists.
- Without representative SQL Server actual plans, logical reads and measured p95, adding speculative V60 indexes would not be defensible. Current migrations end at V59 with no numeric collision, but no migration is created here.
- Browser timing remains blocked by the shared Playwright backend startup timeout.

## Safety And Consistency

No financial/capacity/payment policy is changed. No schema, production data, credential or destructive operation is involved. T274 eligibility and T278-T280 price/order/availability regressions remain green.

## Promotion Condition

Provide an authorized isolated SQL Server benchmark target and approved representative fixture/acceptance budget. Capture warm p50/p95/p99, query plans/logical reads and index before/after evidence, then pass max-page browser consistency/timing before promoting PUB-029.
