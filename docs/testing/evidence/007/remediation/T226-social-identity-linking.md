# T226 Social Identity Provisioning and Linking Evidence

## Scope

- Google and Facebook identities persist an immutable `(provider, provider_subject)` key; provider email changes do not move ownership.
- Unknown provider subjects provision only when the email is not already owned. An existing local email returns `SOCIAL_LINK_REQUIRED` and requires an authenticated explicit link.
- Authenticated users can list, link and unlink providers. The last unlink requires the current password; missing links are idempotent and provider ownership collisions are denied.
- Provisioning runs in an isolated transaction and recovers a concurrent unique-key winner by provider subject without creating duplicate users or identities.
- Social auth responses aggregate role permission masks using the same OR semantics as credential responses.

## Automated results

| Layer | Command | Result |
|---|---|---|
| Backend focused services | Selective JUnit launcher for `SocialAccountLinkServiceTest`, `SocialAccountProvisioningServiceTest` and `SocialAuthPermissionContextTest` | 10/10 passed |
| Angular typed client | `npm test -- --watch=false --include=src/app/core/services/auth-social-identities.spec.ts` | 3/3 passed |
| Full backend build | `./mvnw.cmd test-compile` | BLOCKED_EXTERNAL by pre-existing subscription/notification source drift outside T226 |

No provider credentials, production OAuth app, or real-money flow was used. Provider adapter integration remains an external sandbox gate (AUTH-016/AUTH-017).
