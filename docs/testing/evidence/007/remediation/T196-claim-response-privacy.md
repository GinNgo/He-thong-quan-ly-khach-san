# T196 - Claim response privacy and serialization

## Scope

All claim request, review, rejection, cancellation and paged-list responses now use `PropertyClaimResponseDTO`. The DTO exposes only explicit property and user summary fields required by the requester/admin UI.

`User.passwordHash` is marked write-only for defensive Jackson serialization. The JPA associations on `PropertyClaimRequest` are ignored so an accidental entity response cannot traverse requester, reviewer or property graphs.

## Evidence

- Backend: `PropertyClaimServiceTest`, `PropertyClaimPrivacySerializationTest` and `PropertyClaimControllerIntegrationTest` passed 16/16.
- Frontend: `property-claims.component.spec.ts` passed 1/1 against the typed safe-response contract.
- HTTP assertions confirm `passwordHash`, roles and property user mappings are absent.

## Boundaries

Claim input validation, rate limiting and evidence-file policy remain PROP-SUB-010. Ownership mapping activation is covered separately by T195.
