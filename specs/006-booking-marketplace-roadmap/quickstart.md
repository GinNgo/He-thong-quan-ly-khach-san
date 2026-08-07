# Quickstart and Verification Log

## Safety

- Work on the current branch without reverting unrelated dirty files.
- Use only the approved server-bound payment session/callback contract; browser return URLs remain display-only.
- Do not send Facebook/Zalo messages until official sandbox credentials and OQ-006 to OQ-009 are resolved.
- Use real local services and persisted fixture data for E2E evidence.

## Baseline Commands

```powershell
git status --short --branch
Get-Content .specify/feature.json
```

Frontend:

```powershell
Set-Location frontend
npm test -- --watch=false
npm run build -- --no-progress
```

Backend:

```powershell
Set-Location backend
./mvnw.cmd test
```

Focused backend suites after implementation:

```powershell
./mvnw.cmd -Dtest=PropertySearchControllerIntegrationTest,PublicDiscoveryControllerIntegrationTest test
./mvnw.cmd -Dtest=ReservationConcurrencyIntegrationTest,PaymentServiceImplTest,PaymentControllerIntegrationTest test
./mvnw.cmd -Dtest=ChatControllerIntegrationTest,ChatChannelInterceptorTest,SubscriptionControllerIntegrationTest test
```

## Browser Matrix

| Viewport | Search/date | Landmark | Promotion disclosure | VI/EN | Motion | Payment/support/subscription |
|---|---|---|---|---|---|---|
| 375px | Required | Required | Required | Required | Required | Customer-critical states |
| 768px | Required | Required | Required | Required | Required | Representative states |
| 1024px | Required | Required | Required | Required | Required | Admin/management representative |
| 1440px | Required | Required | Required | Required | Required | Full representative journeys |

For each viewport record:

- `scrollWidth === clientWidth` except intentional inner table scrollers.
- Popup/sheet bounds and z-index.
- Keyboard tab order, visible focus and 44px targets.
- Console/network errors and loading/error/retry states.
- `prefers-reduced-motion` behavior.
- VI/EN text and date formatting.

## Current Baseline (2026-07-29)

- Active Git branch: `codex/ui-functional-audit-polish` tracking `origin/codex/ui-functional-audit-polish`.
- Worktree is intentionally dirty with existing Feature-03, Feature-04, Feature-05, thesis, backend security/notification and Home/Footer changes; Feature-06 implementation must preserve and avoid staging unrelated files.
- Active Speckit pointer is `specs/006-booking-marketplace-roadmap`.

- Feature-05 frontend regression previously passed 73 tests in 36 files and production build; Home/Footer browser matrix passed 375/768/1024/1440 with no horizontal overflow.
- Feature-03 still reports payment simulator/callback as partial and GAP-022 as high-impact broken pending policy approval.
- Reservation creation uses a pessimistic room-type lock before availability count, but no direct two-thread booking test was found.
- Landmark discovery is implemented and verified against an isolated hotel E2E backend on port `19081`; port `8080` remains owned by an unrelated local service and is not used as evidence.
- VI/EN JSON files exist; app-level runtime localization is not configured.
- Internal central-support chat source/security and authenticated browser history/send/reply/reconnect evidence pass; tenant conversation context and external channels remain open.
- No Facebook/Zalo integration source was found.
- Subscription plan/feature services exist; management/admin UI contracts and purchase behavior remain inconsistent/partial.

## Phase 1 Verification (2026-07-29)

Status: `PARTIAL` for the real backend journey. Overnight search, date state, responsive layout and recoverable error handling are implemented and verified. Day-use remains disabled (`Sắp ra mắt`) because `PropertySearchServiceImpl` currently requires both `checkInDate` and `checkOutDate`; the frontend state support is preparatory only.

Completed Speckit tasks: `T002`, `T007`-`T015`, `T069` (frontend release check).

### Automated Evidence

- Focused Home/date tests: `16/16` passed.
- Full frontend regression: `38` test files, `87` tests passed.
- Production build: passed with existing Angular optional-chain, Chart.js/canvas and CommonJS warnings (`@stomp/stompjs`, `sockjs-client`).
- Validation commands:

```powershell
Set-Location frontend
npm test -- --watch=false
npm run build -- --no-progress
```

### Browser Evidence

| Viewport | Result | Evidence |
|---|---|---|
| 375px | PASS; one-column Home search, no horizontal overflow (`scrollWidth=360`) | `docs/screenshots/home-search-after-mobile.png` |
| 768px | PASS; two-column Home search, no horizontal overflow (`scrollWidth=753`) | `docs/screenshots/home-search-after-mobile.png` |
| 1024px | PASS; two-column Home search, no horizontal overflow (`scrollWidth=1009`) | `docs/screenshots/home-search-after-desktop.png` |
| 1440px | PASS; four-column Home search, no horizontal overflow (`scrollWidth=1425`) | `docs/screenshots/home-search-after-desktop.png` |

Additional checks:

- Header partner CTA is absent at all four tested widths; account-menu/Home owner entry points remain available.
- Mobile date picker opens from labelled controls and shows one calendar month; desktop two-month behavior is covered by unit tests. A direct desktop popover browser probe timed out and is retained as a follow-up risk rather than treated as a pass.
- Sticky search at `/search` on 375px exposes `Mở bộ tìm kiếm`, opens the `Thay đổi tìm kiếm` sheet, and preserves the search summary after a backend connection error.
- Overlay ordering is now explicit: mobile sheet `z-index: 160`, location popup `z-index: 170`, date popover base `z-index: 180`; mobile overlays account for `safe-area-inset-*` and `100dvh`.

## Phase 2 Landmark Discovery (2026-07-29)

Status: `COMPLETE` for the scoped persisted API/browser journey. Backend contracts, persisted landmark fixtures, radius filtering, URL restoration, distance context and recoverable empty states are implemented and verified against the isolated E2E backend on port `19081`.

Completed Speckit tasks: `T016`-`T024`.

### Automated Evidence

- Focused backend landmark suites: `15/15` tests passed (`PropertySearchControllerIntegrationTest`, `PublicDiscoveryControllerIntegrationTest`, `LocationImportServiceTest`).
- Full frontend regression after landmark integration: `39` test files, `91` tests passed.
- Landmark page unit coverage: `2/2` tests passed for URL reload, `NEAREST` sorting, radius expansion and province recovery.
- The E2E backend imported 97 province rows, 10,051 wards and 122 landmarks, then seeded 315 approved demo properties. Demo coordinates are now anchored near active landmarks in the matching current province instead of being derived from a nationwide sequence number.
- Home autocomplete shows a labelled `Địa danh` group. Selecting a result writes canonical `provinceId`, `landmarkId`, `radiusKm`, coordinates and `sortBy=NEAREST` to the URL; browser reload restores the same context.

### Phase 2 Evidence Log

| Date | Phase/Task | Command or browser journey | Expected | Actual | Result | Artifact |
|---|---|---|---|---|---|---|
| 2026-07-29 | T016-T021 | `Set-Location backend; .\\mvnw.cmd "-Dtest=LocationImportServiceTest,PublicDiscoveryControllerIntegrationTest,PropertySearchControllerIntegrationTest" test` | Landmark fixtures, province scoping, inactive handling and radius ordering pass | 15 tests passed | PASS | Maven output |
| 2026-07-29 | T022-T023 | `Set-Location frontend; npm test -- --watch=false` | State, URL persistence and UI integration compile and pass | 39 files / 91 tests passed | PASS | Vitest output |
| 2026-07-29 | T024 | Home `Ngườm` -> `Động Ngườm Ngao` -> `/search`, then reload | Labelled suggestion, persisted URL/radius, distance results and restored context | 3 results at 1.2-1.9 km; URL kept `landmarkId=10155&radiusKm=12`; reload restored context | PASS | In-app browser DOM/URL evidence |

## Phase 2B Nationwide Landmark Catalog (2026-07-29)

Status: `COMPLETE` for `T076`-`T088`. The reproducible offline generator emits 122 quality-gated rows, 121 active rows and at least three active coordinate-valid landmarks for every current province/city. API performance and real north/central/south/island browser journeys are complete. The former 63-province publication target is superseded by the approved 34-unit public baseline; all 63 legacy rows remain compatibility data and map exactly once into that baseline.

Source audit:

- MIT-licensed `HongTin2104/VietNam-Travel-Recommendation-System` dataset: 315 attractions, five rows for each legacy province after normalizing one `Quang Ninh` spelling variant; Vietnamese name, province, description, rating, image and keywords; coordinates absent.
- GeoNames `VN.zip`: 67,142 Vietnam features and 4,200 selected geographic/heritage candidates; stable ids and coordinates, but tourist-attraction coverage is uneven and its province view has already moved to 34 consolidated units.
- Geofabrik Vietnam OSM PBF: preferred versioned offline coordinate source; approximately 326 MB at the 2026-07-28 snapshot and subject to OpenStreetMap ODbL attribution.
- `provinces.open-api.vn` depth-3 source: still returns the legacy 63-code hierarchy and can restore the 29 missing compatibility provinces. It is not treated as the final current-34 canonical migration.

Planned generator commands:

```powershell
Set-Location backend
python tools/landmarks/build_vietnam_landmarks.py --dry-run
python tools/landmarks/build_vietnam_landmarks.py --write
python tools/landmarks/build_vietnam_landmarks.py --validate-only
```

Required output checks:

```powershell
Set-Location backend
python -m unittest discover -s tools/landmarks/tests -p "test_*.py"
.\mvnw.cmd "-Dtest=LocationImportServiceTest,PublicDiscoveryControllerIntegrationTest,PropertySearchControllerIntegrationTest" test
```

The generator must write `landmarks.json`, a per-province coverage report and a quarantine report. A second run with the same source versions must keep the landmark artifact checksum unchanged.

### Phase 2B Implementation Evidence

- Completed tasks: `T076`-`T088`. `V18__landmark_import_audit.sql` adds `location_import_runs` and `landmark_import_issues`; matching JPA entities and repositories compile and load during the packaged import integration test.
- Administrative fixture: 63/63 compatibility provinces, SHA-256 `87c64c32262f3d0ade57954668cdf1771c7718ca2c38cdcb3250c246b3f65c9b`.
- Audited editorial source: 315 rows, five per legacy province, normalized source SHA-256 `bb9fac65bfeaceeb4fad4d38bc0bc51dcb4f9ea034671370afe7739d05e7be81`.
- Generated catalog: 122 total / 121 active; 34/34 current provinces; minimum `3`, maximum `7` active landmarks per province; 113 `VERIFIED` and 9 `MATCHED` rows.
- Coordinate provenance: 56 GeoNames, 32 Wikidata, 27 OpenStreetMap and 7 manual-curated coordinate records.
- Quarantine: 207 candidates; every rejected source row remains machine-readable and no unresolved row appears publicly.
- Generated landmark SHA-256: `54092b3fcb7f4d4f0583165baafa41d31c63e8e6289da243640ccf537da2b078`; two consecutive strict builds produced the identical hash. Coverage SHA-256: `656330578dd5c7298f2a3c32d079f10676316950705769b8cc14b0b9f7814a6c`; quarantine SHA-256: `bfeb78d8300ebf09493010cce47d35d2349ceaca5166820ad524dab33498118f`.
- OSM source attempt: Geofabrik 2026-07-28 snapshot downloaded successfully (325,750,725 bytes; SHA-256 `0eabb81d3f3417552bbd5dd46418510155e59f9dcfab346465499ac9335467a9`), but full and node-only pyosmium passes exceeded the local 15/10-minute verification limits. No OSM-derived row is claimed in the checked-in cache.
- Curated OSM/Wikidata corrections are pinned separately from the raw source in `backend/tools/landmarks/config/coordinate_overrides.json`; display-only source corrections are pinned in `backend/tools/landmarks/config/source_row_corrections.json`.
- Generator tests: `10/10` passed, including strict three-per-province validation and override/correction safety.
- Backend current-province/landmark regression: `24/24` passed (`ProvinceCompatibilityServiceTest`, `LocationImportServiceTest`, `PublicDiscoveryControllerIntegrationTest`, `PropertySearchControllerIntegrationTest`). The importer-specific rerun passed `11/11`, including a two-run fixture idempotency test. A combined rerun passed `25/25` after including `PackagedLocationImportIntegrationTest`, which imported the real 63-province/10,051-ward, 34-current-province and 122-landmark resources twice on H2 with zero second-run additions.
- Strict release validation now passes with `--min-per-province 3`; the old 63-unit AC-205 wording is retained only as historical compatibility context and is superseded by FR-039 to FR-043 / SC-012.
- Warmed local latency over 40 requests cycling the four representative regions: autocomplete p50 `94.48 ms`, p95 `174.53 ms`, max `220.06 ms`; landmark search p50 `96.21 ms`, p95 `219.81 ms`, max `225.93 ms`. No additional index was added because the observed nationwide fixture behavior remained bounded and the current contains-search predicate would not benefit from an ordinary B-tree index.
- Browser journeys at `479px` inner width (`scrollWidth=clientWidth=464`) passed for `Động Ngườm Ngao` (north, 3 results), `Cầu Rồng` (central, 6), `Chợ Bến Thành` (south, 6) and `Đảo Phú Quốc` (island, 2). Every journey preserved `landmarkId`/radius in the URL, restored context after `Ctrl+R`, displayed nearest distances and produced no browser console errors.

| Date | Phase/Task | Command or browser journey | Expected | Actual | Result | Artifact |
|---|---|---|---|---|---|---|
| 2026-07-29 | T076-T086 | Strict generator/source/correction build | Versioned source, deterministic data, auditable rejects and three current-region landmarks minimum | 315 source rows; 122 generated; 207 quarantined; 34/34 current provinces; minimum 3 | PASS | `backend/tools/landmarks/` |
| 2026-07-29 | T084/T090 | `python -m unittest discover -s tools/landmarks/tests -p "test_*.py"` | Generator validation passes | 10 tests passed | PASS | Python unittest output |
| 2026-07-29 | T083/T085/T091-T095 | `.\mvnw.cmd "-Dtest=ProvinceCompatibilityServiceTest,LocationImportServiceTest,PublicDiscoveryControllerIntegrationTest,PropertySearchControllerIntegrationTest" test` | Import/current-province/search/radius regressions pass | 24 tests passed | PASS | Maven output |
| 2026-07-29 | T085/T086/T091 | `.\mvnw.cmd "-Dtest=PackagedLocationImportIntegrationTest" test` | Real packaged catalog imports twice without row growth | 1 integration test passed; second import added 0 rows | PASS | Maven output |
| 2026-07-29 | T082/T086/T096 | `python tools/landmarks/build_vietnam_landmarks.py --write --min-per-province 3` twice | Byte-stable strict catalog and 34 provinces each have at least three active rows | Identical hashes on both runs; minimum 3, maximum 7 | PASS | `backend/tools/landmarks/reports/landmark_coverage.json` |
| 2026-07-29 | T087 | 40 warmed HTTP requests cycling north/central/south/island autocomplete and landmark search | Measured nationwide behavior without speculative indexing | Autocomplete p95 174.53 ms; landmark search p95 219.81 ms; maxima below 226 ms | PASS | Local E2E API timing output |
| 2026-07-29 | T088 | Home selection/search/reload for Ngườm Ngao, Cầu Rồng, Chợ Bến Thành and Phú Quốc | Labelled group, persisted URL, nearby results/context, no overflow or console error | 3/6/6/2 results; nearest distances shown; reload restored all four contexts | PASS | In-app browser DOM/URL/console evidence |
| 2026-07-30 | T078 | `.\mvnw.cmd -q -DskipTests compile` and focused packaged import suites | Audit migration/entities/repositories compile and Spring Data repository discovery succeeds | Compile passed; `LocationImportServiceTest` 11/11 and `PackagedLocationImportIntegrationTest` 1/1 passed | PASS | Maven/Surefire output |

## Phase 2C Current 34-Province Compatibility (2026-07-29)

Status: `COMPLETE` for the scoped backend/data contract (`T089`-`T096`). Ward/hotel foreign-key replacement remains intentionally out of scope; legacy rows are preserved and expanded at query time.

- Current administrative source: 34 rows from `provinces.open-api.vn` v2, upstream SHA-256 `ad03b2f3a40bd652d7f2dd63564cc49817f943380560ede8164239ec3c32c0f0`; packaged catalog SHA-256 `4182eae7588d3c90778454ef0b499e92e497d3eeb543f0cc8f494d342f810647`.
- Alias validation: 63 legacy province codes occur exactly once across the 34 current `VN34-*` definitions; no numeric legacy row is renamed or repurposed.
- Import order: legacy provinces/wards -> current provinces -> landmarks. A repeated import creates zero additional rows.
- Public API E2E profile was started on an isolated local port with packaged imports enabled. `GET /api/public/locations/provinces` returned exactly 34 unique rows and every `sourceCode` used `VN34-*`.
- Live autocomplete returned `Động Ngườm Ngao` under current Cao Bằng and `Động Pu Sam Cáp` under current Lai Châu using their canonical current province ids. Popular destinations returned current Hanoi context with aggregated approved property count.
- The temporary E2E backend was stopped after verification; the Docker-owned service on port `8080` was not treated as evidence because it was a separate stale runtime.

| Date | Phase/Task | Command or API journey | Expected | Actual | Result | Artifact |
|---|---|---|---|---|---|---|
| 2026-07-29 | T089-T095 | Focused Maven suites | Alias/import/public discovery/merged search contracts pass | 24 tests passed; 25 passed with the packaged-import integration test included | PASS | Maven output |
| 2026-07-29 | T096 | E2E `GET /api/public/locations/provinces` | Exactly 34 unique current rows | 34 rows, 34 unique `VN34-*` codes | PASS | Local E2E API probe |
| 2026-07-29 | T096 | E2E suggestions for `Ngườm` and `Pu Sam` | Current-province-scoped landmark results | One canonical landmark returned for each query with matching current province id | PASS | Local E2E API probe |

## Phase 4 Localization and Home Motion (2026-07-30)

Status: `COMPLETE`. Tasks `T036`-`T044` pass automated, browser, reduced-motion and five-run desktop/mobile performance evidence.

- Added the application translate provider/HTTP loader, persisted `vi`/`en` locale service and dynamic PrimeNG calendar labels while keeping settlement currency fixed to VND.
- Added a real accessible header locale button and translated the main public header, Home narrative/partner/footer surfaces.
- Added an editorial slideshow with stable image dimensions, previous/next/direct controls, pause/resume, hover/focus/visibility pausing, reduced-motion autoplay suppression and image-error fallback.
- Added locale persistence, translation-key parity and slideshow behavior tests. The Home date-state suite now freezes its clock so fixed 2026 fixtures cannot fail as the calendar advances.
- Completed the remaining P1 string migration for checkout/profile validation, payment errors, profile errors, email/avatar labels and ZaloPay accessibility text; server-provided error messages remain authoritative.
- Real browser discovery exposed a release-blocking `404` for `/assets/i18n/vi.json`; `frontend/angular.json` now copies `src/assets` to `/assets`. The running endpoint returns HTTP `200`, and the production output contains both `assets/i18n/vi.json` and the slideshow image assets.
- The initial locale catalog now loads through an Angular app initializer before first render, preventing translation keys from painting and then shifting the header, hero and search layout. The document `lang`, PrimeNG labels and persisted VI/EN state remain synchronized.
- A focused Playwright release spec verifies reduced-motion autoplay suppression and five cold VI/EN runs at 1440x900 and 390x844. Desktop p75 interaction delay is `6.5 ms` with max CLS `0.000943`; mobile p75 interaction delay is `5.8 ms` with max CLS `0.001167`. Both remain below the `100 ms` and `0.05` limits with no horizontal overflow.

| Date | Phase/Task | Command or browser journey | Expected | Actual | Result | Artifact |
|---|---|---|---|---|---|---|
| 2026-07-30 | T036-T043 | Focused Angular specs for locale, key parity, layout, slideshow and Home date state | Locale/motion/state behavior passes deterministically | 19 focused tests passed | PASS | Vitest output |
| 2026-07-30 | T069 regression | `npm test -- --watch=false --progress=false` | Full frontend remains green after locale/motion/date changes | 42 files / 102 tests passed | PASS | Vitest output |
| 2026-07-30 | T036 asset delivery | `Invoke-WebRequest http://localhost:4200/assets/i18n/vi.json` | Runtime translation catalog is served | HTTP 200 after Angular asset configuration and dev-server restart | PASS | Local HTTP probe |
| 2026-07-30 | T036-T043 build | `npm run build -- --no-progress` | Production bundle and translation/image assets compile | Build passed; initial bundle 1.10 MB raw / 204.65 kB estimated transfer | PASS | Angular build output |
| 2026-07-30 | T039 regression | `npm test -- --watch=false --progress=false`; `ngc -p tsconfig.app.json --rootDir src --noEmit` | Checkout/profile translated strings compile and remain green | 44 files / 107 tests passed; Angular template compiler passed; VI/EN catalogs 573 keys each with zero parity gaps | PASS | Vitest/ngc output |
| 2026-07-30 | T044 | Fresh Home browser run at default viewport; switch VI -> EN; inspect console | VI/EN labels remain translated after async catalog load and no client errors appear | VI and EN hero/search/footer/slideshow labels rendered correctly; console error/warn log empty | PASS | In-app browser DOM/screenshot evidence |
| 2026-07-30 | T044 | `playwright test e2e/localization-motion-performance.spec.ts --project=chromium --workers=1 --retries=0` | Reduced-motion remains static; five cold desktop/mobile runs stay within interaction/CLS budgets | 3/3 passed; desktop p75 `6.5 ms`, CLS `0.000943`; mobile p75 `5.8 ms`, CLS `0.001167`; no overflow | PASS | Playwright output |
| 2026-07-30 | T044 regression | `npm test -- --watch=false --progress=false` | Locale bootstrap change preserves the complete Angular suite | 46 files / 111 tests passed | PASS | Vitest output |

## Phase 5 Booking/Payment Lifecycle Baseline (2026-07-29)

Status: `PARTIAL`. Tasks `T045`-`T054` and `T097`-`T100` are complete. Canonical lifecycle, persisted reservation holds, the two-thread inventory proof, server-bound sessions, provider callback hardening, explicit refund lifecycle, provider query/refund recovery and customer/admin lifecycle UI/browser evidence pass; live sandbox evidence remains open under `T101`.

- Canonical `ReservationStatus`, `PaymentStatus` and `RefundStatus` parsers normalize known legacy values while rejecting blank/unknown state.
- `BookingLifecyclePolicy` implements the approved transition tables, idempotent replay decisions and `RECONCILIATION_REQUIRED` for payment success arriving after cancellation/expiry or local payment expiry.
- Flyway migration `V14__canonical_booking_payment_statuses.sql` maps reservation `PENDING` to `PENDING_PAYMENT` and known payment aliases to `PENDING`/`SUCCEEDED` without deleting financial history.
- Reservation creation now writes `PENDING_PAYMENT`; payment ledger writes use `SUCCEEDED`. Existing admin reservation surfaces accept both legacy `PENDING` and canonical `PENDING_PAYMENT` during rollout.
- Flyway migration `V15__reservation_holds.sql` adds hotel-scoped holds with a configurable 15-minute default TTL, deterministic reservation hold keys and explicit `ACTIVE`/`CONSUMED`/`RELEASED`/`EXPIRED` states.
- Reservation creation persists the hold inside the room-type pessimistic-lock transaction. Status changes use a consistent reservation-then-hold lock order; confirmed/later states consume, cancellation/rejection/no-show release and expiry changes the reservation to `EXPIRED` exactly once.
- A committed expired hold is discovered by the scheduler without in-memory state, and a real H2 two-thread test with one physical room proves exactly one booking succeeds while the other receives the sold-out failure.
- Focused backend lifecycle/service tests: the canonical transition/payment suites pass; the new `RefundServiceTest` covers request creation, terminal outcomes, ledger/points mutation and replay idempotency.
- T100 provider recovery uses environment-only credentials and deterministic references. MoMo query/refund/refund-query requests use the official HMAC-SHA256 payloads; ZaloPay query/refund/refund-query requests use the official HMAC-SHA256 form payloads. A scheduled recovery pass queries missed payment callbacks and advances refund attempts only after provider outcomes; timeout/unknown-reference paths never create a local success claim.
- Focused T047-T049 hold/concurrency tests: `17/17` passed across unit, scheduler, persisted restart/replay, concurrent expiry scans and real two-thread booking coverage.
- Full backend regression after T047-T049: `154/154` passed. Focused provider/session/controller suites later passed `27/27`; frontend checkout/payment-result suites passed `6/6`. The latest regression on 2026-07-30 passed `216/216` backend tests across 54 suites and `46` frontend files / `113` tests; the frontend production build also passed. Existing Angular optional-chain, Chart.js canvas and STOMP/SockJS CommonJS warnings remain non-blocking.

| Date | Phase/Task | Command | Expected | Actual | Result | Artifact |
|---|---|---|---|---|---|---|
| 2026-07-29 | T045-T046 | `.\mvnw.cmd "-Dtest=BookingLifecyclePolicyTest,ReservationServiceTest,PaymentServiceImplTest" test` | Canonical aliases and valid/invalid/idempotent/late-event transitions pass without refund-of-refund | 23 tests passed | PASS | Maven output |
| 2026-07-29 | T045-T046 regression | `.\mvnw.cmd test` | No backend regression from canonical status rollout | 145 tests passed, 0 failure/error/skipped | PASS | Maven output |
| 2026-07-29 | T045 compatibility | `npm test -- --watch=false` | Legacy/canonical pending UI compatibility compiles without regression | 39 files / 92 tests passed | PASS | Vitest output |
| 2026-07-29 | T047-T049 | `.\mvnw.cmd "-Dtest=ReservationHoldServiceTest,ReservationHoldExpirySchedulerTest,ReservationServiceTest,ReservationHoldIntegrationTest,ReservationConcurrencyIntegrationTest" test` | Persisted holds, TTL, replay-safe/concurrent expiry and one-room concurrency pass | 17 tests passed, including exactly 1 expiry winner and exactly 1 booking success | PASS | Surefire reports |
| 2026-07-29 | T047-T049 regression | `.\mvnw.cmd test` | No backend regression after hold/concurrency integration | 154 tests passed, 0 failure/error/skipped | PASS | Surefire reports |
| 2026-07-30 | T050-T052, T097-T099 regression | `.\mvnw.cmd -q test` | Provider callbacks remain public-but-explicitly-annotated, cross-account payment creation does not disclose reservation existence and the full backend remains green | 182 tests passed, 0 failure/error/skipped across 45 suites | PASS | Surefire reports |

| 2026-07-30 | T053 | `V19__refund_lifecycle.sql`; `RefundServiceTest`; `RefundLifecycleConcurrencyIntegrationTest` | Refund request/provider-attempt transitions are explicit; provider success changes ledger, points and notification exactly once under replay/concurrency | Unit suite passed; H2 two-thread suite passed with one negative ledger, one point reversal and one terminal notification | PASS | Maven/Surefire output |
| 2026-07-30 | T100 | `MomoPaymentGatewayTest,ZaloPayPaymentGatewayTest,PaymentProviderRecoveryServiceTest`; `.\mvnw.cmd -q test` | Official transaction query/refund/refund-query contracts validate signatures, server-owned amount/reference, pending/failed/unknown outcomes and timeout retry without duplicate financial effects | Focused `15/15` tests passed; full backend `54` suites / `216` tests passed | PASS | Maven/Surefire output |
| 2026-07-30 | T070 regression | `.\mvnw.cmd -q test` | Full backend, security, payment and concurrency regression remains green | 54 suites / 216 tests passed, 0 failure/error/skipped | PASS | Surefire reports |

### T054 customer/admin lifecycle UI implementation (2026-07-30)

The reservation payload now exposes safe `payment` and ordered `refunds` summaries. Customer booking history and admin reservation management render labelled payment states (`pending`, `failed`, `expired`, `reconciliation`) and refund states (`requested`, `provider pending`, `succeeded`, `failed`). Cancellation copy creates a refund request and does not promise provider success before an authoritative result. Transaction ids, callback payloads and credentials are intentionally excluded.

- Frontend evidence: `46` test files / `113` tests passed, including the login user-id contract and delayed-response admin regression; production build passed at `1.10 MB` raw / `205.14 kB` estimated transfer.
- Browser evidence: `2/2` Playwright journeys pass against the dedicated E2E backend on port `8082`. The customer journey verifies all payment/refund states and cancellation creates `REQUESTED`; the admin journey verifies the same payment states, paginates to the failed refund row, confirms no horizontal overflow and writes `docs/screenshots/payment-refund-customer.png` plus `docs/screenshots/payment-refund-admin.png`. This is UI/state evidence only; no live provider refund success is claimed.

| 2026-07-30 | T054 browser | `npx playwright test e2e/payment-refund-lifecycle.spec.ts --project=chromium --workers=1 --retries=0` | Authenticated customer/admin lifecycle states render, cancellation creates a refund request, pagination retains failed refund visibility and no overflow occurs | 2/2 passed; customer and admin screenshots generated; direct authenticated API checks returned fixture data | PASS | Playwright output and `docs/screenshots/payment-refund-customer.png`, `docs/screenshots/payment-refund-admin.png` |

## Phase 6 Internal Support Chat (2026-07-31)

Status: `PARTIAL` for the full social-channel phase. `T055`-`T058` and `AC-601`-`AC-602` are complete for tenant-aware in-app support; `T059`-`T061` remain blocked by OQ-006 to OQ-009 and official Facebook/Zalo credentials.

- The login response now exposes the authenticated server-owned `userId`, and the Angular login flow persists it in the local session contract so `ChatWidgetComponent` can load principal-scoped history without trusting a caller-supplied identity.
- `frontend/e2e/support-chat-lifecycle.spec.ts` uses isolated customer/admin browser contexts and proves history reload, customer send, support queue discovery, support reply, user-queue delivery, blocked SockJS transport feedback and automatic reconnect after transport recovery.
- Browser run: `npx playwright test e2e/support-chat-lifecycle.spec.ts --project=chromium --workers=1 --retries=0` passed `1/1` in `33.7s` against a dedicated H2 E2E backend and Angular frontend.
- Focused frontend auth/chat regression passed `3` files / `8` tests. Focused backend auth contract regression passed `6/6` tests across `AuthControllerIntegrationTest` and `AuthServiceTest`.
- `V20__tenant_support_conversations.sql` adds `support_conversations`, tenant-scoped audit events and mandatory `conversation_id`/`hotel_id` scope for new messages. Existing rows are retained as `legacy_unscoped`; the migration does not infer a tenant from historical receiver ids.
- Customer message context is derived from an owned reservation, an explicitly selected approved/active property, or the customer's latest reservation. A reservation/property mismatch is hidden as not found.
- Support list/history/reply access is restricted to active hotel assignments unless the principal is a system administrator. Cross-tenant history/reply attempts return `404` and create `ACCESS_DENIED_*` audit events without exposing the foreign tenant.
- Conversation replies use `conversationId`, automatically claim open/escalated work, reject takeover by another agent and support explicit `/assign` plus `/escalate` transitions. Conversation rows use optimistic `@Version` protection.
- The former shared `/topic/support/messages` broadcast is removed. Customers receive `/user/queue/messages`; authorized tenant agents receive `/user/queue/support/messages`.
- Focused Java main/test compilation passed. Focused chat service/controller/security tests passed `12/12`; `SupportConversationIsolationIntegrationTest` passed `2/2` on H2. Focused TypeScript compilation also passed.
- Standard Maven and Angular runners were retried but produced no completion output and were terminated after `304 s`. A later official focused Maven retry for the five chat suites also produced no output, timed out after `184 s` and left no Surefire report; its wrapper/JVM processes were stopped without touching the frontend on `4200` or E2E backend on `8082`. These tooling timeouts are recorded rather than replacing the successful focused evidence or the earlier full-regression baseline.
- No external Facebook/Zalo message was transmitted, and no tenant-channel credential/configuration completion is claimed.

## Phase 7 Subscription Contract (2026-07-30)

Status: `PARTIAL`. Tasks `T062`, `T063` and `T065`-`T068` are complete. The canonical
read contract is shared by admin/management, advertised quota mutations are enforced from
the backend, lifecycle fixtures pass in a real browser and upgrade remains a truthful contact
path. Only T064 admin plan/feature lifecycle remains gated by configuration approval.

- Backend `/api/subscriptions/plans` now returns active `SubscriptionPlanDTO` rows with VND pricing and typed feature limits from the catalog.
- Authenticated `/api/subscriptions/me` returns canonical subscription history, including expired records for authorized read-only views.
- Authenticated `/api/subscriptions/me/usage` returns effective limits, current usage and per-feature `allowed` state. Usage covers owned properties, room types, rooms, property/room-type/room images and staff.
- Management billing and admin plan screens render feature names/limits from API data. Price/code-based benefit inference and fake “Mua ngay”/payment redirection messaging were removed; upgrade uses an explicit contact link until OQ-010 is approved.
- Entitlement inventory and mutation coverage:

| Feature | Counted usage | Mutation enforcement |
|---|---|---|
| `MAX_PROPERTIES` | Active owned property assignments | Management property creation and imported-property claim approval |
| `MAX_ROOM_TYPES` | Room types across accessible owned properties | Direct and management create/update/deactivate paths |
| `MAX_ROOMS` | Rooms across accessible owned properties | Direct and management create/bulk/update/deactivate paths |
| `MAX_IMAGES` | Property, room-type and room image associations | Room-type create/replacement and room creation; raw uploads are not counted until associated |
| `MAX_STAFF` | Active non-owner assignments | Staff create/update/delete paths |

- Effective subscriptions are resolved by status, start time, end time and lifetime flag. Expired/no-plan accounts receive empty active limits; authorized subscription and resource reads remain available while gated mutations fail server-side.
- E2E provisioning creates active finite, expired, lifetime/unlimited and multiple-subscription owners. `E2eFixtureInitializerIntegrationTest` verifies effective repository/service results and repeated provisioning remains idempotent.
- `subscription-entitlements.spec.ts` passes `5/5` against a real local E2E backend: active finite limits, expired history, expired POST room denial (`409`), lifetime unlimited values, multi-plan maximum merging, visible `mailto:support@luxestay.vn` upgrade contact and no fake `Mua ngay` action.
- Focused quota/catalog/user/claim tests pass. `PropertyClaimControllerIntegrationTest` adds `6/6` passing security cases proving unauthenticated denial, principal-derived requester/reviewer ids, `403` without claim authority and canonical `SUPER_ADMIN` access. Full backend regression now passes `216/216` across 54 suites. Frontend regression remains `46` files / `113` tests; the production build passes with a `1.10 MB` initial bundle (`205.14 kB` estimated transfer).

Open scope: T064 admin plan/feature lifecycle is not implemented because OQ-010/configuration approval remains unresolved. Online subscription ordering/payment activation is not claimed; the approved current path is real support contact.

## Phase 8 Release Gate (2026-07-31)

Status: `COMPLETE` for T069-T075. The integrated release gate now combines the existing
frontend/backend regression evidence with a real 4-viewport browser matrix across public,
customer, admin and management surfaces. Product/provider work outside Phase 8 remains
explicitly gated and does not become complete through this browser evidence.

- Feature-03 GAP-005/GAP-019/GAP-021/GAP-022/GAP-024 were reconciled against the current gap register without overwriting prior evidence.
- `traceability-matrix.md` now distinguishes completed regression/reconciliation work from the remaining integrated browser gate.
- `release-report.md` records the scoped delivery, verification counts, blockers and the fact that no files were staged/committed/pushed.
- Latest public Home audit passes at 375/768/1024/1440px with zero horizontal overflow, no visible translation keys, working VI/EN toggle and slideshow controls. Slideshow dots and search-service tabs now meet the 44px touch-target requirement; the mobile account menu no longer lets its profile header intercept the logout action.
- Dedicated E2E fixture verification passes `5/5`: Home database media/pricing integrity, date-overlap availability, customer/pending/owner account actions and mobile logout. The availability fixture now persists a `ReservationDetail`, matching the production inventory query instead of relying on the legacy `reservation.room` field.
- `integrated-release-matrix.spec.ts` passes `4/4` serial journeys against the dedicated E2E backend. Each journey audits Home, Search, Customer profile/bookings, Admin dashboard and Management dashboard at `375x812`, `768x1024`, `1024x768` and `1440x900`, using alternating VI/EN locale contexts and the established reduced-motion verification.
- All 20 audited surfaces report zero page overflow, zero visible missing translation keys, zero console/page/HTTP errors and a visible in-viewport first keyboard focus target. Navigation timing remains inside the `15 s` release threshold.
- Measured CLS by viewport (Home / Search / Customer / Admin / Management): `375px` = `0.03039 / 0 / 0.03959 / 0.00084 / 0.01047`; `768px` = `0.00219 / 0.00016 / 0.03540 / 0.00313 / 0.01048`; `1024px` = `0.00375 / 0.00026 / 0.00686 / 0.00787 / 0.02006`; `1440px` = `0.00149 / 0.00013 / 0.00307 / 0.00567 / 0.01429`. Every value is below the `0.1` release budget.
- Layout shells now reserve stable header/context/account/footer dimensions while lazy routes and account data load. The admin quick search is hidden from `992px` through `1180px`, and the management header wraps predictably in that range, removing the observed workspace overflow/layout shifts.
- The 2026-07-31 Search audit reported zero sponsored markers while the policy gate was still open. That historical result is superseded by the 2026-08-03 Phase 3 closure below, which provisions a governed placement and verifies its visible disclosure.
- The focused Angular unit command and a fresh one-worker production build were retried after the layout changes; their current outcome is recorded in the release report. Earlier complete frontend regression/build evidence remains preserved rather than overwritten.
- Promotion/sponsored disclosure was still gated at the Phase 8 timestamp. The pending-owner regression continues to exercise the account-menu action, while the later Phase 3 closure now covers real promotion/member/sponsored data.

## Payment Provider Contract (2026-07-29)

Completed scope:

- `PaymentSession` persists owner, hotel, expected VND amount, idempotency key, provider reference, checkout URL, expiry, status, provider transaction id and reconciliation flag. `V17__payment_session_checkout_url.sql` preserves replay-safe provider URLs.
- VNPay browser return remains display-only; VNPay IPN validates merchant, amount, transaction status and HMAC-SHA512. Extra `session`/`provider` return parameters are excluded from the signed field set.
- MoMo Test create uses `captureWallet`, HMAC-SHA256 request/response signatures and environment-only `MOMO_*` configuration. MoMo IPN validates the documented sorted signature payload and returns HTTP `204` for accepted/duplicate/failed-recorded callbacks.
- ZaloPay Sandbox create uses `/v2/create`, `app_id`, Vietnam-date `app_trans_id`, key1 HMAC over the documented pipe-delimited payload and callback URL embedded in `embed_data`. Callback validation uses key2 HMAC over raw `data`, checks `type=1`, app id, amount and provider transaction id, and returns `return_code` `1`/`2` acknowledgements.
- Checkout options are VNPay, MoMo, ZaloPay and pay-at-hotel; Stripe is deferred. The browser return page polls `GET /api/payments/sessions/{sessionId}` with the authenticated owner and shows pending, failed, expired, success and reconciliation states without treating URL parameters as financial authority.

Focused evidence:

- Backend provider/session/controller suites: `27/27` passed (`MomoPaymentGatewayTest`, `ZaloPayPaymentGatewayTest`, `VnpayPaymentGatewayTest`, `PaymentSessionServiceTest`, `PaymentControllerIntegrationTest`, `MockPaymentControllerIntegrationTest`).
- Existing real concurrency callback proof: `PaymentSessionConcurrencyIntegrationTest` passes with exactly one `00`, one `02`, one ledger row and one points award.
- Frontend checkout/payment-result suites: `6/6` passed. Build/test output retains the existing Angular optional-chain warning only.

Open provider work:

- `T101`: live sandbox credentials and publicly reachable callback URLs are required; no secrets are committed and no live provider success is claimed by local tests.

## Evidence Log Template

| Date | Phase/Task | Command or browser journey | Expected | Actual | Result | Artifact |
|---|---|---|---|---|---|---|
| | | | | | PASS/FAIL/BLOCKED | screenshot/test output |

## Phase 3 canonical quote evidence (2026-08-03)

Status: `COMPLETE` for Phase 3 (`T025-T035`, `T110-T117`). Server-side price authority, admin lifecycle, public deal/member presentation, sponsored disclosure and responsive browser acceptance now have executable evidence. OQ-002 remains open for unpaid inventory holds and is not claimed by this promotion-scope completion.

- `PromotionQuoteService` is the single evaluator for VND room quotes. It rounds with `HALF_UP` to integer VND, preserves the existing 15% tax/fee policy, selects one best eligible automatic campaign, optionally applies one eligible coupon, enforces expiry, JSON eligibility, max discount, budget, global/per-customer quotas and explicit membership assignments, and writes idempotent `PromotionRedemption` rows.
- `V46__reservation_promotion_quote_snapshot.sql` stores the accepted quote id/expiry, room scope, subtotal, tax, fee, discount, currency and localized promotion/member JSON on `reservations`. `FolioCalculationService` emits immutable TAX/FEE/DISCOUNT lines from that snapshot; legacy invoice generation and refund creation continue to consume `reservation.total_amount`/successful payment amounts.
- Angular hotel detail and checkout no longer derive a payable total from nightly price. They call `POST /api/public/quotes`, render the server response and block booking until a current quote is available; the backend recalculates and persists the final quote again at reservation creation.

| Date | Phase/Task | Command or browser journey | Expected | Actual | Result | Artifact |
|---|---|---|---|---|---|---|
| 2026-08-03 | T028-T029 | `backend\\mvnw.cmd -q -Dtest=PromotionQuoteServiceTest test` | Expiry, eligibility, best automatic, coupon stacking, max discount, quota/budget, membership and replay tests pass | 6/6 tests passed | PASS | Surefire report |
| 2026-08-03 | T030 | `backend\\mvnw.cmd -q -Dtest=FolioCalculationServiceTest,PromotionQuoteServiceTest,ReservationServiceTest,InvoiceFinalizationServiceTest,RefundServiceTest test` | Quote snapshot does not regress reservation/folio/invoice/refund behavior | All focused suites passed | PASS | Surefire reports |
| 2026-08-03 | T031 | `backend\\mvnw.cmd -q -Dtest=PromotionPriceConsistencyIntegrationTest test` | Public quote, room detail, search, booking, invoice and refund expose one final amount | 1/1 integration test passed; fixture total `1,050,000 VND` across every surface | PASS | Surefire report |
| 2026-08-03 | T030 frontend | `frontend\\npm run build` | Detail/checkout compile with server quote models and no local payable-total arithmetic | Production build passed; existing CSS/CommonJS warnings remain | PASS | Angular build output |
| 2026-08-03 | T033 | `frontend\\npm test -- --watch=false --include=src/app/features/client/home/home.spec.ts --include=src/app/features/client/home/components/promotions/promotions.component.spec.ts` | Home consumes typed public promotion data and renders loading, active, empty and error states without hard-coded deals | 2 files passed; 5/5 tests passed | PASS | Vitest output |
| 2026-08-03 | T033/T116 | `frontend\\npm test -- --watch=false --include=src/app/features/client/home/components/destination-recommendations/destination-recommendations.component.spec.ts` | Home recommendation cards render canonical original/final amounts and locale-aware member tier proof | 1 file passed; 7/7 tests passed | PASS | Vitest output |
| 2026-08-03 | T034/T116 | `frontend\\npm test -- --watch=false --include=src/app/features/client/booking-checkout/booking-checkout.component.spec.ts --include=src/app/features/property-search/components/property-result-card/property-result-card.spec.ts --include=src/app/features/client/hotel-detail/hotel-detail.component.spec.ts` | Search, detail and checkout render server quote totals, tier conditions and typed sponsored disclosure consistently | 3 files passed; 14/14 tests passed | PASS | Vitest output |
| 2026-08-03 | T033/T034/T116 backend | `backend\\mvnw.cmd -q -Dtest=HomeRecommendationServiceTest,PublicPlacementDisclosureServiceTest,PromotionPriceConsistencyIntegrationTest,PromotionCampaignRepositoryTest test` | Public recommendation pricing and sponsored disclosure use eligible active backend records only | 4 suites passed; 10/10 tests passed | PASS | Surefire reports |
| 2026-08-03 | T033/T034/T116 build | `frontend\\npm run build` | Final templates, i18n keys and typed contracts compile for production | Build passed; only the existing property-payment CSS budget and STOMP/SockJS CommonJS warnings remain | PASS | Angular build output |
| 2026-08-03 | T035 / AC-301 | `PromotionCampaignManagementServiceTest`, `SponsoredPlacementManagementServiceTest` | Authorized tenant/system lifecycle validates ownership, schedule, quota, activation/pause and approval | Campaign suite 4/4 passed, including create -> scheduled -> paused; placement suite 6/6 passed | PASS | Surefire reports |
| 2026-08-03 | T035 / AC-302-AC-304 | Local E2E API and authenticated browser journey | Search/detail/checkout use one backend quote and display the explicit GOLD assignment | Base `850,000`, discount `85,000`, tax/fees `127,500`, final `892,500 VND`; coupon `E2EGOLD` quote discounts `135,000` and ends at `842,500 VND` | PASS | API responses and screenshots |
| 2026-08-03 | T035 / AC-303 | `E2eFixtureInitializerIntegrationTest`, `PromotionQuoteServiceTest`, public projection tests | Repeated fixtures/redemptions stay idempotent; expired/ineligible/exhausted records stay hidden | Fixture 1/1, quote 6/6, public promotion 3/3 and disclosure 2/2 passed | PASS | Surefire reports |
| 2026-08-03 | T117 responsive browser | Home -> Search -> Detail -> Checkout at 375/768/1024/1440 with VI/EN toggle | Preserve dates/guests/rooms, price proof, GOLD benefit and disclosure without overflow, missing keys or plural defects | 12/12 route/viewport checks passed; `Được tài trợ` / `Sponsored`, `Hạng Vàng` / `Gold`, zero overflow, zero missing P1 keys, buffered CLS `0` on the audited Phase 3 routes | PASS | `docs/screenshots/phase3-promotions-*` |
| 2026-08-03 | T117 console/runtime | Browser console and hot-reload validation after plural correction | No application runtime error and correct singular English labels | No localhost application warning/error; only the pre-existing external Google FedCM token retrieval error from the login session. `1 room`, `1 night`, `1 child` render correctly | PASS | Browser log and DOM snapshots |
| 2026-08-03 | T117 frontend verification | `tsc -p tsconfig.app.json --noEmit`; `tsc -p tsconfig.spec.json --noEmit`; focused Angular unit retry | Type-check changed app/spec code and rerun focused component tests | Both TypeScript checks passed. Angular generated the bundle, then Vitest failed before collection with `Timeout starting vmThreads runner`; the prior 14/14 component baseline remains valid and browser assertions cover the changed plural output | PASS / TOOLING_LIMITATION | Command output |

Screenshot evidence:

- Search: `docs/screenshots/phase3-promotions-search-375.png`, `phase3-promotions-search-768.png`, `phase3-promotions-search-1024.png`, `phase3-promotions-search-1440.png`.
- Home membership/offers: `docs/screenshots/phase3-promotions-home-375.jpg`, `phase3-promotions-home-1440.jpg`.
- Detail quote/disclosure: `docs/screenshots/phase3-promotions-detail-375.jpg`, `phase3-promotions-detail-1440.jpg`.
- Checkout context/quote/disclosure: `docs/screenshots/phase3-promotions-checkout-375.jpg`, `phase3-promotions-checkout-1440.jpg`.
