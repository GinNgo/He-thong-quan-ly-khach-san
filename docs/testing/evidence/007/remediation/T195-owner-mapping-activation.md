# T195 - Owner mapping activation timing

## Scope

Pending property registration and imported-property claims no longer grant operational owner authority before review. Applicant ownership is represented by a `user_properties` row with `status=PENDING`; `PROPERTY_OWNER` is granted only when an administrator approves the property or claim.

Rejecting a property, rejecting a claim, or cancelling a pending claim expires the pending mapping as `INACTIVE` and records `end_date`. Operational property state is updated atomically with approval/rejection through the lifecycle service.

## Evidence

- `PropertyOwnershipLifecycleServiceTest`: 4/4 passed, including protection against rejecting an already approved property while active ownership remains.
- `PropertyRegistrationServiceTest`: 1/1 passed; registration leaves roles and mapping pending.
- `PropertyClaimServiceTest`: 5/5 passed; request, approval, rejection and cancellation boundaries covered.
- `PropertyClaimControllerIntegrationTest`: 8/8 passed.
- `HotelControllerIntegrationTest`: 2/2 passed.

## Boundaries

This remediation does not implement ownership transfer/history or claim response DTO privacy; those remain T196 and later ownership tasks. Platform entitlement authority remains tracked by T197/T198.
