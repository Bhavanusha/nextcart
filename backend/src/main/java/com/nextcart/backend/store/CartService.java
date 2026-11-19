package com.nextcart.backend.store;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
class CartService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private final JdbcTemplate jdbc;

    CartService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    void ensure(long user) {
        jdbc.update("INSERT INTO carts(user_id) VALUES (?) ON CONFLICT DO NOTHING", user);
    }

    private static final String LINES = CatalogController.CATEGORY_TREE + """
            SELECT ci.id, ci.product_id, ci.variant_id, p.slug, p.name,
                COALESCE(v.sku_override, p.sku) AS sku, NULLIF(concat_ws(' / ', v.color, v.size), '') AS variant,
                ci.quantity, COALESCE(v.price, p.price) AS price, p.currency_code, GREATEST(p.stock_quantity - p.reserved_quantity, 0) AS available,
                (
            """ + CatalogController.PUBLISHED + """
             ) AND CASE WHEN ci.variant_id <> 0 THEN v.id IS NOT NULL ELSE
                 NOT EXISTS (SELECT 1 FROM product_variants existing WHERE existing.product_id = p.id) END AS purchasable,
            """ + CatalogController.IMAGE + """
             AS image
            FROM carts ca JOIN cart_items ci ON ci.cart_id = ca.id JOIN products p ON p.id = ci.product_id
            LEFT JOIN visible_categories c ON c.id = p.category_id
            LEFT JOIN product_variants v ON v.id = ci.variant_id AND v.product_id = p.id
            WHERE ca.user_id = ? ORDER BY ci.id
            """;

    Quote quote(long user, boolean validate, boolean lockCoupon) {
        Config config = jdbc.queryForObject("""
                SELECT currency_code, tax_rate, (shipping_config->>'flatRate')::numeric,
                    (shipping_config->>'freeShippingMinimum')::numeric,
                    COALESCE((payment_config->>'cashOnDelivery')::boolean, FALSE)
                FROM store_settings WHERE id = 1
                """, (rs, row) -> new Config(rs.getString(1), rs.getBigDecimal(2), rs.getBigDecimal(3),
                    rs.getBigDecimal(4), rs.getBoolean(5)));
        var lines = jdbc.query(LINES, (rs, row) -> new Line(rs.getLong("id"), rs.getLong("product_id"),
                rs.getLong("variant_id"), rs.getString("slug"), rs.getString("name"), rs.getString("sku"),
                rs.getString("variant"), rs.getInt("quantity"), rs.getBigDecimal("price"), rs.getString("currency_code"),
                rs.getInt("available"), rs.getBoolean("purchasable"), rs.getString("image")), user);
        var quantities = new java.util.HashMap<Long, Long>();
        for (Line line : lines) quantities.merge(line.productId(), (long) line.quantity(), Long::sum);
        boolean available = lines.stream().allMatch(line -> line.purchasable() && line.currency().equals(config.currency())
                && quantities.get(line.productId()) <= line.available());
        if (validate && (!available || lines.isEmpty())) {
            throw StoreAccess.error(HttpStatus.CONFLICT, "Review the availability of the items in your bag.");
        }
        BigDecimal subtotal = lines.stream().filter(line -> line.currency().equals(config.currency()))
                .map(Line::total).reduce(ZERO, BigDecimal::add);
        String code = jdbc.query("SELECT coupon_code FROM carts WHERE user_id = ?", rs -> rs.next() ? rs.getString(1) : null, user);
        Discount coupon = discount(code, subtotal, lockCoupon);
        if (validate && code != null && coupon == null) {
            throw StoreAccess.error(HttpStatus.CONFLICT, "This promo code is no longer valid. Remove it to continue.");
        }
        BigDecimal discount = coupon == null ? ZERO : coupon.amount();
        BigDecimal net = subtotal.subtract(discount);
        boolean free = config.freeMinimum() != null && subtotal.compareTo(config.freeMinimum()) >= 0;
        BigDecimal shipping = lines.isEmpty() || free || config.shippingRate() == null ? ZERO : config.shippingRate();
        BigDecimal tax = net.multiply(config.taxRate()).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
        boolean deliveryAvailable = free || config.shippingRate() != null;
        return new Quote(lines, config.currency(), subtotal, shipping, tax, discount, net.add(tax).add(shipping),
                code, coupon == null ? null : coupon.id(), code != null && coupon == null, available && !lines.isEmpty(),
                deliveryAvailable, config.cashOnDelivery());
    }

    void lockProducts(long user) {
        jdbc.queryForList("""
                SELECT p.id FROM products p WHERE p.id IN (
                    SELECT ci.product_id FROM cart_items ci JOIN carts c ON c.id = ci.cart_id WHERE c.user_id = ?
                ) ORDER BY p.id FOR UPDATE
                """, Long.class, user);
    }

    boolean activeVariant(long product, long variant) {
        return Boolean.TRUE.equals(jdbc.query(CatalogController.CATEGORY_TREE + """
                SELECT CASE WHEN ? = 0 THEN NOT EXISTS (SELECT 1 FROM product_variants WHERE product_id = p.id)
                    ELSE EXISTS (SELECT 1 FROM product_variants WHERE product_id = p.id AND id = ?) END
                FROM products p LEFT JOIN visible_categories c ON p.category_id = c.id WHERE p.id = ? AND
                """ + CatalogController.PUBLISHED, rs -> rs.next() && rs.getBoolean(1), variant, variant, product));
    }

    private Discount discount(String code, BigDecimal subtotal, boolean lock) {
        if (code == null) return null;
        String base = """
                SELECT id, discount_type, discount_value, maximum_discount FROM coupons
                WHERE code = ? AND is_active AND starts_at <= CURRENT_TIMESTAMP
                    AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP) AND minimum_order_amount <= ?
                    AND (usage_limit IS NULL OR used_count < usage_limit)
                """;
        return jdbc.query(base + (lock ? " FOR UPDATE" : ""), rs -> {
            if (!rs.next()) return null;
            BigDecimal value = rs.getBigDecimal(3);
            if ("PERCENTAGE".equals(rs.getString(2))) value = subtotal.multiply(value).movePointLeft(2);
            BigDecimal cap = rs.getBigDecimal(4);
            if (cap != null) value = value.min(cap);
            return new Discount(rs.getLong(1), value.min(subtotal).setScale(2, RoundingMode.HALF_UP));
        }, code.toUpperCase(Locale.ROOT), subtotal);
    }

    record Config(String currency, BigDecimal taxRate, BigDecimal shippingRate, BigDecimal freeMinimum, boolean cashOnDelivery) {}
    record Discount(long id, BigDecimal amount) {}
    record Line(long id, long productId, long variantId, String slug, String name, String sku, String variant,
            int quantity, BigDecimal price, String currency, int available, boolean purchasable, String image) {
        public BigDecimal total() { return price.multiply(BigDecimal.valueOf(quantity)); }
        @com.fasterxml.jackson.annotation.JsonProperty("total")
        public BigDecimal subtotal() { return total(); }
    }
    record Quote(List<Line> lines, String currency, BigDecimal subtotal, BigDecimal shipping, BigDecimal tax,
            BigDecimal discount, BigDecimal total, String coupon, Long couponId, boolean invalidCoupon,
            boolean available, boolean deliveryAvailable, boolean cashOnDelivery) {}
}
