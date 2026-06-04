# API Notes

Last updated: 2026-06-04

## Response Contract

All REST endpoints return the project wrapper:

```json
{
  "code": 200,
  "message": "Success",
  "data": {}
}
```

Error responses use the same shape without `data`:

```json
{
  "code": 401,
  "message": "Token is invalid or expired"
}
```

`ApiResponse<T>` is the runtime contract. Swagger/OpenAPI schemas must document this wrapper, not only the inner DTO.

## OpenAPI / Swagger

Springdoc is enabled with `springdoc-openapi-starter-webmvc-ui`.

- API docs: `/v3/api-docs`
- Swagger UI: `/swagger-ui/index.html`
- Local server in docs: `http://localhost:8080`
- Bearer auth scheme name: `bearerAuth`

Documentation rules:

- Application-level metadata lives in `common/config/OpenApiConfig`.
- Common response schemas live in `common/response/SwaggerResponses`.
- Module-specific wrapper schemas live inside the owning module, for example `modules/auth/dto/response/AuthSwaggerResponses`.
- Do not make `common` depend on business modules.
- Do not place raw JWT, refresh token, password, token hash, cookie, or verification link in examples.

## Auth Endpoints

Base path: `/api/auth`

| Method | Path | Auth | CSRF | Notes |
| --- | --- | --- | --- | --- |
| `GET` | `/csrf` | Public | No header required | Initializes `XSRF-TOKEN` cookie. |
| `POST` | `/login` | Public | `X-XSRF-TOKEN` required | Returns access token and sets refresh cookie. |
| `POST` | `/oauth2/exchange` | Public | `X-XSRF-TOKEN` required | Exchanges one-time OAuth2 code for token pair. |
| `POST` | `/register` | Public | `X-XSRF-TOKEN` required | Creates pending user and publishes verification email event. |
| `POST` | `/verify` | Public | `X-XSRF-TOKEN` required | Consumes verification token atomically. |
| `POST` | `/refresh` | Public | `X-XSRF-TOKEN` required | Requires refresh cookie; rotates refresh token. |
| `POST` | `/logout` | Public | `X-XSRF-TOKEN` required | Bearer and refresh cookie are optional for idempotent retry. |
| `POST` | `/logout-all` | Bearer required | `X-XSRF-TOKEN` required | Revokes all refresh sessions for authenticated user. |

## Cookie Contract

Refresh token is stored only in an HttpOnly cookie:

- Name: `__Secure-refresh_token`
- Path: `/api/auth`
- `HttpOnly`: true
- `Secure`: configured by `app.security.auth-cookie.secure`
- `SameSite`: configured by `app.security.auth-cookie.same-site`
- Value: opaque refresh token, returned only to the client once

Login, OAuth2 exchange, and refresh set the cookie. Logout and logout-all clear it with the same attributes and `Max-Age=0`.

## CSRF Contract

CSRF uses a separate cookie/header pair:

- Cookie: `XSRF-TOKEN`
- Header: `X-XSRF-TOKEN`

All `/api/auth` state-changing endpoints require the header. `/api/auth/csrf` exists so clients can initialize the CSRF cookie before sending POST requests.

## Rate Limit Error Surface

Auth endpoints can return `429` with `ApiResponse<Void>` when Bucket4j rejects the request. Current documented endpoints include:

- `/api/auth/csrf`
- `/api/auth/login`
- `/api/auth/oauth2/exchange`
- `/api/auth/register`
- `/api/auth/verify`
- `/api/auth/refresh`
- `/api/auth/logout-all`

`POST /api/auth/logout` bypasses rate limiting so users can still attempt DB session revocation.
