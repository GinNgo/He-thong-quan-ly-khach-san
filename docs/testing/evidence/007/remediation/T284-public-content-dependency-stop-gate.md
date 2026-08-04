# T284 Public Property Content Dependency Stop Gate

Task: `T284`
Capability: `PUB-017`
Status: `BLOCKED_PARALLEL_DATA_CONTRACTS`

## Decision

The complete public-content task cannot be implemented truthfully on this branch because required canonical data contracts belong to unfinished or parallel scopes. A gallery-only subset would not satisfy T284 and must not be presented as complete.

## Dependency Audit

- Gallery/location: ordered property images, localized alt text, address and coordinates can support read-only presentation.
- Amenities: canonical catalog and assignments are owned by parallel T255 (`69d16c0`) and are not converged here.
- Policies: versioned operational policies belong to T256; cancellation/refund/no-show, breakfast and pay-at-property remain blocked under T277.
- Verified reviews: no completed-stay-linked review, moderation or verified model exists; that contract belongs to T313.
- Booking/payment facts: only internal management configuration exists. Safe public projection belongs to T288; quote/deposit facts depend on T282/T289.

## Safety Boundary

This branch does not duplicate parallel schema, invent policies or verified reviews, expose merchant configuration, require a map credential, or advertise unsupported payment facts.

## Verification

- Read-only review covered spec, plan, constitution, task, inventory, DTO/entities/repositories and the current hotel-detail UI.
- No `Active Parallel Claims` table or T284 completion evidence exists in this worktree.
- T283 canonical public eligibility is complete but does not supply the missing content contracts.

## Resume Condition

Converge T255, approve/land T256 and T277, land T288 public payment facts and T313 verified reviews. Then build and test the localized gallery, accessible location, policies, verified reviews and booking/payment sections from canonical projections.
