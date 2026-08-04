# T307 - Authoritative Checkout Preview Evidence

Task: T307 / STAY-017  
Branch: `codex/stay-lifecycle`  
Implementation commit: `a5baf5c`

## Verified behavior

- `POST /api/management/reservations/{id}/checkout-preview` returns the complete
  server-owned folio, settlement gate, blocking error and source version.
- `CHECKOUT:VIEW` is enforced by the HTTP permission interceptor; `CREATE`
  without `VIEW` returns `403 FORBIDDEN_PERMISSION`.
- Cross-property/not-owned identifiers preserve `404 RESOURCE_NOT_FOUND` at the HTTP boundary.
- A preview is informational, not an authorization token. The database test first
  sees a 100,000 VND outstanding balance, then persists a settling payment and
  sees `SETTLED`; a later 1 VND surcharge makes `requireSettled` fail, and a
  subsequent 1 VND refund produces a freshly recomputed 2 VND balance.
- The folio source version increases after authoritative ledger changes, so the
  UI can identify stale evidence while checkout continues to revalidate on the server.

## Focused validation

Isolated backend snapshot:

```text
mvnw.cmd -Dtest=FolioDatabaseReconciliationIntegrationTest,PropertyCheckoutPreviewHttpTest test
```

- Database reconciliation suite: `3/3` passed. The combined run later failed only
  because the first HTTP slice setup selected the nested test application; its
  database Surefire report remained green.
- Corrected HTTP slice: `3/3` passed with `BUILD SUCCESS`.

Direct worktree Maven remains blocked before test compilation by the pre-existing
UTF-8 BOM errors in shared `UserController.java` and `UserService.java`; T307 did
not edit those files.

## Scope and recovery

- No migration, frontend change, production credential or real-money operation applies.
- No overpayment/debt/refund policy was introduced; only existing fail-closed states are asserted.
- Rollback is removal of the two focused regression-test changes and this evidence commit.

