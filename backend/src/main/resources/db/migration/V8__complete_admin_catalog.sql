
ALTER TABLE products ADD COLUMN catalog_status VARCHAR(12);
UPDATE products SET catalog_status = CASE WHEN is_active THEN 'ACTIVE' ELSE 'ARCHIVED' END;
ALTER TABLE products ADD CONSTRAINT ck_products_catalog_status
    CHECK (catalog_status IN ('ACTIVE', 'DRAFT', 'ARCHIVED'));

CREATE TABLE product_variants (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    color VARCHAR(100),
    size VARCHAR(100),
    sku_override VARCHAR(100) UNIQUE,
    price NUMERIC(12, 2) CHECK (price >= 0)
);
CREATE INDEX ix_product_variants_product_id ON product_variants(product_id);

ALTER TABLE product_images ADD COLUMN image_content BYTEA;
ALTER TABLE product_images ADD COLUMN content_type VARCHAR(20);
ALTER TABLE store_settings ADD COLUMN logo_content BYTEA;
ALTER TABLE store_settings ADD COLUMN logo_content_type VARCHAR(20);
