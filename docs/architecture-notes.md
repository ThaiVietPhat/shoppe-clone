# Architecture Notes

Last updated: 2026-06-04

## Current State

The project is a Spring Boot modular monolith under package `com.shopee.monolith`.

Implemented modules after Phase 2:

- `common`: base entity, response wrapper, exception model, global exception handling, async/config helpers, OpenAPI config.
- `user`: user core, OAuth identities, verification token persistence, user authentication lookup DTOs.
- `auth`: JWT, refresh token families, login, registration, verification, OAuth2 exchange, logout, blacklist, CSRF/CORS, rate limiting.
- `notification`: verification email listener and delivery flow.

## Module Boundary Rules

- `common` must not depend on business modules.
- Cross-module calls use service interfaces and DTOs, not entity references.
- Side effects use Spring application events and listeners.
- Search/notification-style listeners must not call back into business modules unless explicitly allowed by design.

OpenAPI wrapper schemas follow the same boundary:

- Common wrapper without payload: `common/response/SwaggerResponses.ApiResponseVoid`.
- Auth-specific wrappers: `modules/auth/dto/response/AuthSwaggerResponses`.
- Do not put `LoginResponse`, `UserResponse`, or future module DTO imports inside `common`.

## Auth Runtime Flow

### Login

1. `AuthController.login` receives email/password.
2. `AuthService` loads `UserAuthenticationData` from UserModule.
3. Password login is blocked for OAuth-only accounts and non-active/locked states.
4. Access JWT is issued.
5. Initial refresh token family and refresh token hash are persisted.
6. Raw refresh token is returned only as `__Secure-refresh_token` cookie.

### Refresh Rotation

1. Client sends refresh cookie and CSRF header.
2. Refresh token hash is looked up.
3. Parent refresh-token family row is locked before token mutation.
4. Old token becomes tombstone with replacement hash.
5. New refresh token in same family is created.
6. Reuse detection revokes the active family and returns an auth error.

### Logout

`POST /api/auth/logout` is intentionally idempotent:

- It may receive bearer token, refresh cookie, both, or neither.
- If refresh cookie exists, the current family is revoked/deleted according to service contract.
- If bearer token exists, access token `jti` is blacklisted.
- The refresh cookie is always cleared.
- Rate limiting and blacklist pre-check bypass this route so logout can still attempt DB revocation.

`POST /api/auth/logout-all` is protected:

- Bearer JWT is required.
- Blacklist pre-check applies.
- Rate limiting applies.
- Redis failure on protected path is fail-closed.

## Security Filters

Current filter chain:

1. `JwtAuthenticationFilter`
2. `BlacklistFilter`
3. `RateLimitingFilter`

Important behavior:

- Anonymous public routes continue without JWT principal.
- Invalid/malformed bearer token is rejected.
- Authenticated requests check Redis blacklist.
- Protected requests fail-closed when Redis blacklist/rate-limit state cannot be checked.
- Public normal routes may fail-open for rate limiter Redis outage, except auth abuse-control endpoints.

## OpenAPI Architecture

Springdoc is configured in `OpenApiConfig`.

- Global bearer scheme is declared as a component only.
- Bearer is not applied globally.
- Protected operations add `@SecurityRequirement(name = "bearerAuth")` explicitly.
- Auth endpoints document CSRF header, refresh cookie, `Set-Cookie`, and wrapper response schemas.

## Next Architecture Step

Phase 3 starts Commerce Catalog Foundation:

1. Shop Core in UserModule.
2. Product/Category/Variant module.
3. Inventory module with pessimistic lock ordering.
4. Later Cart/Order/Checkout uses Product and Inventory service interfaces.
