# T135 Financial Reporting Security

The integration test verifies independent HTTP permissions and context boundaries:

- `/api/management/reports/property-revenue` is annotated with `REPORT` + `VIEW`;
- `/api/admin/reports/platform-revenue` is annotated with `PLATFORM_REVENUE` + `VIEW`;
- a caller with only one permission receives `403` for the other endpoint through `PermissionInterceptor`;
- property and platform report services reject the other context before invoking their repositories.

No property identifier is accepted by the platform controller, and no platform plan filter is accepted by the property controller.

## Automated Validation

Command from `backend/`:

```powershell
.\mvnw.cmd '-Dtest=FinancialReportingSecurityIntegrationTest' -DforkCount=0 test
```

Result on 2026-08-02: 2 tests passed, 0 failed, 0 skipped.

No production credentials, external provider, migration or real-money operation was used.
