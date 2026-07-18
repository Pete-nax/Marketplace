<div align="center">

# 🛒 Marketplace — Full-Stack E-Commerce Platform

**An e-commerce app with real payment processing, race-condition safe inventory, and webhook-verified checkout.**

[![Java](https://img.shields.io/badge/Java-17-orange)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen)](https://spring.io/projects/spring-boot)
[![Stripe](https://img.shields.io/badge/Payments-Stripe-635BFF)](https://stripe.com)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)


</div>


## What this is

A Spring Boot API + vanilla JS frontend covering the full order lifecycle: browse, cart, checkout, **real Stripe payment confirmed via webhook**, admin fulfillment with the specific engineering problems that come with that scope actually solved and tested.

## Why it's worth a look

| Problem | How it's handled |
|---|---|
| **Overselling under concurrent checkout** | Stock decrements via a single atomic `UPDATE ... WHERE stock_quantity >= ?` — not a read-then-write in app code. [Proven with a real test](backend/src/test/java/com/ecommerce/api/repository/ProductStockConcurrencyTest.java) that fires 10 concurrent threads at the last unit and asserts exactly one wins. |
| **Trusting client-side "payment succeeded"** | Orders stay `PENDING` until Stripe's webhook confirms `payment_intent.succeeded` server-to-server: the client redirect is never treated as proof of payment. |
| **Duplicate webhook delivery** | Stripe retries webhooks on timeout. Each event's ID is stored with a unique DB constraint and checked before processing, so a redelivered event is a safe no-op. |
| **Price/name changes rewriting order history** | Order line items snapshot `productNameSnapshot` and `unitPriceCents` at purchase time: a later price change never silently alters a past order's total. |
| **Money as floating point** | All prices are integer cents (`priceCents`, `totalAmountCents`) end to end, frontend and backend, to avoid rounding drift. |

## Tech stack

**Backend** — Java 17 · Spring Boot 3.3 · Spring Security (JWT) · PostgreSQL · Flyway · Redis · Stripe API · Testcontainers

**Frontend** — HTML / CSS / vanilla JS · Tailwind · Stripe Elements

**Infra** — Docker · GitHub Actions CI · deployed on Render (backend) + Netlify (frontend) + Neon (Postgres) + Upstash (Redis)

## Architecture

```
┌─────────────┐        REST + JWT         ┌──────────────────┐
│   Frontend   │ ────────────────────────▶ │   Spring Boot     │
│  (Netlify)   │                            │   API (Render)    │
└─────────────┘                            └──────────┬────────┘
                                                        │
                              ┌─────────────────────────┼─────────────────────────┐
                              ▼                         ▼                         ▼
                       ┌────────────┐            ┌────────────┐            ┌────────────┐
                       │ PostgreSQL │            │   Redis    │            │   Stripe   │
                       │   (Neon,   │            │ (Upstash,  │            │ (payments +│
                       │Flyway-mgd) │            │  sessions) │            │  webhooks) │
                       └────────────┘            └────────────┘            └─────┬──────┘
                                                                                   │
                                                        webhook: payment_intent.succeeded
                                                                                   │
                                                                                   ▼
                                                                    order flips PENDING → PAID
```

## Core features

- **Catalog & search**: categories, stock-aware product cards, price/category filtering
- **Auth**: JWT access + refresh tokens, BCrypt password hashing, role-based access (`CUSTOMER` / `ADMIN`)
- **Cart**: server-side, persisted per user, quantity capped live against real stock
- **Checkout**: atomic stock reservation → Stripe PaymentIntent → Stripe Elements → webhook-confirmed order
- **Order history**: customer-facing order tracking with status badges
- **Admin dashboard**: product CRUD, stock management, order overview, revenue stats
- **Email**: order confirmation on successful payment

## Project structure

```
ecommerce-app/
├── backend/            Spring Boot API
│   ├── src/main/java/com/ecommerce/api/
│   │   ├── entity/          JPA entities
│   │   ├── repository/      Spring Data repositories (see ProductRepository for the atomic decrement query)
│   │   ├── service/         Business logic — OrderService.checkout() is the core flow
│   │   ├── controller/      REST endpoints
│   │   ├── security/        JWT filter, UserDetails, token service
│   │   └── config/          Security, Stripe config
│   ├── src/main/resources/db/migration/   Flyway schema
│   └── src/test/            Includes the concurrency test proving no-oversell
├── frontend/           Static HTML/CSS/JS, claymorphic UI
│   ├── index.html, product.html, checkout.html, ...
│   └── js/api.js            Typed-ish fetch client for the whole API surface
└── .github/workflows/  CI: backend tests, frontend guardrails, optional gated deploy
```

## Getting started

Full setup instructions live in each part's own README — [`backend/README.md`](backend/README.md) and [`frontend/README.md`](frontend/README.md).

```bash
# Backend
cd backend
cp .env.example .env   # fill in Stripe test keys
docker run -d -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=ecommerce -p 5432:5432 postgres:16
docker run -d -p 6379:6379 redis:7
mvn spring-boot:run

# Frontend (separate terminal)
cd frontend
# edit js/config.js with your backend URL + Stripe publishable key
npx serve .
```

## Testing

```bash
cd backend && mvn verify   # requires Docker running — Testcontainers spins up real Postgres per test
```

Backend tests run automatically in CI on every push/PR via `.github/workflows/backend-ci.yml`.

## What I'd build next

- Refresh-token rotation with server-side revocation (currently just expires)
- M-Pesa as a second payment provider alongside Stripe, sharing the same `Payment` model
- A scheduled job to release stock reserved by abandoned `PENDING` orders
- Product image upload instead of placeholder imagery

## License

MIT — see [LICENSE](LICENSE).
