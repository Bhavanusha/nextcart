package com.nextcart.backend.admin;

import com.nextcart.backend.store.OrderStock;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/orders")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class OrderAdminController {

    private static final List<String> ORDER_STATUSES = List.of(
            "PENDING", "CONFIRMED", "PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED");
    private final JdbcTemplate jdbcTemplate;

    public OrderAdminController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public OrderListResponse list(
            @RequestParam(required = false, defaultValue = "") String status,
            @RequestParam(required = false, defaultValue = "") String search,
            HttpSession session) {
        requireAdmin(session);
        String pattern = "%" + search.trim().toLowerCase() + "%";
        String normalizedStatus = status.trim().toUpperCase();
        List<OrderResponse> orders = jdbcTemplate.query("""
                SELECT o.id, o.order_number, o.shipping_recipient_name, o.created_at, o.total_amount,
                       o.payment_status, o.order_status, COALESCE(SUM(oi.quantity), 0) AS item_count,
                       u.email, o.currency_code
                FROM orders o JOIN users u ON u.id = o.user_id
                LEFT JOIN order_items oi ON oi.order_id = o.id
                WHERE (? = '' OR o.order_status = ?)
                  AND (LOWER(o.order_number) LIKE ? OR LOWER(o.shipping_recipient_name) LIKE ? OR LOWER(u.email) LIKE ?)
                GROUP BY o.id, u.email
                ORDER BY o.created_at DESC
                """, (rs, rowNum) -> new OrderResponse(
                rs.getLong("id"), rs.getString("order_number"), rs.getString("shipping_recipient_name"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getBigDecimal("total_amount"),
                rs.getString("payment_status"), rs.getString("order_status"), rs.getLong("item_count"), rs.getString("email"), rs.getString("currency_code")),
                normalizedStatus, normalizedStatus, pattern, pattern, pattern);
        Map<String, Long> counts = jdbcTemplate.query("""
                SELECT order_status, COUNT(*) AS status_count FROM orders GROUP BY order_status
                """, result -> {
            Map<String, Long> values = new java.util.LinkedHashMap<>();
            while (result.next())
                values.put(result.getString("order_status"), result.getLong("status_count"));
            return values;
        });
        return new OrderListResponse(orders, counts);
    }

    @PutMapping("/{id}/status")
    @Transactional
    public OrderResponse updateStatus(
            @PathVariable long id,
            @Valid @RequestBody StatusRequest request,
            HttpSession session) {
        long actor = AdminAccess.require(jdbcTemplate, session);
        String status = request.status().trim().toUpperCase();
        if (!ORDER_STATUSES.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid order status.");
        }
        OrderState previous = jdbcTemplate.query("""
                SELECT o.order_status, o.tracking_number, o.payment_status, o.checkout_key IS NOT NULL, o.coupon_id,
                    EXISTS (SELECT 1 FROM payments p WHERE p.order_id = o.id AND p.payment_provider = 'PayHere')
                FROM orders o WHERE o.id = ? FOR UPDATE
                """, rs -> rs.next() ? new OrderState(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getBoolean(4), rs.getObject(5, Long.class), rs.getBoolean(6)) : null, id);
        if (previous == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found.");
        if (previous.storefront() && !previous.status().equals(status)) {
            if ("CANCELLED".equals(previous.status())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A cancelled storefront order cannot be reopened.");
            }
            if (previous.payHere() && "PENDING".equals(previous.paymentStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Wait for PayHere to confirm or decline this payment.");
            }
            if ("CANCELLED".equals(status)) {
                if (List.of("SHIPPED", "DELIVERED").contains(previous.status()) || "PAID".equals(previous.paymentStatus())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Handle the return or refund before cancelling this order.");
                }
                OrderStock.restore(jdbcTemplate, id, previous.couponId());
            }
        }
        String tracking = request.trackingNumber() == null || request.trackingNumber().isBlank()
                ? null : request.trackingNumber().trim();
        boolean trackingChanged = tracking != null && !tracking.equals(previous.trackingNumber());
        if (!previous.status().equals(status) || trackingChanged) {
            jdbcTemplate.update("""
                    UPDATE orders SET order_status = ?, tracking_number = COALESCE(?, tracking_number),
                        shipped_at = CASE WHEN ? = 'SHIPPED' THEN COALESCE(shipped_at, CURRENT_TIMESTAMP) ELSE shipped_at END,
                        delivered_at = CASE WHEN ? = 'DELIVERED' THEN COALESCE(delivered_at, CURRENT_TIMESTAMP) ELSE delivered_at END,
                        cancelled_at = CASE WHEN ? = 'CANCELLED' THEN COALESCE(cancelled_at, CURRENT_TIMESTAMP) ELSE cancelled_at END,
                        updated_at = CURRENT_TIMESTAMP WHERE id = ?
                    """, status, tracking, status, status, status, id);
            jdbcTemplate.update("""
                    INSERT INTO order_status_history(order_id, previous_status, new_status, actor_user_id, note)
                    VALUES (?, ?, ?, ?, ?)
                    """, id, previous.status(), status, actor, trackingChanged ? "Tracking number " + tracking : null);
        }
        return findById(id);
    }

    private OrderResponse findById(long id) {
        return jdbcTemplate.query("""
                SELECT o.id, o.order_number, o.shipping_recipient_name, o.created_at, o.total_amount,
                       o.payment_status, o.order_status, COALESCE(SUM(oi.quantity), 0) AS item_count,
                       u.email, o.currency_code
                FROM orders o JOIN users u ON u.id = o.user_id LEFT JOIN order_items oi ON oi.order_id = o.id
                WHERE o.id = ? GROUP BY o.id, u.email
                """, result -> result.next() ? new OrderResponse(
                result.getLong("id"), result.getString("order_number"), result.getString("shipping_recipient_name"),
                result.getObject("created_at", OffsetDateTime.class), result.getBigDecimal("total_amount"),
                result.getString("payment_status"), result.getString("order_status"), result.getLong("item_count"), result.getString("email"), result.getString("currency_code"))
                : null, id);
    }

    private void requireAdmin(HttpSession session) {
        Object userId = session.getAttribute("userId");
        if (!(userId instanceof Long id))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in is required.");
        String role = jdbcTemplate.query("SELECT role FROM users WHERE id = ? AND is_active = TRUE",
                result -> result.next() ? result.getString("role") : null, id);
        if (!"ADMIN".equals(role))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access is required.");
    }

    private record OrderState(String status, String trackingNumber, String paymentStatus,
            boolean storefront, Long couponId, boolean payHere) {}

    public record StatusRequest(@NotBlank String status, @Size(max = 255) String trackingNumber) {
    }

    public record OrderListResponse(List<OrderResponse> orders, Map<String, Long> counts) {
    }

    public record OrderResponse(
            long id, String orderNumber, String customerName, OffsetDateTime createdAt,
            BigDecimal total, String paymentStatus, String orderStatus, long itemCount, String customerEmail, String currencyCode) {
    }
}
