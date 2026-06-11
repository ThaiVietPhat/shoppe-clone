# shoppe-clone
High-performance Shopee Clone built with Spring Boot 4 and Spring Modulith.

## Bootstrap local PostgreSQL

Create the local database once with the PostgreSQL admin account:

```bash
psql -U postgres -f scripts/create-database.sql
```

Then run Spring Boot with the local profile. Flyway creates the tables:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## Demo Script (Task 7)

### Demo data

Seed deterministic demo data after the first app start (Flyway must have run):

```bash
psql "$DATABASE_URL" -f scripts/demo-seed.sql      # demo accounts, shop, products, stock, address
psql "$DATABASE_URL" -f scripts/demo-reset.sql     # wipe everything back to a clean state
```

Demo accounts (password for both: `password`):

| Role | Email |
|------|-------|
| Buyer | `demo-buyer@shopee.local` |
| Seller | `demo-seller@shopee.local` |

After seeding, publish each product once via the seller API (`POST /api/seller/products/{id}/publish`)
to trigger Elasticsearch + pgvector indexing, or browse deterministically without it.

### Buyer path

1. `POST /api/auth/login` → Bearer access token + refresh cookie.
2. Browse: `GET /api/products/homepage`, `GET /api/search/products?q=keyboard`,
   `GET /api/search/products/semantic?q=quiet typing` (AI), `GET /api/recommendations/home`.
3. Cart: `POST /api/cart/items`, `POST /api/cart/items/select`, `POST /api/orders/preview`.
4. Checkout: `POST /api/orders` (Idempotency-Key header) → `POST /api/payments/initiate` (COD or VNPAY sandbox).
5. Orders: `GET /api/buyer/orders`, `GET /api/buyer/orders/{id}` (timeline).
6. After delivery: `POST /api/reviews` (per order item), `GET /api/notifications` (inbox).

### Seller path

1. Login as seller → `GET /api/users/me` (shop summary).
2. Products: `GET/POST /api/seller/products`, media upload via `POST /api/media`.
3. Orders: `GET /api/seller/orders` → `POST /api/seller/orders/{id}/ship` → `/deliver`.
4. Dashboard: `GET /api/seller/dashboard`; stock audit: `GET /api/inventories/variants/{id}/movements`.

### Chat

- REST history: `POST /api/chat/rooms` (buyer opens with `shopId`), `GET /api/chat/rooms`,
  `GET/POST /api/chat/rooms/{roomId}/messages`, `POST /api/chat/rooms/{roomId}/read`.
- Realtime: STOMP over WebSocket at `/ws`; send `Authorization: Bearer <token>` as a STOMP CONNECT header.
  Subscribe to `/topic/chat/rooms/{roomId}`, send to `/app/chat/rooms/{roomId}` — both require room membership.

### Failure-mode demos

- Stop Elasticsearch → keyword search degrades to PostgreSQL LIKE (`degraded=true`, counter `app.search.degraded`).
- Remove Gemini API key → semantic search/recommendations return deterministic fallback (`app.ai.fallback`).
- Replay a VNPay webhook → idempotent no-op (`app.payment.webhook.duplicate`).
- Scheduler throughput: `app.scheduler.processed{scheduler=checkout-timeout|payment-timeout}`.
- Metrics at `/actuator/prometheus` (ADMIN role), health at `/actuator/health` (public).

### OpenAPI

Swagger UI at `/swagger-ui.html`, grouped by module: `auth-user`, `catalog-search`,
`cart-order-payment`, `review-notification-chat`.
