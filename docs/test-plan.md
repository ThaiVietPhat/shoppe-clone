# Test Plan

Last updated: 2026-06-04

## Verification Commands

Use focused tests first, then full verification.

```powershell
mvn test
mvn verify
```

For documentation-only changes, `mvn compile` is enough to validate imports, annotation usage, and Checkstyle.

```powershell
mvn compile
```

Latest local documentation verification:

- `mvn compile` passed.
- Checkstyle reported 0 violations.

## Phase 2 Coverage Areas

### Auth Unit Tests

Covered or expected coverage:

- JWT properties validation.
- JWT issue/parse success.
- Reject wrong signature, missing claims, blank `jti`, wrong issuer/audience, unknown `kid`, and wrong algorithm.
- Refresh token generation and SHA-256 hashing.
- Login success and failure paths.
- Refresh rotation success, expired token, revoked token, reuse detection.
- Logout current session and logout-all service behavior.
- OAuth2 exchange code consume and invalid code paths.
- Registration and verification token flow.
- Rate limit key routing and failure behavior.

### PostgreSQL Integration Tests

Covered or expected coverage:

- Refresh token family schema and FK ownership invariant.
- Rotation with pessimistic family locking.
- Reuse detection revokes family.
- Concurrent refresh attempts.
- Login-vs-logout-all ordering.
- Verification token consume with pessimistic lock.
- Duplicate normalized email race.
- Refresh token cleanup and tombstone retention.

### Redis Integration Tests

Covered or expected coverage:

- Access-token blacklist key format: `security:blacklist:{jti}`.
- Blacklist TTL equals remaining JWT lifetime.
- Blacklist idempotency.
- Redis unavailable fail-closed behavior for protected requests.
- OAuth2 one-time exchange code atomic consume.
- Bucket4j rate-limit key format and refill behavior.
- Rate limiter fail-open/fail-closed split by route.

### HTTP / Security Integration Tests

Covered or expected coverage:

- Public auth endpoints are accessible without bearer token.
- Protected endpoint rejects missing/invalid bearer token.
- `logout-all` requires bearer.
- Blacklisted token is rejected.
- Redis unavailable returns 503 for protected blacklist/rate-limit checks.
- `/api/auth/logout` can still attempt revocation and clear cookie.
- CSRF required for `/api/auth` state-changing endpoints.
- CORS exact-origin allowlist and credentials behavior.
- Refresh cookie attributes on login/refresh/logout.

### Notification Tests

Covered or expected coverage:

- User registration publishes event after commit.
- Verification email listener is async and out-of-transaction.
- Verification link does not expose token hash.
- SMTP failure does not roll back registration.
- Event publication log keeps failed delivery for retry/replay.

## OpenAPI / Swagger Test Plan

For every controller endpoint:

- Check `@Tag` exists at controller level.
- Check `@Operation` exists at method level.
- Check success and main error `@ApiResponse` entries exist.
- Check response schemas use `ApiResponse<T>` wrappers.
- Check public endpoints do not show bearer auth required.
- Check protected endpoints include `@SecurityRequirement(name = "bearerAuth")`.
- Check CSRF-protected endpoints document `X-XSRF-TOKEN`.
- Check refresh-token endpoints document `__Secure-refresh_token` cookie.
- Check cookie-setting endpoints document `Set-Cookie`.
- Check examples do not contain real secrets or tokens.

Current AuthController documentation checks:

- `/api/auth/csrf`: documents `200`, `429`, `503`.
- `/api/auth/login`: documents `200`, `401`, `403`, `429`, `503`.
- `/api/auth/oauth2/exchange`: documents `200`, `400`, `403`, `409`, `429`, `503`.
- `/api/auth/register`: documents `200`, `400`, `403`, `409`, `429`, `503`.
- `/api/auth/verify`: documents `200`, `400`, `403`, `429`, `503`.
- `/api/auth/refresh`: documents `200`, `401`, `403`, `429`, `503`.
- `/api/auth/logout`: documents `200`, `403`, `503`; bearer and refresh cookie are optional.
- `/api/auth/logout-all`: documents `200`, `401`, `403`, `429`, `503`; bearer is required.

## Phase 3 Test Plan

### Task 3.1 Shop Core

Required tests:

- Service unit test for create shop success.
- Reject inactive/non-active user.
- Reject duplicate shop for same owner.
- Owner can update own shop.
- Non-owner cannot update another shop.
- Public read returns shop response.
- PostgreSQL integration test for `shops.owner_id` unique constraint.
- Controller/security IT for public read, authenticated create/update, and auth failure.
- OpenAPI annotation check for all new Shop endpoints and DTOs.

### Task 3.2 Product Core

Required tests:

- Seller can create product for owned shop.
- Non-owner cannot mutate shop product.
- Product detail/list public read.
- Variant price validation.
- Product events publish after commit.
- MapStruct mapper coverage.
- OpenAPI annotation check.

### Task 3.3 Inventory Core

Required tests:

- Create inventory for variant.
- Reject negative stock.
- Reserve decreases available and increases reserved.
- Insufficient stock throws business exception.
- Confirm/release are idempotent through reservation ledger when implemented.
- Concurrency IT verifies lock order by `variant_id ASC`.
- OpenAPI only if external inventory endpoints are exposed.
