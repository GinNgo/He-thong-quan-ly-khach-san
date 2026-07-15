# Public/Customer quality report - 2026-07-15

## Scope and causes

- Property and room seeders reused three Admin screenshot assets and three fixed
  room prices. Ten legacy demo properties were also missing `is_demo=true`, so
  the first migration correctly skipped them.
- Cached `average_rating` and `review_count` were populated without a reviews
  table. Public UI therefore presented demo values as customer ratings.
- Home HTTP callbacks updated plain component fields without forcing a view
  refresh in the application's current change-detection setup. Responses were
  successful, but cards appeared only after a later click.
- Header context came only from local storage, used an external avatar service,
  and opened a hover-only menu. Partner state and assigned properties were not
  considered.

## Database work

- Backup tables created before migration:
  `hotels_backup_20260715_public_quality` (181 rows),
  `property_images_backup_20260715_public_quality` (510),
  `room_types_backup_20260715_public_quality` (678), and
  `room_type_images_backup_20260715_public_quality` (1290).
- Flyway `V7` repaired media, ratings, and deterministic prices for records
  already marked demo.
- Flyway `V8` classified only records matching both `code LIKE 'DEMO-%'` and
  `email LIKE '%@demo.local'`, then added their media and corrected demo fields.
- Schema version: 8. No real/user-created property was deleted or overwritten.

## Before and after

| Metric | Before | After |
|---|---:|---:|
| Demo properties correctly classified | 170 | 180 |
| Distinct demo main-image URLs | 3 | 12 |
| Distinct demo gallery URLs | 3 | 12 |
| Distinct demo room-type image URLs | 3 | 7 |
| Distinct room-type prices | 7 | 29 |
| Demo properties carrying unverified ratings | 170 | 0 |
| Demo properties using `/assets/demo/` screenshots | 10 | 0 |
| Popular cards / distinct returned images | 8 / 3-5 | 8 / 8 |

The latest public property API returned eight cards with seven distinct images,
six distinct nightly prices, and truthful availability. An overlapping pending
reservation reduced availability for property 13 in the requested date range.

## API and frontend

- Property search DTO now exposes main/thumbnail/gallery media, image metadata,
  review state, availability, and nightly/subtotal pricing from the lowest
  available active room type.
- Destination and property fallback images are selected per type and per image
  element. No component-level fallback variable mutates an entire list.
- `/api/users/me` includes role, assignment, subscription, partner status, unread
  messages, and pending bookings.
- `/api/invoices/my` is scoped to the authenticated reservation owner.
- Partner registration protects existing emails and supports `NONE`, `PENDING`,
  and `APPROVED` states.
- Public profile menu supports Customer, pending partner, Property Owner, Admin,
  mobile drawer, Escape, click outside, invoice/settings routes, and logout.
- Customer profile no longer contains fake VIP points or vouchers. Profile and
  avatar updates call the real API and immediately refresh the header session.

## Verification

- Flyway: V7 and V8 applied successfully on SQL Server.
- Backend: 34 tests passed, 0 failed.
- Frontend unit: 18 tests passed, 0 failed.
- Angular production build: passed. Existing bundle/CommonJS warnings remain.
- Home Search Playwright: 10 tests passed.
- Public/Customer Playwright: 5 tests passed.
- Browser checks: no broken Home images, no demo screenshots, no hard-coded 9.4,
  desktop/mobile account menu stayed inside the viewport.

Screenshots:

- `docs/screenshots/public-home-quality-after.png`
- `docs/screenshots/public-profile-menu-after.png`
- `docs/screenshots/public-profile-menu-mobile-after.png`

## Not completed in this phase

- Favorites and customer reviews do not yet have complete server-side domain
  modules, so their menu entries remain hidden instead of pointing to empty pages.
- Chat has an existing widget/admin dashboard but no sufficiently isolated
  customer `/messages` page, so it remains hidden from this menu.
- English is not exposed as a working menu choice yet; only the supported
  Vietnamese/VND state is shown. A complete ngx-translate catalog is still needed.
- A real reviews table and approved-review workflow are still required before
  customer rating averages can be displayed.
