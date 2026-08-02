# T190 Operational Observability Evidence

Date: 2026-08-02
Capability: `CROSS-041`
Task: `T190`

## Implemented Contract

- Every HTTP request resolves one bounded `X-Correlation-ID`, returns it to the client, places it in MDC and records method/outcome duration without URL, query, user or payload tags.
- Inbound STOMP frames reuse the connection correlation id, keep it in MDC for the channel send, and increment a failure counter for rejected or unsent frames.
- The application task scheduler wraps every current or future `@Scheduled` recovery/outbox poller with a generated correlation id, duration, outcome and alertable failure counter.
- Mail delivery records only channel/template/outcome/duration; recipient addresses, message bodies, invoice identifiers, exception messages and credentials are not metric tags or log fields.
- Actuator exposes public detail-free liveness/readiness probes. All other actuator routes require `SUPER_ADMIN`; anonymous and normal authenticated users are denied.
- Angular HTTP and STOMP clients generate/preserve correlation ids and emit bounded operational events containing transport, operation, status, outcome and time only. Request URLs, bodies, tokens and broker error details are excluded.
- The project still has no durable email/notification outbox; `CROSS-009` remains separately tracked. This task supplies the scheduler-level observability contract that future outbox pollers inherit.

## Executed Verification

Backend focused suites:

```powershell
Set-Location backend
.\mvnw.cmd -q '-Dtest=CorrelationIdFilterTest,OperationalMetricsTest,ObservingTaskSchedulerTest,StompObservabilityInterceptorTest,FinancialErrorContractTest' test
.\mvnw.cmd -q -DforkCount=0 '-Dtest=EmailServiceTest,EmailObservabilityTest,OperationalMetricsTest' test
.\mvnw.cmd -q '-Dtest=ObservabilityEndpointIntegrationTest' test
.\mvnw.cmd -q '-Dtest=AuthExceptionIntegrationTest' test
```

Result: 21/21 assertions passed across HTTP correlation, secret-safe meters, STOMP frame outcomes, scheduled jobs, mail adapters, public health probes, operator-only metrics and stable-error regressions. The full-context endpoint report completed 3/3 assertions; the CLI wrapper remained attached after the report on two runs, while no Java process or failed assertion remained.

Frontend focused suites:

```powershell
Set-Location frontend
.\node_modules\.bin\ng.cmd test --watch=false --no-progress `
  --include "src/app/core/services/client-observability.service.spec.ts" `
  --include "src/app/core/interceptors/error-interceptor.spec.ts" `
  --include "src/app/core/services/chat.service.spec.ts" `
  --include "src/app/core/services/notification.service.spec.ts"
```

Result: command exited 0 with 18 declared tests covering safe client events, response-correlation preservation, per-message STOMP correlation, notification connection metadata and existing auth/navigation behavior.

## Meter Names

| Meter | Low-cardinality tags |
|---|---|
| `hotel.http.server.requests` | `method`, `outcome` |
| `hotel.stomp.frames` | `frameType`, `outcome` |
| `hotel.scheduled.jobs` | `job`, `outcome` |
| `hotel.external.operations` | `channel`, `operation`, `outcome` |
| `hotel.operational.failures` | `layer`, `reason` |

No meter accepts a route value, resource id, recipient, username, token, request/response body or provider error message.

## Outcome

`CROSS-041` is promoted to `COMPLETE_VERIFIED` for the defined platform observability contract. Provider-specific alert routing and a durable outbox UI remain separate capabilities and are not claimed by this task.
