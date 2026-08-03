# Avatar Storage and Lifecycle

## Runtime contract

`POST /api/uploads/image` is an authenticated avatar operation. The authenticated user id comes from the JWT principal; no user id is accepted from the multipart body or URL. The service locks that user row, validates the file, stores the new object, updates `users.avatar_url`, and returns the verified URL plus image metadata.

Accepted formats are JPEG, PNG and WebP. The service checks magic bytes and format structure, validates decoded/header dimensions, enforces a 5 MiB byte limit, a 4096 x 4096 maximum dimension and a 16,777,216 pixel maximum. The declared multipart MIME type is only a consistency check and is never the source of truth.

## Local storage

Development and simulator environments use the directory configured by `upload.path` (default `uploads`). Files are written to a temporary file in that directory and moved atomically to a random filename of the form `avatar-{userId}-{uuid}.ext`. Public serving accepts only a single safe image filename, re-validates the image, sends the detected media type, and sets `X-Content-Type-Options: nosniff`.

When a replacement succeeds, the previous application-managed file is deleted after transaction commit. If persistence fails or the transaction rolls back, the newly written file is deleted. External HTTPS avatar URLs are never deleted by this service.

## Production object storage

Production should use a private object-storage adapter behind the same `FileUploadService` contract (for example, an S3-compatible bucket or Azure Blob container). The adapter must:

- keep the bucket/container private and expose short-lived signed read URLs or a backend streaming endpoint;
- use a server-generated object key containing the authenticated user id and an opaque random suffix;
- set content type from verified bytes, disable public content sniffing, and enforce server-side size limits;
- write the database pointer only after the object write succeeds, then delete the old key from an after-commit outbox;
- retry deletes idempotently and expose failed cleanup for operator reconciliation;
- keep credentials in the deployment secret store, never in Git or `application.yml`.

The local filesystem implementation is intentionally deterministic for tests and must not be treated as a production durability or backup strategy.
