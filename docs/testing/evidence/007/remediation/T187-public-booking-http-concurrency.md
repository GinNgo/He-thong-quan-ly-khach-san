# T187 Public Booking HTTP And Concurrency Evidence

Date: 2026-08-02
Base commit: `f211a4f271467f362aa85172cf1d665da929d379`
Profile: `test`
Database: H2 `create-drop`; Flyway disabled
Production credentials or real-money operations: N/A

## Command

```powershell
Set-Location backend
.\mvnw.cmd -q '-Dtest=PublicDiscoveryControllerIntegrationTest,PropertySearchControllerIntegrationTest,ReservationConcurrencyIntegrationTest,ReservationHoldIntegrationTest' test
```

## Result

| Suite | Tests | Passed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| `PublicDiscoveryControllerIntegrationTest` | 6 | 6 | 0 | 0 | 0 |
| `PropertySearchControllerIntegrationTest` | 5 | 5 | 0 | 0 | 0 |
| `ReservationConcurrencyIntegrationTest` | 1 | 1 | 0 | 0 | 0 |
| `ReservationHoldIntegrationTest` | 2 | 2 | 0 | 0 | 0 |
| **Total** | **14** | **14** | **0** | **0** | **0** |

The suites execute the real `BackendApplication`, MVC controllers, JPA repositories,
reservation transactions and H2 locking. They cover accent-insensitive grouped suggestions,
property and address suggestions, active landmark disambiguation, the current 34-province
projection, approved-property search, authoritative stay pricing, landmark distance ordering,
invalid-landmark rejection, simultaneous last-room booking and exactly-once hold expiry.

## Harness Repairs

- Pinned all four Spring integration suites to `BackendApplication`, eliminating ambiguous
  nested `@SpringBootConfiguration` discovery.
- Added only a non-secret, annotation-scoped property-payment encryption key.
- Replaced the discovery fixture UUID with deterministic `PUB030` identifiers while preserving
  current-province, legacy-alias and landmark coverage.
- Persisted an enabled simulator payment configuration with deposit policy `NONE` for the
  concurrency fixture so booking reaches the inventory lock instead of failing policy setup.
- Added diagnostic concurrent-outcome text to expose captured exception types and messages.

## Checksums

| Artifact | SHA-256 |
|---|---|
| `PublicDiscoveryControllerIntegrationTest.java` | `94306a00f633ba18e28046e022c95f7a91cd2e3f226205a5166ceadd629281a5` |
| `PropertySearchControllerIntegrationTest.java` | `6ee0782304bde8e62080bb70087401f808d54f2479a70f90d115bd7c32927760` |
| `ReservationConcurrencyIntegrationTest.java` | `117c9d903e45480a310db99d658981ee78935e7f00d1be7cf23634ebae1e2222` |
| `ReservationHoldIntegrationTest.java` | `2b0db6c573b4658ff3a9fd71d8965796e76085a93f0624697a197bbd838750c4` |
| Public-discovery Surefire XML | `ef07e48ec541de65a0945fd54af3777bd43ce5df58a5adf1f1b0ca2e17d7fa2a` |
| Property-search Surefire XML | `a6d2f65f1d2515bde85c313a626b25f170103c85550db55d4d2539a04cab7170` |
| Reservation-concurrency Surefire XML | `6fd0f68769cd7894800fce4c51fdbbeb1fa0cc488cf5e98c654043545a8d0ca5` |
| Reservation-hold Surefire XML | `fdb5d070f3101d9a49139c0ecbeae850ed6142df928406ce80a92eee785a8da3` |

## Remaining Public Booking Scope

- The harness is complete, but a live API-backed browser search-to-detail-to-booking journey
  remains tracked by PUB-001, PUB-003, PUB-005, PUB-018, PUB-019 and PUB-028.
- Payment/expiry races, restart recovery and SQL Server locking remain PUB-024 work.
- Public property/room-type eligibility, booking idempotency and configured payment-method truth
  remain PUB-015, PUB-016, PUB-021 and PUB-023.
- No production provider, production database, real-money flow or destructive cleanup was used.
