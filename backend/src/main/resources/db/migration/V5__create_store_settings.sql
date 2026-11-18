CREATE TABLE store_settings (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    store_name VARCHAR(150) NOT NULL,
    contact_email VARCHAR(320) NOT NULL,
    contact_phone VARCHAR(30),
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    city VARCHAR(100),
    state_or_province VARCHAR(100),
    postal_code VARCHAR(20),
    country VARCHAR(100),
    currency_code CHAR(3) NOT NULL DEFAULT 'INR',
    tax_rate NUMERIC(5, 2) NOT NULL DEFAULT 0 CHECK (tax_rate >= 0 AND tax_rate <= 100),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO store_settings (id, store_name, contact_email, currency_code)
VALUES (1, 'NextCart', 'admin@nextcart.local', 'INR');
