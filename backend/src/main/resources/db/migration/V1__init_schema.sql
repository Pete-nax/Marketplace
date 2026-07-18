-- Users
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(255) NOT NULL,
    role            VARCHAR(20)  NOT NULL DEFAULT 'CUSTOMER' CHECK (role IN ('CUSTOMER', 'ADMIN')),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Categories
CREATE TABLE categories (
    id      BIGSERIAL PRIMARY KEY,
    name    VARCHAR(100) NOT NULL UNIQUE,
    slug    VARCHAR(100) NOT NULL UNIQUE
);

-- Products
CREATE TABLE products (
    id              BIGSERIAL PRIMARY KEY,
    category_id     BIGINT NOT NULL REFERENCES categories(id),
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    sku             VARCHAR(64) NOT NULL UNIQUE,
    price_cents     BIGINT NOT NULL CHECK (price_cents >= 0),
    stock_quantity  INTEGER NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),
    image_url       VARCHAR(500),
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_active ON products(active);

-- Cart items (one row per user+product)
CREATE TABLE cart_items (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id  BIGINT NOT NULL REFERENCES products(id),
    quantity    INTEGER NOT NULL CHECK (quantity > 0),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, product_id)
);

-- Orders
CREATE TABLE orders (
    id                          BIGSERIAL PRIMARY KEY,
    user_id                     BIGINT NOT NULL REFERENCES users(id),
    status                      VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                                    CHECK (status IN ('PENDING','PAID','FAILED','SHIPPED','DELIVERED','CANCELLED')),
    total_amount_cents          BIGINT NOT NULL CHECK (total_amount_cents >= 0),
    currency                    VARCHAR(3) NOT NULL DEFAULT 'usd',
    stripe_payment_intent_id    VARCHAR(255) UNIQUE,
    shipping_address            TEXT,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_orders_user ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(status);

-- Order line items — price snapshotted at purchase time, never joins live product price
CREATE TABLE order_items (
    id                  BIGSERIAL PRIMARY KEY,
    order_id            BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id          BIGINT NOT NULL REFERENCES products(id),
    product_name_snapshot VARCHAR(255) NOT NULL,
    unit_price_cents    BIGINT NOT NULL CHECK (unit_price_cents >= 0),
    quantity            INTEGER NOT NULL CHECK (quantity > 0)
);
CREATE INDEX idx_order_items_order ON order_items(order_id);

-- Payments — one row per provider event we've processed (idempotency for webhooks)
CREATE TABLE payments (
    id                          BIGSERIAL PRIMARY KEY,
    order_id                    BIGINT NOT NULL REFERENCES orders(id),
    provider                    VARCHAR(20) NOT NULL DEFAULT 'STRIPE',
    provider_payment_id         VARCHAR(255) NOT NULL,
    status                      VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','SUCCEEDED','FAILED','REFUNDED')),
    amount_cents                BIGINT NOT NULL,
    raw_event_id                VARCHAR(255) UNIQUE,  -- Stripe event id, prevents double-processing webhooks
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_payments_order ON payments(order_id);
