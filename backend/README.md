# E-Commerce API

Spring Boot backend for a full-stack e-commerce app. JWT auth, PostgreSQL + Flyway,
Redis, Stripe payments with webhook confirmation, and an admin API for products/orders.

## Stack
- Java 17, Spring Boot 3.3
- PostgreSQL (schema managed by Flyway migrations, not Hibernate auto-DDL)
- Spring Security + JWT (stateless)
- Stripe Payment Intents API
- Redis (wired in, ready for cart caching / rate limiting)

## Running locally

1. Start Postgres and Redis (easiest via Docker):
   ```bash
   docker run -d --name ecommerce-pg -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=ecommerce -p 5432:5432 postgres:16
   docker run -d --name ecommerce-redis -p 6379:6379 redis:7
   ```

2. Copy `.env.example` to `.env` and fill in your Stripe test keys (from
   https://dashboard.stripe.com/test/apikeys).

3. Export the env vars (or use a plugin like `spring-dotenv`, or your IDE's env file support)
   and run:
   ```bash
   mvn spring-boot:run
   ```

4. Flyway runs migrations automatically on startup. Swagger UI is at `/docs`.

5. To test the Stripe webhook locally, use the Stripe CLI:
   ```bash
   stripe listen --forward-to localhost:8080/api/payments/webhook
   ```
   This prints a `whsec_...` value — put that in `STRIPE_WEBHOOK_SECRET`.

## Running tests

```bash
mvn verify
```

Tests use [Testcontainers](https://testcontainers.com) to spin up a real,
throwaway Postgres container per test class — **Docker must be running locally**
for `mvn verify` to work. No manual database setup needed; each test gets a clean
instance and the container is destroyed afterward automatically.

`ProductStockConcurrencyTest` is worth reading directly — it fires 10 concurrent
requests at the last unit of a product's stock and asserts exactly one succeeds,
which is actual proof behind the "prevents overselling" claim below, rather than
just a description of the intended behavior.

## Architecture notes 

**Preventing overselling.** `ProductRepository.decrementStockIfAvailable` is a single
conditional `UPDATE ... WHERE stock_quantity >= ?`. Two concurrent checkouts racing for
the last unit will not both succeed — the database evaluates the WHERE clause against the
current committed row, so the second query affects 0 rows and the checkout fails cleanly
with `InsufficientStockException`. This is safer than "read stock, check in app code, then
write" which has a race window between the read and the write.

**Payment flow is asynchronous by design.** Checkout creates a `PENDING` order and a Stripe
PaymentIntent, then returns a `client_secret` to the frontend to confirm payment. The order
only flips to `PAID` when Stripe's webhook fires `payment_intent.succeeded` — never on the
client-side redirect alone, since that can be spoofed or interrupted.

**Webhook idempotency.** Stripe retries webhook delivery on timeout, so the same event can
arrive twice. `payments.raw_event_id` has a unique DB constraint and is checked before
processing, so a redelivered event is a safe no-op.

**Money as integer cents.** All prices are `BIGINT` cents, never floating point, to avoid
rounding errors compounding across cart totals and refunds.

**Price snapshotting.** `order_items.unit_price_cents` and `product_name_snapshot` are
copied at purchase time and never re-derived from the live `products` row — so a price
change or rename later doesn't rewrite history on past orders.

## What's stubbed / left for you to extend
- Refresh token rotation/storage (currently issued but not persisted or checked against a blocklist on logout)
- A scheduled job to expire/release stock for `PENDING` orders whose payment never completes
- Rate limiting on `/api/auth/**` (Redis is wired in and ready for this)
- Order status transitions for fulfillment (`SHIPPED`, `DELIVERED`) Add an admin endpoint
- Pagination/filtering params on the public product list (sort by price, filter by category)
- M-Pesa as a second payment provider, model it as a sibling to `PaymentService`, keyed by
  the same `payments.provider` column, so both providers write to the same order/payment tables

## API surface

| Method | Path | Auth |
|---|---|---|
| POST | /api/auth/register | public |
| POST | /api/auth/login | public |
| GET | /api/products | public |
| GET | /api/products/{id} | public |
| GET | /api/cart | user |
| POST | /api/cart/items | user |
| DELETE | /api/cart/items/{id} | user |
| POST | /api/orders/checkout | user |
| GET | /api/orders | user |
| GET | /api/orders/{id} | user |
| POST | /api/payments/webhook | Stripe (signature-verified) |
| POST/PUT/DELETE | /api/admin/products/** | admin |
| GET | /api/admin/orders | admin |
