# VPS final backup - 2026-08-17

This directory contains the final runtime artifacts exported from `103.116.38.74` before the hotel deployment was retired.

## Files

- `backend-app.jar`: exact JAR copied from the running backend container.
- `frontend-dist.tar.gz`: exact `/usr/share/nginx/html` content copied from the running frontend container.
- `HotelDB-final.bak.aes`: AES-256-GCM encrypted SQL Server backup.
- `docker-compose.production.yml`: production Compose template without `.env` secrets.
- `frontend-nginx.conf`: active frontend proxy configuration.
- `decrypt_db.py`: decrypts the database backup using the separately retained key.

The database backup was created with `COPY_ONLY`, `COMPRESSION`, and `CHECKSUM`, then validated using `RESTORE VERIFYONLY WITH CHECKSUM` before upload.

## Database restore

Keep the decryption key outside this public repository. Set it as `HOTEL_DB_BACKUP_KEY`, then run:

```powershell
$env:HOTEL_DB_BACKUP_KEY = Get-Content C:\path\to\hotel-backup-key-20260817.txt
python decrypt_db.py HotelDB-final.bak.aes HotelDB-final.bak
```

Restore the resulting `HotelDB-final.bak` with SQL Server tooling.

## SHA-256

```text
606483039d46e0c9db0883387b2302dc54c048362506f7ab1a1a92575e6d43ca  backend-app.jar
85c7e881f7237b1bb49274c399f68e8e9b75d97ecd74025a885f3ab923f3782d  frontend-dist.tar.gz
e59c188733f518ed1f76a4d1c018b2b1e6a88ff269022e9b1e6c5f8407b357f1  HotelDB-final.bak.aes
e611f2258ac99a34cb5bd97881f7843eb23f0f0b2382d64acee6e70e595241e3  docker-compose.production.yml
d41c6ee4a6660f447c486406dd8f4e76524734fc2cc6e59a8ad09e0cf390a550  frontend-nginx.conf
```
