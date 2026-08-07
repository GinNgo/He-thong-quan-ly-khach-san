# Research: Current-State Audit and Decisions

## Method

The audit compared existing Angular routes/components, Spring controllers/services/entities, migrations, focused tests and Feature-03 evidence. Status meanings:

- **Implemented**: source and meaningful automated/browser evidence exist.
- **Partial**: useful code exists but an advertised lifecycle, policy or real-browser branch is incomplete.
- **Scaffold only**: types/UI hooks exist but the backend/data path is empty.
- **Missing**: no source contract found.
- **Decision gate**: implementation would change security, financial or external-provider policy.

## Findings

| Area | Status | Source evidence | Decision |
|---|---|---|---|
| Header partner button | Complete for the requested removal | Desktop/mobile standalone CTA removed; account menu and Home partner band remain; header regression spec covers both placements | Keep owner access in the account menu/Home partner band |
| Search responsive UI | Implemented for Phase 1 | Hero/sticky search use one/two/four-column layouts by breakpoint; browser matrix has no page overflow at 375/768/1024/1440 | Continue with persisted landmark resolution in Phase 2 |
| Date selection | Partial: overnight reliable, day-use blocked | Labelled buttons, local-date normalization, invalid-range feedback, recent-search restoration and responsive month count are covered by focused tests; real backend still requires both check-in and check-out, so the day-use selector remains disabled | Do not enable day-use until an approved backend contract supports a single service date |
| Landmarks | Search contract and current-administration backend/data migration implemented | `Location` stores discovery/provenance metadata; 63 legacy provinces remain packaged for hotel/ward compatibility. Public APIs expose 34 current units, the alias catalog covers 63/63 legacy codes exactly once, and the generated catalog contains 122 rows with at least three active coordinate-valid landmarks in every current unit | Keep independent `VN34-*` identities and runtime alias expansion; complete browser/performance evidence before closing Phase 2 end to end |
| Promotions | Missing backend | Unused Home `PromotionsComponent`; no campaign/coupon entity/API | Add persisted campaign/application domain and a canonical quote service before rendering deals |
| VIP/member deals | Missing product model | Only loyalty points and a translation label exist | Define membership tier separately from room type code `VIP`; do not infer eligibility from points without an approved rule |
| Advertising | Missing | No placement/campaign source found | Add labelled sponsored placement with schedule/quota/relevance and audit; never mix silently into organic ranking |
| VI/EN | Partial assets | Translate dependency and `vi.json`/`en.json` exist; app config has fixed Vietnamese PrimeNG translation and public templates are hard-coded | Add locale store/provider and migrate P1 public scope incrementally; keep VND settlement |
| Home motion | Partial | Existing hover/reveal/reduced-motion CSS, no slideshow | Add one editorial slideshow only; stable aspect ratio, manual controls, pause and reduced-motion static fallback |
| Reservation concurrency | Implemented and directly proven | `createReservation()` keeps the room-type pessimistic lock through inventory validation, reservation/detail creation and persisted hold creation; a real two-thread H2 test with one room yields one success and one sold-out failure | Preserve the lock boundary and rerun the concurrency test after any inventory-query change |
| Reservation hold/status | Canonical hold baseline complete | Creation writes `PENDING_PAYMENT` plus a hotel-scoped 15-minute hold. Scheduler restart/replay tests prove expired inventory releases once; confirmed/later states consume and cancelled/rejected/no-show states release the hold | Keep callback mutation behind `OQ-001`; late provider success must use the reconciliation policy rather than revive an expired reservation |
| Payment idempotency | Implemented for charge session/callback/query scope | `PaymentSession` stores owner/idempotency/provider references; VNPay concurrent callback, MoMo/ZaloPay signature/replay/query recovery tests pass; provider references and amounts remain server-bound | Keep live credentials/callback evidence separate from local adapter proof |
| Pending/failed payment | Implemented for local recovery scope | Server session status is persisted as `PENDING`, `FAILED`, `EXPIRED` or `SUCCEEDED`; browser return polls an authenticated owner endpoint and scheduled MoMo/ZaloPay query recovery processes missed callbacks without trusting browser return data | Run T101 live sandbox journey before production claim |
| Refund | Explicit request/attempt lifecycle with provider adapter recovery | Cancellation creates idempotent `RefundRequest`/provider-attempt rows; official MoMo/ZaloPay refund/refund-query adapters preserve requested/pending/succeeded/failed semantics, timeout retries remain pending and provider success alone creates the negative ledger/points reversal/terminal notification | Run T101 live provider confirmation; never claim external refund without authoritative confirmation |
| Demo/provider callback | Implemented for approved charge and local recovery scope | Signed expiring simulator token plus server-owned VNPay/MoMo/ZaloPay sessions; provider IPN/callback is authoritative, missed MoMo/ZaloPay callbacks are query-recovered and late success is reconciled | Keep live credentials out of source; complete T101 before production claim |
| Internal chat | Implemented for tenant-aware in-app scope | Principal-derived sender, property/reservation context, `SupportConversation`, tenant-scoped messages/audit events, private user queues, assignment/escalation and authenticated plus cross-tenant evidence pass | Preserve this isolation contract while keeping Facebook/Zalo configuration and transmission blocked until OQ-006 to OQ-009 and credentials are resolved |
| Facebook/Zalo | Missing | No source/config found | Official OAuth/webhook adapters only; encrypted secret reference, signature validation, deduplication, consent and provider-specific status |
| Subscription plans | Partial/inconsistent | Backend exposes plans/me/features; plan feature service works; UI has mismatched DTOs, hard-coded benefits and no real purchase | Create canonical DTO/catalog, admin lifecycle, full gate inventory and truthful upgrade path |

## UI Direction

The UI research recommends a search-led marketplace with premium but restrained surfaces. The project should keep the existing teal/navy/gold LuxeStay language instead of adopting the generated orange/blue palette literally. Apply the useful principles:

- Search remains the primary CTA; promotions and partner content cannot overpower it.
- Use layered photography, subtle atmospheric gradients and editorial cards rather than flat white sections.
- Use motion for one meaningful Home narrative, not generic animation on every control.
- Maintain 4.5:1 text contrast, visible focus, 44px targets and `prefers-reduced-motion`.
- Avoid heavy blur/liquid-glass on dense search controls because contrast and mobile performance are more important.

## Nationwide Landmark Source Decision

| Option | Assessment | Decision |
|---|---|---|
| Public Nominatim bulk geocoding | Easy for individual addresses but unsuitable as a repeatable nationwide catalog source; public service policy and rate limits make systematic refresh fragile | Do not use for bulk generation |
| Overpass API nationwide query | Useful for exploration, but large queries are rate-limited and not reproducible enough for release artifacts | Optional diagnostic only |
| Geofabrik Vietnam OSM PBF | Versioned offline extract, reproducible and suitable for coordinate matching; requires ODbL attribution and local parsing | Preferred geographic coordinate source |
| GeoNames Vietnam dump | Small, versioned CC BY dataset with stable ids; good fallback for geographic features but tourist-attraction coverage is uneven | Secondary/fallback source |
| MIT Vietnam travel recommendation dataset | 315 curated attractions, five for each legacy province, with Vietnamese descriptions/categories but no coordinates | Preferred initial editorial catalog input after quality review |

The initial pipeline combines the 315-row MIT source with offline OSM/GeoNames coordinate matching, records source provenance and quarantines low-confidence matches. The application currently stores hotels and wards against the pre-consolidation 63 province rows. The `provinces.open-api.vn` v2 endpoint now exposes the current 34-unit view and reuses some numeric codes for different province meanings, so the importer must not overwrite legacy rows by code. The selected model creates canonical `VN34-*` province rows plus explicit legacy aliases; current province search expands to mapped legacy database ids until hotel/ward data is migrated safely.

Current-administration source decision:

- Province list: `https://provinces.open-api.vn/api/v2/p/`, fetched 2026-07-29, 34 rows, SHA-256 `ad03b2f3a40bd652d7f2dd63564cc49817f943380560ede8164239ec3c32c0f0`.
- Ward hierarchy reference: `https://provinces.open-api.vn/api/v2/?depth=2`, fetched 2026-07-29, 34 provinces and 3,321 wards, SHA-256 `e997eb9dcbbbafbbae00496965572f8ccd6e192cbb1024e8fe6b5513c162dc9b`.
- Legal baseline: Resolution `202/2025/QH15` on provincial administrative reorganization and Decision `19/2025/QD-TTg` on administrative unit codes.
- Safety decision: package the current province catalog and alias membership, but do not destructively replace legacy ward rows or hotel foreign keys in this slice.

Source obligations:

- Preserve the MIT source license notice and dataset URL in catalog documentation.
- Attribute OpenStreetMap contributors and comply with ODbL when OSM-derived coordinates are distributed or served.
- Attribute GeoNames under CC BY when GeoNames-derived records are published.
- Record download/version dates and checksums so the artifact can be reproduced and audited.

## Recommended Decisions Before Implementation

1. **Payment demo callback**: signed, expiring, server-issued demo transaction (recommended) vs authenticated reservation-owner confirmation.
2. **Pending hold TTL**: recommend 15 minutes, aligned with current VNPay URL expiry, with configurable environment value.
3. **Promotion stacking**: recommend one automatic campaign plus at most one coupon; choose the best eligible automatic campaign unless stacking is explicitly enabled.
4. **VIP definition**: recommend an explicit `MembershipTier`/benefit model; loyalty points remain a balance, not a tier by themselves.
5. **Sponsored ranking**: recommend fixed eligible slots and relevance threshold; ads never bypass availability or property approval.
6. **Social secret custody**: recommend encrypted secret reference/vault and official OAuth; never store raw tokens in frontend or logs.
7. **Subscription purchase**: keep contact-only until a complete order/payment/activation contract is approved.

## Risks

- Existing status strings and UI DTO mismatches can create silent regressions during migration.
- Promotion pricing becomes a financial source of truth and must not be duplicated between SQL, frontend and checkout.
- Provider sandbox success does not guarantee production Facebook/Zalo approval or permissions.
- Landmark radius queries need indexes/geospatial strategy before nationwide scale.
- Administrative consolidation can silently attach hotels/landmarks to the wrong province if numeric legacy codes are repurposed; use aliases and migrations instead of in-place renaming.
- Automated name-to-coordinate matching can produce false positives; low-confidence or cross-province candidates must stay quarantined until reviewed.
- A carousel can hurt accessibility/performance if autoplay, focus, image dimensions and reduced motion are not controlled.
