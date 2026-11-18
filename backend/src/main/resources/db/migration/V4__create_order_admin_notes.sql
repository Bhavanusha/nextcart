CREATE TABLE order_admin_notes (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    created_by_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    note TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_order_admin_notes_order_id ON order_admin_notes (order_id);
