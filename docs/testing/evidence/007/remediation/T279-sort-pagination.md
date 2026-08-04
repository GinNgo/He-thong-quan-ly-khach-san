# T279 Sort And Pagination Evidence

Date: 2026-08-04
Branch: `codex/public-booking`
Capability: `PUB-011`
Validation status: `BLOCKED_RUNTIME`

## Implemented Contract

- Public sort values are normalized and limited to `POPULAR`, `NEAREST`, `PRICE_ASC`, `PRICE_DESC` and `RATING`; unknown values return HTTP 400.
- Every ordering has a stable hotel-ID tie-breaker. Price ordering uses the bounded displayed-price projection established by T278.
- Request pages are one-based, response page numbers remain Spring zero-based, and invalid page numbers or sizes are rejected instead of silently clamped.
- A page beyond the last page returns empty content with authoritative totals. The Angular route then replaces it once with the last valid one-based page while preserving the canonical search query.
- Sort and filter changes reset to page one; paginator changes preserve filters and sort.

## Focused Validation

- Backend `PropertySearchControllerIntegrationTest`: 22/22 PASS, zero failures/errors/skips. The matrix covers all five sorts, repeatability, tie ordering, page-through count parity, no duplicates, invalid inputs, coordinate-only NEAREST count binding and high out-of-range pages.
- Frontend focused suite: 5 files / 32 tests PASS. Final query/page recheck: 2 files / 16 tests PASS. Angular development build: PASS.
- Playwright collection: 3 tests discovered in `property-search-sort-pagination.api.spec.ts`.

## Runtime Blocker

The final real-backend Playwright execution did not reach product assertions. Four runtime approaches were attempted:

1. The original Playwright Maven command was parsed by PowerShell as an invalid lifecycle phase.
2. Quoting the Spring profile exposed unrelated UTF-8 BOM compilation failures in `UserService.java` and `UserController.java`.
3. A temporary compiler exclusion allowed main compilation, but `spring-boot:run` then compiled unrelated cross-domain tests with missing parallel-branch classes.
4. Adding `-Dmaven.test.skip=true` and the exact temporary exclusions still failed to produce a listening backend before the bounded five-minute Playwright run timed out.

The permanent E2E harness now quotes the Spring profile and skips unrelated test compilation. The temporary compiler overlay and task-owned runtimes were removed. Because no API-backed browser assertion passed, `PUB-011` remains `PARTIAL` and T279 must not be declared complete until the three Playwright cases pass against a runnable backend.

## Permissions And Isolation

This is an anonymous public read path with no tenant mutation. Tests use deterministic H2 fixtures and test-only secrets; no production credential, production data, destructive migration or real-money operation is involved.
