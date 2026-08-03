# T212 Chat WebSocket Authentication And Destination Authorization

## Scope

- Pins the integration context explicitly to `BackendApplication` and starts a real
  embedded HTTP/WebSocket server on a random port.
- Connects through the production `/ws-chat` SockJS/STOMP endpoint with real JWTs
  issued for deterministic E2E customer and administrator fixtures.
- Exercises authentication, destination authorization, role separation and
  session reauthentication rather than calling the interceptor only as a unit.

## Automated Validation

Backend commands from `backend/`:

```powershell
.\mvnw.cmd -q '-Dtest=ChatWebSocketIntegrationTest' test
.\mvnw.cmd -q '-Dtest=ChatChannelInterceptorTest' test
```

Results:

| Suite | Tests | Passed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| `ChatWebSocketIntegrationTest` | 4 | 4 | 0 | 0 | 0 |
| `ChatChannelInterceptorTest` | 7 | 7 | 0 | 0 | 0 |
| **Total** | **11** | **11** | **0** | **0** | **0** |

The embedded-server suite proves:

- a malformed bearer token cannot establish a chat STOMP session;
- a chat session is disconnected when it subscribes to an unrelated broker
  destination;
- a customer cannot subscribe to `/user/queue/support/messages`, while a
  permissioned administrator can;
- disconnecting and reconnecting creates a fresh authenticated session that can
  subscribe only to the approved customer queue.

## Frontend Runner Note

The focused Angular command for `chat.service.spec.ts`, `chat-widget.spec.ts` and
`chat-dashboard.spec.ts` generated no result and remained stuck in the `ng test`
runner for more than five minutes. Only the exact `npm`, `ng` and `esbuild`
processes created by that command were stopped. This is recorded as a tooling
limitation, not as a passing frontend rerun. The existing source tests remain in
place, while the release claim for CROSS-014 relies on the fresh 11/11 backend
authorization and real STOMP evidence above.

## Runtime Note

The embedded-server Maven wrapper exceeded the command-output timeout during
shutdown, after Surefire had already written the final 4/4 passing report. No
application assertion failed and no external provider credential was used.
