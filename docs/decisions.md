# Decisions

Last updated: 2026-06-04

## Phase 2 Decisions

### JWT Runtime Contract

- Access tokens use fixed HS256.
- JWT validates `iss`, `aud`, `sub`, `role`, `jti`, `iat`, and `exp`.
- JWT header includes `kid`.
- Signing uses the active configured key.
- Verification accepts active and optional previous key during key rotation.
- Unknown `kid` and non-HS256 algorithms are rejected.
- Raw JWTs and parse stack traces from client errors must not be logged.

### Refresh Token Storage

- Refresh tokens are opaque random tokens.
- Database stores only SHA-256 hashes.
- Raw refresh token is returned only once via HttpOnly cookie.
- Refresh token families are explicit parent rows used as aggregate lock.
- Rotation locks family row before mutating child tokens.
- Rotated tokens remain as tombstones until expiry to support reuse detection.

### Logout Behavior

- Logout current session is idempotent.
- `POST /api/auth/logout` is public but CSRF-protected.
- Bearer access token and refresh cookie are optional at runtime.
- Logout bypasses blacklist pre-check and rate limit so revocation can still be attempted.
- Logout-all is a protected operation and must fail-closed when blacklist/Redis cannot be checked.

### Redis Failure Policy

- Protected requests fail-closed when blacklist or authenticated rate-limit checks cannot complete.
- Auth abuse-control endpoints fail-closed for rate limiter Redis outage.
- Normal public routes may fail-open for rate limiter Redis outage.
- Logout rate limiting fails open by design.

### CSRF and Cookies

- Refresh token cookie is HttpOnly and scoped to `/api/auth`.
- Access token is never stored in a cookie in Phase 2.
- CSRF uses separate `XSRF-TOKEN` cookie and `X-XSRF-TOKEN` header.
- `/api/auth` state-changing endpoints require CSRF.
- Refresh cookie remains HttpOnly; frontend reads only the CSRF cookie.

### OAuth2 Login

- OAuth2 callback creates a one-time exchange code in Redis.
- Frontend exchanges the code through `POST /api/auth/oauth2/exchange`.
- Exchange uses Redis atomic consume.
- OAuth-only users cannot login with password.
- Existing accounts are not auto-linked by email alone.
- OAuth failure redirects expose only stable error codes.

### Registration and Verification

- User email normalization is trim + lowercase.
- `normalized_email` is unique at DB level.
- Verification token is opaque and persisted as SHA-256 hash.
- Verification is a POST mutation, not GET.
- Verification token consumption is atomic.
- Registration publishes a post-commit verification email event.
- SMTP failure must not fail or roll back registration.

### Rate Limiting

- Bucket4j is backed by Redis.
- Anonymous key format: `rate_limit:ip:{ip}`.
- Authenticated key format: `rate_limit:user:{userId}`.
- Abuse-control buckets exist for login, register, verify, resend verification, and OAuth callback/exchange.
- Webhook-specific bucket is deferred to PaymentModule.

### OpenAPI / Swagger

- Swagger docs must reflect `ApiResponse<T>`.
- Success and error responses document wrapper schemas.
- Common OpenAPI docs must not create reverse dependency from `common` to business modules.
- Bearer auth is declared as a component, not a global security requirement.
- Public auth endpoints must not show bearer as required.
- `logout-all` explicitly documents bearer requirement.
- CSRF header, refresh cookie, and `Set-Cookie` behavior are documented on auth endpoints.
- Examples use placeholders for secrets: `<jwt-access-token>`, `<password>`, `<opaque-verification-token>`.

## Phase 3 Decisions To Carry Forward

- Start with Shop Core because Product depends on `shops`.
- ProductModule must use Shop/User service interfaces and DTOs, not entity references.
- Inventory must keep `available_stock` and `reserved_stock` separate.
- Inventory locks must be acquired by `variant_id ASC`.
- Every new REST endpoint must include OpenAPI annotations from the start.
