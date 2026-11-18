package com.nextcart.backend.admin;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/orders")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class OrderDetailAdminController {

        private final JdbcTemplate jdbcTemplate;

        public OrderDetailAdminController(JdbcTemplate jdbcTemplate) {
                this.jdbcTemplate = jdbcTemplate;
        }

        @GetMapping("/{id}/detail")
        public OrderDetailResponse detail(@PathVariable long id, HttpSession session) {
                long adminId = requireAdmin(session);
                OrderSummary summary = jdbcTemplate.query(
                                """
                                                SELECT o.id, o.order_number, o.user_id, u.first_name, u.last_name, u.email, u.phone,
                                                       o.shipping_recipient_name, o.shipping_phone, o.shipping_address_line1,
                                                       o.shipping_address_line2, o.shipping_city, o.shipping_postal_code, o.shipping_country,
                                                       o.shipping_state_or_province, o.tracking_number,
                                                       o.subtotal, o.discount_amount, o.shipping_amount, o.total_amount, o.currency_code,
                                                       o.order_status, o.payment_status, o.created_at, o.placed_at
                                                FROM orders o JOIN users u ON u.id = o.user_id WHERE o.id = ?
                                                """,
                                result -> result.next() ? new OrderSummary(
                                                result.getLong("id"), result.getString("order_number"),
                                                result.getLong("user_id"),
                                                result.getString("first_name"), result.getString("last_name"),
                                                result.getString("email"),
                                                result.getString("phone"), result.getString("shipping_recipient_name"),
                                                result.getString("shipping_phone"),
                                                result.getString("shipping_address_line1"),
                                                result.getString("shipping_address_line2"),
                                                result.getString("shipping_city"),
                                                result.getString("shipping_postal_code"),
                                                result.getString("shipping_country"),
                                                result.getBigDecimal("subtotal"),
                                                result.getBigDecimal("discount_amount"),
                                                result.getBigDecimal("shipping_amount"),
                                                result.getBigDecimal("total_amount"), result.getString("currency_code"),
                                                result.getString("order_status"),
                                                result.getString("payment_status"),
                                                result.getObject("created_at", OffsetDateTime.class),
                                                result.getObject("placed_at", OffsetDateTime.class), result.getString("shipping_state_or_province"),
                                                result.getString("tracking_number")) : null,
                                id);
                if (summary == null)
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found.");

                List<OrderItemResponse> items = jdbcTemplate.query("""
                                SELECT oi.id, oi.product_id, oi.product_name, oi.product_sku, oi.quantity, oi.unit_price, oi.subtotal, oi.variant_label,
                                       (SELECT CASE WHEN i.image_content IS NOT NULL THEN '/api/media/products/' || i.id ELSE i.image_url END
                                        FROM product_images i WHERE i.product_id = oi.product_id
                                        ORDER BY i.is_primary DESC, i.display_order, i.id LIMIT 1) AS image_url
                                FROM order_items oi WHERE oi.order_id = ? ORDER BY oi.id
                                """, (rs, rowNum) -> new OrderItemResponse(
                                rs.getLong("id"), rs.getObject("product_id", Long.class), rs.getString("product_name"),
                                rs.getString("product_sku"), rs.getInt("quantity"), rs.getBigDecimal("unit_price"),
                                rs.getBigDecimal("subtotal"), rs.getString("image_url"), rs.getString("variant_label")), id);
                List<PaymentResponse> payments = jdbcTemplate.query("""
                                SELECT payment_reference, payment_method, payment_provider, amount, currency_code,
                                       payment_status, transaction_id, paid_at, created_at
                                FROM payments WHERE order_id = ? ORDER BY created_at DESC
                                """, (rs, rowNum) -> new PaymentResponse(
                                rs.getString("payment_reference"), rs.getString("payment_method"),
                                rs.getString("payment_provider"),
                                rs.getBigDecimal("amount"), rs.getString("currency_code"),
                                rs.getString("payment_status"),
                                rs.getString("transaction_id"), rs.getObject("paid_at", OffsetDateTime.class),
                                rs.getObject("created_at", OffsetDateTime.class)), id);
                List<NoteResponse> notes = jdbcTemplate.query("""
                                SELECT n.note, n.created_at, u.first_name, u.last_name
                                FROM order_admin_notes n JOIN users u ON u.id = n.created_by_user_id
                                WHERE n.order_id = ? ORDER BY n.created_at DESC
                                """, (rs, rowNum) -> new NoteResponse(
                                rs.getString("note"), rs.getObject("created_at", OffsetDateTime.class),
                                rs.getString("first_name") + " " + rs.getString("last_name")), id);
                List<TimelineEntry> timeline = jdbcTemplate.query("""
                                SELECT h.new_status, h.note, h.created_at,
                                       NULLIF(concat_ws(' ', u.first_name, u.last_name), '') AS author
                                FROM order_status_history h LEFT JOIN users u ON u.id = h.actor_user_id
                                WHERE h.order_id = ? ORDER BY h.created_at DESC, h.id DESC
                                """, (rs, row) -> new TimelineEntry(rs.getString("new_status"), rs.getString("note"),
                                rs.getString("author"), rs.getObject("created_at", OffsetDateTime.class)), id);
                return new OrderDetailResponse(summary, items, payments, notes, adminId, timeline);
        }

        @PostMapping("/{id}/notes")
        public NoteResponse addNote(
                        @PathVariable long id,
                        @Valid @RequestBody NoteRequest request,
                        HttpSession session) {
                long adminId = requireAdmin(session);
                if (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders WHERE id = ?", Long.class, id) == 0) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found.");
                }
                return jdbcTemplate.queryForObject("""
                                INSERT INTO order_admin_notes (order_id, created_by_user_id, note)
                                VALUES (?, ?, ?) RETURNING note, created_at,
                                    (SELECT concat_ws(' ', u.first_name, u.last_name) FROM users u WHERE u.id = created_by_user_id) AS author
                                """, (rs, rowNum) -> new NoteResponse(
                                rs.getString("note"), rs.getObject("created_at", OffsetDateTime.class),
                                rs.getString("author")), id, adminId, request.note().trim());
        }

        private long requireAdmin(HttpSession session) {
                Object userId = session.getAttribute("userId");
                if (!(userId instanceof Long id))
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in is required.");
                String role = jdbcTemplate.query("SELECT role FROM users WHERE id = ? AND is_active = TRUE",
                                result -> result.next() ? result.getString("role") : null, id);
                if (!"ADMIN".equals(role))
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access is required.");
                return id;
        }

        public record NoteRequest(@NotBlank @Size(max = 4000) String note) {
        }

        public record OrderDetailResponse(OrderSummary summary, List<OrderItemResponse> items,
                        List<PaymentResponse> payments, List<NoteResponse> notes, long adminId, List<TimelineEntry> timeline) {
        }

        public record TimelineEntry(String status, String note, String author, OffsetDateTime createdAt) {}

        public record OrderSummary(long id, String orderNumber, long userId, String firstName, String lastName,
                        String email, String phone, String recipientName, String shippingPhone, String addressLine1,
                        String addressLine2, String city, String postalCode, String country, BigDecimal subtotal,
                        BigDecimal discountAmount, BigDecimal shippingAmount, BigDecimal totalAmount,
                        String currencyCode, String orderStatus, String paymentStatus, OffsetDateTime createdAt,
                        OffsetDateTime placedAt, String stateOrProvince, String trackingNumber) {
        }

        public record OrderItemResponse(long id, Long productId, String productName, String productSku, int quantity,
                        BigDecimal unitPrice, BigDecimal subtotal, String imageUrl, String variantLabel) {
        }

        public record PaymentResponse(String reference, String method, String provider, BigDecimal amount,
                        String currencyCode, String status, String transactionId, OffsetDateTime paidAt,
                        OffsetDateTime createdAt) {
        }

        public record NoteResponse(String note, OffsetDateTime createdAt, String author) {
        }
}
