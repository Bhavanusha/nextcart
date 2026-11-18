
ALTER TABLE orders ADD COLUMN shipping_state_or_province VARCHAR(100);
ALTER TABLE order_items ADD COLUMN variant_label VARCHAR(200);
