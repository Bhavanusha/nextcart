package com.nextcart.backend.store;

import org.springframework.jdbc.core.JdbcTemplate;

public final class OrderStock {
    private OrderStock() {}

    public static void adjust(JdbcTemplate jdbc, long orderId, boolean restore) {
        var demand = jdbc.query("""
                SELECT product_id, SUM(quantity)::integer AS quantity FROM order_items
                    WHERE order_id = ? AND product_id IS NOT NULL GROUP BY product_id ORDER BY product_id
                """, (rs, row) -> new Demand(rs.getLong(1), rs.getInt(2)), orderId);
        for (var item : demand) {
            int before = jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id = ? FOR UPDATE", Integer.class, item.id());
            int after = restore ? Math.addExact(before, item.quantity()) : before - item.quantity();
            jdbc.update("UPDATE products SET stock_quantity = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", after, item.id());
            jdbc.update("""
                    INSERT INTO inventory_transactions(product_id, transaction_type, quantity, previous_stock, new_stock, reference_type, reference_id)
                    VALUES (?, ?, ?, ?, ?, 'ORDER', ?)
                    """, item.id(), restore ? "ORDER_RELEASE" : "ORDER_SALE", item.quantity(), before, after, orderId);
        }
    }

    public static void restore(JdbcTemplate jdbc, long orderId, Long couponId) {
        adjust(jdbc, orderId, true);
        if (couponId != null) {
            jdbc.update("UPDATE coupons SET used_count = GREATEST(used_count - 1, 0) WHERE id = ?", couponId);
        }
    }

    private record Demand(long id, int quantity) {}
}
