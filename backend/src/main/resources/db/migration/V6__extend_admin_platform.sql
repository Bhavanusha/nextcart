ALTER TABLE products
    ADD COLUMN IF NOT EXISTS compare_at_price NUMERIC(12, 2) CHECK (compare_at_price IS NULL OR compare_at_price >= 0),
    ADD COLUMN IF NOT EXISTS low_stock_threshold INTEGER NOT NULL DEFAULT 5 CHECK (low_stock_threshold >= 0),
    ADD COLUMN IF NOT EXISTS meta_title VARCHAR(255),
    ADD COLUMN IF NOT EXISTS meta_description VARCHAR(500),
    ADD COLUMN IF NOT EXISTS canonical_url VARCHAR(2048),
    ADD COLUMN IF NOT EXISTS barcode VARCHAR(100),
    ADD COLUMN IF NOT EXISTS weight NUMERIC(12, 3),
    ADD COLUMN IF NOT EXISTS tax_class VARCHAR(50);

ALTER TABLE categories
    ADD COLUMN IF NOT EXISTS meta_title VARCHAR(255),
    ADD COLUMN IF NOT EXISTS meta_description VARCHAR(500),
    ADD COLUMN IF NOT EXISTS sort_order INTEGER NOT NULL DEFAULT 0 CHECK (sort_order >= 0);

ALTER TABLE inventory_transactions
    ADD COLUMN IF NOT EXISTS adjustment_reason VARCHAR(50);

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS reserved_quantity INTEGER NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    ADD COLUMN IF NOT EXISTS reorder_point INTEGER NOT NULL DEFAULT 5 CHECK (reorder_point >= 0);

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS shipping_carrier VARCHAR(100),
    ADD COLUMN IF NOT EXISTS tracking_number VARCHAR(255),
    ADD COLUMN IF NOT EXISTS tracking_url VARCHAR(2048),
    ADD COLUMN IF NOT EXISTS shipped_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS cancellation_reason TEXT;

CREATE TABLE IF NOT EXISTS order_status_history (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    previous_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    actor_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_order_status_history_order_id
    ON order_status_history(order_id, created_at DESC);

ALTER TABLE reviews
    ADD COLUMN IF NOT EXISTS is_hidden BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS moderation_reason TEXT,
    ADD COLUMN IF NOT EXISTS moderated_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS moderated_at TIMESTAMPTZ;

ALTER TABLE store_settings
    ADD COLUMN IF NOT EXISTS timezone VARCHAR(100) NOT NULL DEFAULT 'UTC',
    ADD COLUMN IF NOT EXISTS locale VARCHAR(20) NOT NULL DEFAULT 'en-US',
    ADD COLUMN IF NOT EXISTS logo_url VARCHAR(2048),
    ADD COLUMN IF NOT EXISTS shipping_config JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS payment_config JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS notification_config JSONB NOT NULL DEFAULT '{}'::jsonb;
