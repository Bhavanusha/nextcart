ALTER TABLE addresses ADD COLUMN label VARCHAR(40) NOT NULL DEFAULT 'Home';
ALTER TABLE users ADD COLUMN avatar_data BYTEA, ADD COLUMN avatar_type VARCHAR(20);
ALTER TABLE carts ADD COLUMN coupon_code VARCHAR(50);
ALTER TABLE cart_items DROP CONSTRAINT uq_cart_items_product;
ALTER TABLE cart_items ADD COLUMN variant_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE cart_items ADD CONSTRAINT uq_cart_variant UNIQUE (cart_id, product_id, variant_id);

ALTER TABLE orders ADD COLUMN checkout_key UUID,
    ADD COLUMN tax_amount NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (tax_amount >= 0),
    ADD COLUMN delivery_method VARCHAR(50);
CREATE UNIQUE INDEX ux_orders_checkout_key ON orders (user_id, checkout_key) WHERE checkout_key IS NOT NULL;
ALTER TABLE order_items ADD COLUMN image_snapshot VARCHAR(2048);

CREATE TABLE newsletter_subscriptions (
    email VARCHAR(320) PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE store_settings ADD COLUMN storefront_content JSONB NOT NULL DEFAULT '{}'::jsonb;
UPDATE store_settings SET storefront_content = '{
    "heroTitle":"Designed for",
    "heroAccent":"Everyday Living",
    "heroDescription":"Premium essentials crafted with intention and care. Elevate your everyday rituals with products built to last.",
    "heroImage":"/auth-interior.png",
    "storyTitle":"Objects made with patience and respect",
    "storyDescription":"Discover everyday essentials that combine form, function, and considered design.",
    "storyImage":"/auth-interior.png"
}'::jsonb WHERE id = 1;
