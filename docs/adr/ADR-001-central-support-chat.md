# ADR-001: Central LuxeStay Support Chat

- **Status**: Accepted
- **Date**: 2026-07-28
- **Decision owners**: Product and engineering
- **Scope**: T056 authenticated customer-support chat

## Context

The routed chat UI describes LuxeStay customer support, but the implementation treats chat as an arbitrary user-to-user channel. The client chooses `senderId` and `receiverId`, both customer and admin pages hardcode `adminId = 1`, REST history accepts arbitrary user IDs, and `/ws` accepts all origins without STOMP authentication. The data model has no conversation, property, reservation, assignment, or support-team relationship.

`DataInitializer` also registers `/admin/chat` twice: `SYSTEM.AI_CHAT` and `HOTEL.CHAT`. This makes the same central support screen appear to be both a platform function and a property operation.

## Decision

Use a single central LuxeStay support queue under module `SYSTEM` and function `AI_CHAT`.

- Customer messages target the logical queue (`receiver_id = 0`), not a fixed admin account.
- Support agents require `AI_CHAT:VIEW` to list/read conversations and `AI_CHAT:CREATE` to reply.
- JWT principal is the only sender identity. Client payloads contain content and, for support replies only, the selected customer ID.
- Chat uses an authenticated `/ws-chat` SockJS/STOMP endpoint with configurable allowed origins.
- Customer delivery uses the standard authenticated destination `/user/queue/messages`.
- Support receives new queue messages from `/topic/support/messages`; subscription is permission-gated.
- REST history is principal-scoped for customers and permission-scoped for support.
- The duplicate `HOTEL.CHAT` function is no longer seeded or granted to hotel-operation roles.

The legacy notification endpoint `/ws` remains a separate contract. This ADR does not claim to solve notification authorization (`GAP-023`).

## Alternatives Considered

### Property-scoped support chat

Rejected for this iteration. It requires a conversation aggregate, property or reservation ownership, agent assignment, cross-property escalation rules, migrations, tenant-aware queries, and separate property UI. None of those contracts exist today.

### Fixed support account

Rejected. A hardcoded account ID is environment-dependent, prevents multi-agent support, and lets clients influence authorization through recipient selection.

### General peer-to-peer chat

Rejected. It is broader than the product copy and would require participant consent, discovery, blocking, abuse controls, and a different permission model.

## Security and Failure Rules

- Reject chat STOMP `CONNECT` without a valid bearer token.
- Reject unauthorized chat `SEND` and `SUBSCRIBE` before controller invocation.
- Resolve sender and user destination names from authenticated server-side user data.
- Reject empty messages and content over 2,000 characters.
- Do not persist an optimistic message when the broker is offline or rejects the frame.
- Return 401/403/404 without revealing another account's conversation contents.
- Log authentication/authorization failures without tokens or message content.

## Consequences

The current schema can be retained, and the feature can be secured without a new service, broker, or cache. Conversation listing is derived from customer messages sent to queue ID `0`; support cannot initiate a conversation before the customer enters the queue. A future property-chat feature will need a new ADR and schema rather than overloading this queue.

## Rollout and Rollback

Roll out backend contract and tests first, then update both Angular clients. Remove only the duplicate `HOTEL.CHAT` seed/role grants; preserve historical function rows until a separate migration or admin cleanup is approved. Rollback restores the previous application code, while the unchanged `chat_messages` table remains readable.
