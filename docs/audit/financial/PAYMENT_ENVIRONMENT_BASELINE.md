# Payment Environment Baseline - 2026-07-31

## Findings

- `.env.example` contains empty placeholders rather than production secrets and is safe to publish as a template.
- Spring datasource password and JWT/provider secrets are environment-backed, but the SQL Server Docker Compose file previously used a hard-coded `sa` password.
- The Compose password is now required from `MSSQL_SA_PASSWORD`; no fallback password is present.
- `.gitignore` now ignores environment files while explicitly retaining `.env.example`.
- `.dockerignore` excludes `.env*`, generated artifacts, test reports and local databases from build context.
- Production provider mode remains a separate readiness gate; no production credential was added.

## Verification

```powershell
rg -n "MSSQL_SA_PASSWORD|DB_PASSWORD|JWT_SECRET|VNPAY_HASH_SECRET|MOMO_SECRET_KEY|ZALOPAY_KEY" docker-compose.yml backend/src/main/resources .env.example
git check-ignore .env .env.local .env.production
git check-ignore backend/target frontend/dist
```

Expected result: only variable names/placeholders appear in tracked configuration; local environment and generated output are ignored.

## Rollback

If local Compose startup needs a password, set `MSSQL_SA_PASSWORD` in an untracked `.env` file. Do not restore a literal password to `docker-compose.yml`.
