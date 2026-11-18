ALTER TABLE products
    ALTER COLUMN currency_code SET DEFAULT 'USD';

ALTER TABLE orders
    ALTER COLUMN currency_code SET DEFAULT 'USD';

ALTER TABLE payments
    ALTER COLUMN currency_code SET DEFAULT 'USD';

ALTER TABLE store_settings
    ALTER COLUMN currency_code SET DEFAULT 'USD',
    ALTER COLUMN tax_rate SET DEFAULT 0;

UPDATE store_settings
SET
    store_name = 'HAVEN',
    contact_email = 'hello@haven.com',
    contact_phone = '+1 (503) 555-0100',
    address_line1 = '1248 Pine Street',
    city = 'Portland',
    state_or_province = 'Oregon',
    postal_code = '97201',
    country = 'United States',
    currency_code = 'USD',
    tax_rate = 8.25,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 1;
