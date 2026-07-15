# Search Result and Booking Report - 2026-07-15

## Scope completed

- Search Result compact bar reuses `HomeSearchStateService`, location autocomplete,
  date range and guest/room selector from Home.
- Query parameters preserve province, ward, dates, adults, children, room count,
  filters, sorting and pagination without a full page reload.
- Implemented server-backed price, property type, star and minimum review score
  filters. Unsupported amenity/policy relations are intentionally not shown.
- Active filter chips, clear-all, retry/empty/loading states and mobile filter/search
  sheets are available.
- Result cards use local property media with typed fallback, real rating state,
  availability and pricing. VND uses `Intl.NumberFormat('vi-VN')`.
- Pricing now separates nightly price, room quantity, nights, subtotal, 15% tax,
  fee and total. Search and reservation use the same calculation service.
- Property detail API uses `PublicHotelDetailDTO`; payload for property 16 dropped
  from about 1.5 MB recursive entity JSON to 736 bytes plus explicit gallery URLs.
- Room selection displays RoomType images, beds, capacity, availability and a
  quantity bounded by requested room count and current stock.
- Checkout receives quantity/adults/children/special request and shows a summary.
  Backend locks RoomType, recalculates the total and rejects stock conflicts with 409.
- Global 401/403 handling no longer sends a Public page to `/403`; logout state,
  local storage and route guards are synchronized.

## Real verification

- Search API: 180 eligible properties for the unfiltered demo query.
- Pricing sample on the final packaged JAR: 2 rooms x 2 nights at 400,000 VND;
  subtotal 1,600,000 VND, tax 240,000 VND and total 1,840,000 VND.
- Public detail payload: 736 bytes, zero nested `hotel` back references, 3 gallery images.
- Reservation #8 created for RoomType 49, quantity 1, total 1,840,000 VND.
- Availability for RoomType 49 changed from 3 to 2 after reservation creation.
- Overbooking request for quantity 3 returned HTTP 409; SQL count of failed rows is 0.
- Frontend build: PASS (bundle/CommonJS budget warnings remain).
- Frontend unit tests: 20/20 PASS.
- Backend tests: 35/35 PASS.
- Backend package: PASS; the temporary verification process was stopped afterward.
- Playwright Search Result tests: 2/2 PASS in 11 seconds.
- Desktop Search Result and Room Selection have no horizontal overflow.

Screenshots:

- `docs/screenshots/search-result-after.png`
- `docs/screenshots/room-selection-after.png`

## Remaining work

- Multi-RoomType selections in one reservation are not enabled; the current safe
  contract supports one RoomType with quantity greater than one.
- Amenity and booking-policy filters remain hidden until production relations and
  indexed queries exist.
- Checkout service selection is not exposed to customers yet; operational service
  usage after check-in remains available through the existing backend workflow.
- Payment simulator paths were preserved but were not charged during this phase;
  the real E2E used `PAY_AT_HOTEL`.
- Existing bundle budget and CommonJS warnings are outside this phase.
