# T318 Notification WebSocket Security And Recovery Evidence

## Scope and contract

- Pins the embedded-server integration context explicitly to `BackendApplication` and connects through the production `/ws` SockJS/STOMP endpoint on a random port.
- Requires a valid bearer token on STOMP `CONNECT`; an expired token cannot establish a notification session.
- Allows `/topic/admin/notifications` only with `REPORT:VIEW`, allows the standard principal-scoped `/user/queue/notifications`, rejects forged user destinations, and rejects all client `SEND` frames on the notification endpoint.
- Keeps chat and notification handshake markers separate so each endpoint applies only its own destination policy.
- Exposes `connecting`, `connected`, `reconnecting`, `offline`, and `error` states in the admin notification shell. The shell announces failures, marks the bell visibly, and provides an explicit reconnect action.
- Adds `/ws` to both local HTTP and HTTPS Angular proxy configurations. This task does not change retained REST history (T317), customer inbox behavior (T319), or durable delivery/replay (T320).

## Validation handoff

Source is frozen. Root validation is serialized with the other active workstreams to avoid concurrent Maven and Angular output writes.

| Layer | Command | Expected focused coverage | Status |
|---|---|---:|---|
| Backend interceptor + real STOMP | `backend/.\mvnw.cmd -q "-Dtest=NotificationChannelInterceptorTest,NotificationWebSocketIntegrationTest" test` | 12 tests | Pending root validation |
| Angular service + admin recovery | `frontend/npm test -- --watch=false --include=src/app/core/services/notification.service.spec.ts --include=src/app/layout/admin-layout/admin-layout.notification.spec.ts` | 12 tests | Pending root validation |
| Browser offline recovery | `frontend/npx playwright test e2e/notification-websocket-recovery.spec.ts --project=chromium` | 1 test | Pending root validation |
| Proxy JSON | `Get-Content -Raw frontend/proxy.conf.json \| ConvertFrom-Json; Get-Content -Raw frontend/proxy.https.conf.json \| ConvertFrom-Json` | 2 files | PASS |
| Static patch check | `git diff --check -- <T318 scoped files>` | Scoped files | PASS; line-ending warnings only |

## Focused assertions

- missing and expired bearer tokens are rejected before a STOMP session is established;
- a permissioned administrator can connect and subscribe to the protected admin topic;
- an authenticated customer can use the standard personal queue but is disconnected after attempting the admin topic;
- a notification client is disconnected after attempting to publish to an application destination;
- the client sends the latest bearer token and a bounded correlation ID on every connection attempt;
- broker authorization failures stop automatic retry loops until the administrator explicitly retries;
- transport loss exposes reconnecting/offline state without logging broker payloads or credentials;
- the admin shell exposes an accessible reconnect action, while REST pagination and mark-read behavior remain unchanged.

## Migration and recovery

- Database migration: N/A.
- Forward recovery: correct the allowed origin or proxy target, restore network access, then use `Kết nối lại` in the notification panel. STOMP reconnect reads the latest valid access token before opening a new session.
- Rollback: revert the notification-only interceptor/config/client/admin-shell files and remove the `/ws` proxy entries. No notification rows are modified by this task.
