# Tech Context

## Backend

- Java: `21`, xác minh trong `backend/pom.xml`.
- Framework: Spring Boot `3.2.5`.
- Build/dependency manager: Maven.
- Web/API: Spring Web.
- Persistence: Spring Data JPA.
- Database runtime driver: Microsoft SQL Server JDBC.
- Test/runtime database dependency: H2.
- Migration dependency: Flyway core và Flyway SQL Server.
- Security: Spring Security và JJWT `0.11.5`.
- Validation: Spring Boot validation starter.
- Realtime: Spring WebSocket.
- Email: Spring Boot mail starter.
- API documentation: Springdoc OpenAPI WebMVC UI.
- Package root: `com.hotel`.

## Frontend

- Framework: Angular `^22.0.1`.
- Language: TypeScript `~6.0.3`.
- Bootstrap: standalone `bootstrapApplication` từ `frontend/src/main.ts`.
- UI: PrimeNG, PrimeIcons, Bootstrap 5 và Tailwind CSS.
- State/API support: RxJS.
- Translation: `@ngx-translate`.
- Charts/document output: Chart.js, jsPDF và html2canvas.
- Realtime client dependencies: STOMP/SockJS.
- Unit/component test: Vitest, jsdom và Angular test tooling.
- E2E test: Playwright `^1.61.1`.
- Package scripts đã thấy: `start`, `build`, `watch`, `test`.
- Chưa thấy script lint trong `frontend/package.json`.

## Database và môi trường

- Database mục tiêu được xác minh từ manifest là Microsoft SQL Server.
- H2 được khai báo cho runtime/test hỗ trợ kiểm thử.
- Chi tiết instance, schema, credential và dữ liệu thật: không lưu; TBD - Chưa xác định từ source an toàn.
- Biến môi trường production: TBD - Chưa xác định từ source an toàn.

## Kiểm tra thiết lập Memory Bank

- Không chạy build theo yêu cầu thiết lập.
- Không chạy test theo yêu cầu thiết lập.
- Không chạy migration.