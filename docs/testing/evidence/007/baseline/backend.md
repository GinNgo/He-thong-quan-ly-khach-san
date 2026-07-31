# Feature 007 Baseline - Backend

## Commands

```powershell
Set-Location backend
./mvnw.cmd test
./mvnw.cmd package -DskipTests
```

## Results

- `./mvnw.cmd test`: failed baseline; 218 tests, 24 failures, 40 errors, 0 skipped across 55 surefire reports.
- Main failing areas: `AdminUserControllerIntegrationTest`, auth/chat/hotel/payment/property search/public discovery/notification/subscription integrations, payment/refund/reservation concurrency and `TenantIsolationIntegrationTest`.
- `./mvnw.cmd package -DskipTests`: compile and JAR creation succeeded, but Spring Boot repackage failed because the target JAR could not be renamed (file lock in the working environment).
- Java 21.0.11 and Maven 3.9.16.

No failure was fixed before recording this evidence.
