# Business Rules

## Search and Date

- **BR-001**: Overnight checkout must be strictly after check-in; day-use uses one service date and no checkout requirement.
- **BR-002**: Dates are business-local dates (`yyyy-MM-dd`), not UTC instants; serialization must not shift a selected day by timezone.
- **BR-003**: The same search state instance drives hero, sticky search and result route parameters.
- **BR-004**: Removing the header partner CTA must not remove owner access from authenticated account navigation or the Home partner section.

## Landmark Discovery

- **BR-005**: A landmark is searchable only when active, has a province and has valid coordinates.
- **BR-006**: Duplicate names are disambiguated by province/ward and category.
- **BR-007**: Landmark search uses a configurable default radius; the user may expand it but cannot bypass property approval/operation/availability filters.
- **BR-008**: Distance ranking is secondary to eligibility and availability; an unavailable property cannot be promoted as bookable because it is nearby.

## Promotions, VIP and Advertising

- **BR-009**: Campaign effective time is evaluated in the configured business timezone and end time is exclusive.
- **BR-010**: The quote evaluator selects exactly one best eligible automatic campaign, then may apply one eligible coupon when the campaign and coupon policies allow it. Automatic campaigns never stack with one another in the first release, and a coupon cannot be applied twice to the same quote.
- **BR-011**: A discount cannot reduce the payable subtotal below zero and must obey budget, redemption and per-customer limits.
- **BR-012**: The server quote is authoritative; frontend badges cannot calculate or invent the discount.
- **BR-013**: Membership tier eligibility is explicit and independent of room type codes such as `VIP`. A tier is created, changed or revoked only by the managed membership policy and its authorized administration workflow; points may be evaluated by that policy but never create a tier implicitly.
- **BR-014**: Sponsored placements require active/approved property, relevant destination/date context, remaining budget/quota and a visible “Tài trợ/Sponsored” label.
- **BR-015**: Sponsored ranking never bypasses inventory, property approval, safety or tenant status rules.

### Approved promotion, membership and placement policy (T025/T110)

- **Promotion authority**: The backend quote is the only source of payable totals. Automatic campaigns are evaluated by eligibility, priority and deterministic tie-breakers; the winning campaign is the one with the greatest valid customer discount, then highest priority, then stable id. One coupon may be added only when its own eligibility and stacking policy permit it. Search, detail, checkout, invoice and refund flows consume the same quote result.
- **Money and limits**: All amounts are VND integer amounts rounded once by the server according to the campaign currency policy. The final discount is capped by the campaign `maxDiscount`, remaining budget, redemption quota and per-customer quota, and is clamped so payable subtotal never becomes negative. Redemption uses an idempotency key tied to the reservation/quote financial event.
- **Membership authority**: `MembershipTier` and `CustomerMembership` are managed policy records. A customer can display a tier only when an active assignment exists for the quote time. Loyalty points are explanatory/account data unless an approved policy explicitly uses them in an assignment rule; a room type or property badge named `VIP` is never sufficient.
- **Placement authority**: `EDITORIAL` placements are configured by a platform administrator. `SPONSORED` placements are proposed by a tenant owner or authorized tenant manager for that tenant's property and require platform-admin approval before publication; a platform administrator may pause or revoke either kind. Client-supplied hotel ownership is never trusted.
- **Target allowlist**: First release targets are server-generated internal routes only: `/hotel/:id` for an approved target property or `/search` with validated allowlisted query keys (`provinceId`, `landmarkId`, `radiusKm`, `sortBy`, `stayType`, `checkInDate`, `checkOutDate`, `adultCount`, `childCount`, `roomCount`). Arbitrary external URLs, javascript/data URLs and unvalidated query parameters are rejected and are never returned by the public API.
- **Schedule and ranking**: Public projection requires active status, approval, current business-time schedule, remaining impression/click/budget quota, an eligible target property and relevant destination context. Sponsored cards occupy fixed Home/search slots and are ranked separately from organic recommendations; they cannot change organic ranking or bypass availability/approval rules.
- **Disclosure**: The public API returns a typed placement kind and localized disclosure. `SPONSORED` must render `Được tài trợ` in Vietnamese and `Sponsored` in English, with the same meaning in accessible text; `EDITORIAL` renders `Biên tập` / `Editorial`. Missing disclosure data is a validation error, not a reason to silently publish.

## Reservation Inventory

- **BR-016**: A pending booking consumes inventory until paid, cancelled or expired.
- **BR-017**: Recommended hold TTL is 15 minutes and must be configurable; expiration releases inventory exactly once.
- **BR-018**: Reservation creation for the same room type is serialized or otherwise protected so committed quantity never exceeds physical bookable rooms.
- **BR-019**: The current advertised booking contract supports one room type plus quantity; mixed room types require a separate specification.
- **BR-020**: Released statuses do not consume inventory; active/pending/confirmed statuses do.

## Payment and Refund

- **BR-021**: A provider transaction/reference belongs to exactly one reservation/order and is globally unique where the provider contract requires it.
- **BR-022**: Callback success requires valid signature/session, expected reservation/order, amount, method, expiry and provider success code.
- **BR-023**: Replayed success produces the original result without duplicate payment, points, confirmation or notifications.
- **BR-024**: A callback arriving after cancellation/expiry must not silently resurrect the booking; it enters an explicit reconciliation path.
- **BR-025**: An internal negative ledger entry alone is not evidence that an external gateway refund succeeded.
- **BR-026**: Refund transitions are `REQUESTED -> PENDING_PROVIDER -> SUCCEEDED` or `FAILED`; repeated requests are idempotent.
- **BR-027**: Points are awarded/reversed only after authoritative charge/refund success and exactly once.
- **BR-028**: The non-production simulator uses exactly one approved security contract; caller-provided status/ownership is insufficient.

### Canonical Transition Tables

| Aggregate | From | Allowed next states |
|---|---|---|
| Reservation | `PENDING_PAYMENT` | `CONFIRMED`, `CANCELLED`, `EXPIRED`, `REJECTED` |
| Reservation | `CONFIRMED` | `CHECKED_IN`, `CANCELLED`, `NO_SHOW` |
| Reservation | `CHECKED_IN` | `CHECKED_OUT` |
| Reservation | `CHECKED_OUT` | `COMPLETED` |
| Payment transaction | `CREATED` | `PENDING`, `SUCCEEDED`, `FAILED`, `EXPIRED` |
| Payment transaction | `PENDING` | `SUCCEEDED`, `FAILED`, `EXPIRED` |
| Refund request | `REQUESTED` | `PENDING_PROVIDER`, `SUCCEEDED`, `FAILED` |
| Refund request | `PENDING_PROVIDER` | `SUCCEEDED`, `FAILED` |

Terminal states reject backward transitions. Replayed provider events return the existing result. A late successful payment for an expired/cancelled reservation enters reconciliation and does not transition the reservation directly.

## Localization and Motion

- **BR-029**: Locale preference is `vi` or `en`, persisted for guests locally and optionally synchronized to an authenticated profile.
- **BR-030**: Currency remains VND; locale changes labels and formatting, not settlement currency.
- **BR-031**: Missing English content falls back to Vietnamese only with a detectable missing-key signal in non-production.
- **BR-032**: Slideshow autoplay pauses on focus, hover, manual pause or hidden document and is disabled for reduced motion.
- **BR-033**: Slides have stable dimensions, meaningful alt text when informative and no duplicated announcement spam for screen readers.

## Support Channels

- **BR-034**: Customer identity comes from the authenticated principal; tenant/property context comes from an authorized booking/property association or explicit public routing rule.
- **BR-035**: Support agents may view/reply only to assigned/authorized tenants and queues.
- **BR-036**: Social integration is opt-in per tenant and channel; channel disabled/expired state cannot appear as operational.
- **BR-037**: Provider secrets are encrypted or stored as external secret references; they are never returned to the frontend.
- **BR-038**: Webhook signatures, timestamps and provider event IDs are verified; duplicate events do not duplicate messages.
- **BR-039**: Consent, data retention, deletion and audit rules are documented before production activation.

## Subscriptions

- **BR-040**: Only active plans are offered; existing inactive plans remain readable for historical subscriptions.
- **BR-041**: Effective subscriptions respect start/end time and lifetime flag; multiple subscriptions merge by highest limit, with `-1` meaning unlimited.
- **BR-042**: A feature limit of `0`, missing or less than `-1` disables the feature.
- **BR-043**: Expiry blocks entitled mutations but preserves authorized historical read access.
- **BR-044**: UI displays feature names/limits from the canonical catalog and does not infer benefits from plan price/code.
- **BR-045**: Purchase/upgrade cannot report success until order, payment, activation and idempotency are implemented and verified.

## Nationwide Landmark Catalog

- **BR-046**: Approved catalog sources are version-pinned and recorded with license/attribution; source rows without a traceable origin are not published.
- **BR-047**: The first nationwide release preserves the application's 63 legacy province codes. A later 34-unit administrative migration requires an explicit alias table and must not repurpose an existing source code onto a different province silently.
- **BR-048**: Landmark natural key is `sourceProvider + sourceObjectType + sourceObjectId`; display name, province and coordinates are mutable attributes, not identity.
- **BR-049**: Manual name/category/radius/popularity overrides win over refreshed source values and are recorded separately from source-owned fields.
- **BR-050**: Exact source-key duplicates are rejected. Near duplicates within the configured distance and normalized-name threshold are quarantined or linked for review, not merged silently.
- **BR-051**: Active publication requires valid Vietnam coordinates, a resolved supported province, a non-empty Vietnamese display name and an accepted quality status.
- **BR-052**: A source absence in one run only updates `lastSeenAt`/quality status. Referenced rows are never hard-deleted by catalog refresh; deactivation requires the configured repeated-absence threshold or manual approval.
- **BR-053**: Generated files are deterministically sorted and UTF-8 encoded so identical source versions produce byte-stable artifacts.
