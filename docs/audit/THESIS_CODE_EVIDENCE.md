# Code evidence map - 2026-07-28

Nguồn: backend/src/main/java/com/hotel/controllers, services, repositories, entities, security và frontend/src/app. Bảng này ghi sự tồn tại của contract/runtime source; không thay thế kết quả test.

## Controller inventory theo capability

| Domain | Controller/source | Contract evidence | Trạng thái review |
| --- | --- | --- | --- |
| Auth/user | AuthController, UserController | /api/auth, /api/users | REVIEW |
| Location/search | LocationController, PublicDiscoveryController, PropertySearchController, HotelController | /api/public/locations, /api/public/search, /api/public/properties, /api/v1/hotels/public | REVIEW |
| Property | HotelController, FileUploadController | /api/v1/hotels, /api/uploads | REVIEW |
| Room/inventory | RoomTypeController, RoomController, ManagementPortalController | /api/room-types, /api/rooms, /api/management | REVIEW |
| Reservation/stay | ReservationController, HotelServiceController | /api/reservations, /api/services | REVIEW |
| Payment/invoice | PaymentController, MockPaymentController, InvoiceController | /api/payments, /api/invoices | REVIEW |
| RBAC/system | RoleController, RolePermissionController, AppFunctionController, AppModuleController | /api/roles, /api/role-permissions, /api/functions, /api/modules | REVIEW |
| Subscription | SubscriptionController | /api/subscriptions | REVIEW |
| Import/claim/partner | PropertyImportController, PropertyClaimController, PropertyRegistrationController, AdminPartnerController | /api/admin/property-imports, /api/properties/{id}/claim, /api/partner | REVIEW |
| Chat/AI | ChatController, AiController | /api/chat, /api/ai/chat, WebSocket config | PARTIAL; backend controller/service/channel tests CURRENT, frontend E2E pending |
| Notification | NotificationController, NotificationService and security/config additions | /api/notifications and WebSocket config | PARTIAL; backend controller/service/channel tests CURRENT, frontend delivery/E2E pending |
| Analytics | AnalyticsController | /api/analytics/dashboard | REVIEW; verify UI and data evidence |

## Core evidence layers

- Security: backend/src/main/java/com/hotel/security and config; verify JWT, PermissionInterceptor, tenant/property access and WebSocket channel checks separately.
- Services: backend/src/main/java/com/hotel/services; business rules for reservation, payment, refund, subscription, import, chat and notification.
- Persistence: backend/src/main/java/com/hotel/entities, repositories and backend/src/main/resources/db/migration.
- Frontend: frontend/src/app/core, shared and features; route guards are UX/navigation support, not sole authorization.

## Scope cautions

- Reservation contract currently exposes one booking with one RoomType and quantity; mixed RoomType remains deferred unless a different request/aggregate is found.
- Staff services during stay are not evidence that customer add-on checkout exists.
- Chat/notification source exists, but completion requires authenticated handshake/channel integration evidence after the parallel code changes finish.
- Subscription controller existence does not prove full activate/renew/upgrade/downgrade/revoke history.
- Analytics controller existence does not prove complete financial reconciliation/reporting.

## Review procedure

For each capability, add method-level evidence, entity/migration, test and reportSections to FEATURE_TRACEABILITY_MATRIX.md. Use COMPLETE only after a current verification record is available.

## Method-level review update - 2026-07-28

| Capability | Current source evidence | Accurate status and boundary |
| --- | --- | --- |
| Subscription context | `SubscriptionController`: `GET /api/subscriptions/plans`, `GET /api/subscriptions/me`, `GET /api/subscriptions/me/features`; `SubscriptionFeatureService`; `FeatureGateIntegrationTest`; `SubscriptionControllerIntegrationTest` | PARTIAL: feature lookup and limit checks exist; no REST mapping for register/activate/renew/upgrade/cancel/revoke/history/usage |
| Import staging | `PropertyImportController.searchAndStage`, `getBatches`, `getBatchItems`, `importBatch`; `PropertyImportService`; `PropertyImportItem` staging/deduplication fields | PARTIAL: backend staging/import contract exists; authenticated UI/E2E and provider-run evidence are not current |
| Property claim | `PropertyClaimController.requestClaim`, `getAllClaims`, `approveClaim`, `rejectClaim`; `PropertyClaimService`; `PropertyClaimRequest` | BLOCKED/PARTIAL: source contains fixed requester/admin ids (`2L`/`1L`), so ownership/audit correctness is not COMPLETE until principal extraction is implemented and tested |
| Location import | `LocationImportService.importData`, UTF-8/BOM-safe resource reader, province/ward upsert and obsolete cleanup flag; `LocationImportServiceTest` | COMPLETE for the service contract covered by current backend tests; startup execution remains configuration-dependent |
| Central support chat | `ChatController` REST/MessageMapping endpoints; `ChatService`; `ChatChannelInterceptor`; `ChatAuthorizationService`; chat tests | PARTIAL: backend auth, queue routing and history tests are CURRENT; authenticated frontend E2E/release delivery remains unverified |
| Notification | `NotificationController`, `NotificationService`, `NotificationHandshakeInterceptor`, `NotificationChannelInterceptor`; notification tests | PARTIAL: backend persistence, personal/admin destinations and STOMP authorization are CURRENT; frontend delivery/E2E remains unverified |

The API document must use the endpoint list above as the current contract. Entity presence for subscription orders/payments/history does not prove a complete billing lifecycle.
