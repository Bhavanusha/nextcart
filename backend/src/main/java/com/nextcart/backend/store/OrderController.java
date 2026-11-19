package com.nextcart.backend.store;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/store")
@CrossOrigin(origins = StoreAccess.ORIGIN, allowCredentials = "true")
public class OrderController {
    private static final String ORDER_SELECT = """
            SELECT o.id, o.order_number, COALESCE(o.placed_at, o.created_at), o.order_status, o.payment_status,
                o.currency_code, o.subtotal, o.shipping_amount, o.tax_amount, o.discount_amount, o.total_amount,
                o.shipping_recipient_name, o.shipping_phone, o.shipping_address_line1, o.shipping_address_line2,
                o.shipping_city, o.shipping_state_or_province, o.shipping_postal_code, o.shipping_country,
                o.tracking_number, o.delivery_method,
                (SELECT COALESCE(p.payment_provider, p.payment_method) FROM payments p WHERE p.order_id = o.id ORDER BY p.id DESC LIMIT 1),
                (SELECT SUM(oi.quantity) FROM order_items oi WHERE oi.order_id = o.id),
                (SELECT oi.product_name FROM order_items oi WHERE oi.order_id = o.id ORDER BY oi.id LIMIT 1),
                o.estimated_delivery_from, o.estimated_delivery_to
            FROM orders o
            """;
    private final JdbcTemplate jdbc;
    private final CartService carts;
    private final AccountController account;
    private final PayHere payHere;

    public OrderController(JdbcTemplate jdbc, CartService carts, AccountController account, PayHere payHere) {
        this.jdbc = jdbc; this.carts = carts; this.account = account; this.payHere = payHere;
    }

    @GetMapping("/checkout")
    public CheckoutQuote quote(HttpSession session) {
        long user = StoreAccess.customer(jdbc, session);
        var quote = carts.quote(user, false, false);
        return new CheckoutQuote(quote, payHere.enabled(quote.currency()), account.addresses(user));
    }

    @PostMapping("/checkout")
    @Transactional
    public Placed place(@Valid @RequestBody CheckoutInput input, HttpSession session) {
        long user = StoreAccess.customer(jdbc, session);
        StoreAccess.lockCustomer(jdbc, user);
        Order existing = jdbc.query(ORDER_SELECT + " WHERE o.user_id = ? AND o.checkout_key = ?",
                rs -> rs.next() ? order(rs) : null, user, input.key());
        if (existing != null) {
            PayHere.Checkout payment = "PayHere".equals(existing.method()) && "PENDING".equals(existing.paymentStatus())
                    && !"CANCELLED".equals(existing.status()) && payHere.enabled(existing.currency())
                    ? payHere.create(existing.number(), existing.total(), existing.currency(), account.profile(user), addressInput(existing.address()))
                    : null;
            return new Placed(existing.number(), payment);
        }
        var address = input.address();
        AccountController.requirePhone(address.phone());
        carts.lockProducts(user);
        var quote = carts.quote(user, true, true);
        if (!quote.deliveryAvailable()) throw StoreAccess.error(HttpStatus.CONFLICT, "Delivery is currently unavailable.");
        if (quote.total().compareTo(input.total()) != 0 || !quote.currency().equals(input.currency())) {
            throw StoreAccess.error(HttpStatus.CONFLICT, "Your order total changed. Review the updated total.");
        }
        boolean card = "PAYHERE".equals(input.method());
        if (card ? (!payHere.enabled(quote.currency()) || quote.total().signum() <= 0)
                : !("COD".equals(input.method()) && quote.cashOnDelivery())) {
            throw StoreAccess.error(HttpStatus.BAD_REQUEST, "Choose an available payment method.");
        }
        String number = "HAV-" + java.time.Year.now(ZoneOffset.UTC).getValue() + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(java.util.Locale.ROOT);
        String status = card ? "PENDING" : "CONFIRMED";
        long id = jdbc.queryForObject("""
                INSERT INTO orders(order_number, user_id, coupon_id, subtotal, discount_amount, shipping_amount,
                   tax_amount, total_amount, currency_code, order_status, shipping_recipient_name, shipping_phone,
                   shipping_address_line1, shipping_address_line2, shipping_city, shipping_state_or_province,
                   shipping_postal_code, shipping_country, delivery_method, placed_at, checkout_key)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'Standard', CURRENT_TIMESTAMP, ?) RETURNING id
                """, Long.class, number, user, quote.couponId(), quote.subtotal(), quote.discount(), quote.shipping(), quote.tax(),
                quote.total(), quote.currency(), status, address.name(), address.phone(), address.line1(), address.line2(),
                address.city(), address.state(), address.postal(), address.country(), input.key());
        jdbc.update("""
                UPDATE orders SET
                    estimated_delivery_from = (CURRENT_TIMESTAMP AT TIME ZONE s.timezone)::date + (s.shipping_config->>'deliveryMinDays')::integer,
                    estimated_delivery_to = (CURRENT_TIMESTAMP AT TIME ZONE s.timezone)::date + (s.shipping_config->>'deliveryMaxDays')::integer
                FROM store_settings s WHERE s.id = 1 AND orders.id = ?
                """, id);
        for (var line : quote.lines()) {
            jdbc.update("""
                    INSERT INTO order_items(order_id, product_id, product_name, product_sku, quantity, unit_price,
                        subtotal, variant_label, image_snapshot) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, id, line.productId(), line.name(), line.sku(), line.quantity(), line.price(), line.total(), line.variant(), line.image());
        }
        OrderStock.adjust(jdbc, id, false);
        jdbc.update("""
                INSERT INTO payments(order_id, payment_reference, payment_method, payment_provider, amount, currency_code)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, number, card ? "ONLINE" : "Cash on delivery", card ? "PayHere" : null, quote.total(), quote.currency());
        if (quote.couponId() != null) jdbc.update("UPDATE coupons SET used_count = used_count + 1 WHERE id = ?", quote.couponId());
        if (!card) history(id, "PENDING", "CONFIRMED");
        if (Boolean.TRUE.equals(input.saveAddress())) account.saveAddress(user, address);
        jdbc.update("DELETE FROM cart_items WHERE cart_id IN (SELECT id FROM carts WHERE user_id = ?)", user);
        jdbc.update("UPDATE carts SET coupon_code = NULL WHERE user_id = ?", user);
        return new Placed(number, card ? payHere.create(number, quote.total(), quote.currency(), account.profile(user), address) : null);
    }

    @PostMapping(value = "/payhere/notify", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Transactional
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void notify(@RequestParam Map<String, String> fields) {
        if (!payHere.validNotification(fields)) throw StoreAccess.error(HttpStatus.BAD_REQUEST, "Invalid payment notification.");
        String number = fields.get("order_id");
        var payment = jdbc.query("""
                SELECT o.id, o.total_amount, o.currency_code, o.order_status, o.payment_status, o.coupon_id, p.id
                FROM orders o JOIN payments p ON p.order_id = o.id WHERE o.order_number = ? AND p.payment_provider = 'PayHere'
                FOR UPDATE OF o, p
                """, rs -> rs.next() ? new PaymentState(rs.getLong(1), rs.getBigDecimal(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getObject(6, Long.class), rs.getLong(7)) : null, number);
        if (payment == null || !payment.currency().equals(fields.get("payhere_currency"))
                || payment.total().compareTo(new BigDecimal(fields.get("payhere_amount"))) != 0) {
            throw StoreAccess.error(HttpStatus.BAD_REQUEST, "Payment does not match the order.");
        }
        String code = fields.get("status_code");
        if ("0".equals(code) || "REFUNDED".equals(payment.paymentStatus())) return;
        if ("2".equals(code) && !"PAID".equals(payment.paymentStatus())) {
            String reference = fields.get("payment_id");
            if (reference == null || !reference.matches("[a-zA-Z0-9_-]{1,100}")) {
                throw StoreAccess.error(HttpStatus.BAD_REQUEST, "Missing payment reference.");
            }
            jdbc.update("""
                    UPDATE payments SET payment_status = 'PAID', transaction_id = ?, paid_at = CURRENT_TIMESTAMP,
                        updated_at = CURRENT_TIMESTAMP WHERE id = ?
                    """, reference, payment.paymentId());
            boolean confirm = "PENDING".equals(payment.status());
            jdbc.update("""
                    UPDATE orders SET payment_status = 'PAID',
                        order_status = CASE WHEN order_status = 'PENDING' THEN 'CONFIRMED' ELSE order_status END,
                        updated_at = CURRENT_TIMESTAMP WHERE id = ?
                    """, payment.id());
            if (confirm) history(payment.id(), "PENDING", "CONFIRMED");
        } else if (("-1".equals(code) || "-2".equals(code)) && "PENDING".equals(payment.paymentStatus())) {
            boolean release = "PENDING".equals(payment.status());
            if (release) {
                OrderStock.restore(jdbc, payment.id(), payment.couponId());
                history(payment.id(), payment.status(), "CANCELLED");
            }
            jdbc.update("UPDATE payments SET payment_status = 'FAILED', updated_at = CURRENT_TIMESTAMP WHERE id = ?", payment.paymentId());
            jdbc.update("""
                    UPDATE orders SET payment_status = 'FAILED', order_status = CASE WHEN ? THEN 'CANCELLED' ELSE order_status END,
                        cancelled_at = CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE cancelled_at END, updated_at = CURRENT_TIMESTAMP WHERE id = ?
                    """, release, release, payment.id());
        } else if ("-3".equals(code) && "PAID".equals(payment.paymentStatus())) {
            jdbc.update("UPDATE payments SET payment_status = 'REFUNDED', updated_at = CURRENT_TIMESTAMP WHERE id = ?", payment.paymentId());
            jdbc.update("UPDATE orders SET payment_status = 'REFUNDED', updated_at = CURRENT_TIMESTAMP WHERE id = ?", payment.id());
        }
    }

    private void history(long id, String from, String to) {
        jdbc.update("INSERT INTO order_status_history(order_id, previous_status, new_status) VALUES (?, ?, ?)", id, from, to);
    }

    @GetMapping("/orders")
    public Orders orders(@RequestParam(defaultValue = "") String status, @RequestParam(defaultValue = "1") int page,
            HttpSession session) {
        long user = StoreAccess.customer(jdbc, session);
        if (page < 1 || page > 10000) throw StoreAccess.error(HttpStatus.BAD_REQUEST, "Invalid page.");
        var counts = jdbc.query("SELECT order_status, COUNT(*) FROM orders WHERE user_id = ? GROUP BY order_status", rs -> {
            var result = new java.util.LinkedHashMap<String, Long>();
            while (rs.next()) result.put(rs.getString(1), rs.getLong(2));
            return result;
        }, user);
        String filter = "PROCESSING".equals(status) ? " AND o.order_status IN ('PENDING', 'CONFIRMED', 'PROCESSING')"
                : status.isEmpty() ? "" : " AND o.order_status = ?";
        int size = 5;
        Object[] arguments = filter.endsWith("?") ? new Object[] {user, status, size, (page - 1) * size}
                : new Object[] {user, size, (page - 1) * size};
        return new Orders(jdbc.query(ORDER_SELECT + " WHERE o.user_id = ?" + filter + " ORDER BY o.created_at DESC, o.id DESC LIMIT ? OFFSET ?",
                (rs, row) -> order(rs), arguments), counts, size);
    }

    @GetMapping("/orders/{number}")
    public OrderDetail detail(@PathVariable String number, HttpSession session) {
        long user = StoreAccess.customer(jdbc, session);
        Order order = jdbc.query(ORDER_SELECT + " WHERE o.user_id = ? AND o.order_number = ?",
                rs -> rs.next() ? order(rs) : null, user, number);
        if (order == null) throw StoreAccess.error(HttpStatus.NOT_FOUND, "Order not found.");
        var items = jdbc.query("""
                SELECT id, product_name, variant_label, quantity, subtotal, image_snapshot FROM order_items WHERE order_id = ? ORDER BY id
                """, (rs, row) -> new OrderItem(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getInt(4), rs.getBigDecimal(5), rs.getString(6)), order.id());
        var steps = jdbc.query("""
                SELECT new_status, min(created_at) FROM order_status_history WHERE order_id = ? GROUP BY new_status ORDER BY 2
                """, (rs, row) -> new Step(rs.getString(1), rs.getObject(2, OffsetDateTime.class)), order.id());
        return new OrderDetail(order, items, steps);
    }

    private Order order(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Order(rs.getLong(1), rs.getString(2), rs.getObject(3, OffsetDateTime.class), rs.getString(4), rs.getString(5),
                rs.getString(6), rs.getBigDecimal(7), rs.getBigDecimal(8), rs.getBigDecimal(9), rs.getBigDecimal(10), rs.getBigDecimal(11),
                new Destination(rs.getString(12), rs.getString(13), rs.getString(14), rs.getString(15), rs.getString(16),
                        rs.getString(17), rs.getString(18), rs.getString(19)), rs.getString(20), rs.getString(21),
                rs.getString(22), rs.getInt(23), rs.getString(24), rs.getObject(25, LocalDate.class), rs.getObject(26, LocalDate.class));
    }

    private AccountController.AddressInput addressInput(Destination d) {
        return new AccountController.AddressInput("", d.name(), d.phone(), d.line1(), d.line2(), d.city(), d.state(), d.postal(), d.country());
    }

    record CheckoutInput(@NotNull UUID key, @Valid @NotNull AccountController.AddressInput address, Boolean saveAddress,
            @NotNull String method, @NotNull String currency, @NotNull BigDecimal total) {}
    record CheckoutQuote(CartService.Quote quote, boolean payHere, List<AccountController.Address> addresses) {}
    record Placed(String number, PayHere.Checkout payment) {}
    record PaymentState(long id, BigDecimal total, String currency, String status, String paymentStatus, Long couponId, long paymentId) {}
    record Destination(String name, String phone, String line1, String line2, String city, String state, String postal, String country) {}
    record Order(long id, String number, OffsetDateTime date, String status, String paymentStatus, String currency,
            BigDecimal subtotal, BigDecimal shipping, BigDecimal tax, BigDecimal discount, BigDecimal total, Destination address,
            String tracking, String delivery, String method, int count, String firstItem, LocalDate estimatedFrom, LocalDate estimatedTo) {}
    record OrderItem(long id, String name, String variant, int quantity, BigDecimal total, String image) {}
    record Step(String status, OffsetDateTime date) {}
    record OrderDetail(Order order, List<OrderItem> items, List<Step> steps) {}
    record Orders(List<Order> orders, Map<String, Long> counts, int size) {}
}
