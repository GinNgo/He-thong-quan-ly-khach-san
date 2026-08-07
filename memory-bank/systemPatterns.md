# System Patterns

## Kiến trúc tổng thể

- Monorepo gồm Spring Boot backend và Angular frontend.
- Backend theo lớp: REST controller → service nghiệp vụ → Spring Data JPA repository → database.
- Frontend dùng standalone bootstrap, route-level feature areas, core services/guards/interceptors và shared components.
- Giao tiếp chính qua HTTP JSON; WebSocket/STOMP có mặt cho luồng realtime.

## Backend

- Package gốc: `com.hotel`.
- REST controller: `backend/src/main/java/com/hotel/controllers/`.
- Service: `backend/src/main/java/com/hotel/services/`.
- Repository: `backend/src/main/java/com/hotel/repositories/`.
- JPA entity: `backend/src/main/java/com/hotel/entities/`.
- DTO nằm trong cả `dto/` và `dtos/`.
- Transaction nghiệp vụ nằm tại service.
- Flyway migration: `backend/src/main/resources/db/migration/`.

## Bảo mật

- JWT stateless qua `JwtAuthFilter` và `JwtTokenProvider`.
- URL policy nằm trong `SecurityConfig`.
- Method authorization dùng role/authority.
- Permission chức năng dùng annotation/interceptor riêng.
- Phạm vi cơ sở được kiểm tra qua `PropertyAccessService`.
- Frontend gửi JWT bằng HTTP interceptor và chặn route bằng auth/role/feature/permission guards.
- Mọi giá trị credential hoặc key phải ở ngoài Memory Bank.

## Frontend

- `src/main.ts` gọi `bootstrapApplication`.
- `app.config.ts` cấu hình router, HTTP interceptors, animation và PrimeNG.
- `app.routes.ts` định nghĩa public, client, admin, management và system routes.
- Core API clients nằm trong `src/app/core/services/`.
- Feature UI nằm trong `src/app/features/`.
- E2E dùng Playwright; component/unit test dùng file `*.spec.ts`.

## Luồng dữ liệu quan trọng

- Auth: Angular auth service → `AuthController` → `AuthService` → user/role/permission repositories.
- Public search: Angular property/home services → public discovery/search controller → search services → hotel/room/location repositories.
- Reservation: Angular reservation service → `ReservationController` → `ReservationService` → reservation/room/invoice/payment repositories.
- Admin inventory: Angular admin services → room/room-type/service controllers → services → repositories.

## Rủi ro cần kiểm tra ở task liên quan

- Flyway và Hibernate schema update cùng được cấu hình; ảnh hưởng schema phải được kiểm tra trước migration.
- DTO tồn tại ở hai package `dto` và `dtos`; tránh giả định package thống nhất.
- Tính đúng của concurrency đặt phòng cần dựa trên transaction/locking và test hiện tại.