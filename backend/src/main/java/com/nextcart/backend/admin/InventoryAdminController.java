package com.nextcart.backend.admin;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/inventory")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class InventoryAdminController {

    private static final String STOCK_SELECT = """
            SELECT p.id, p.name, p.sku, p.stock_quantity, p.is_active, c.name AS category_name,
                   p.reserved_quantity, p.low_stock_threshold, p.updated_at,
                   (SELECT CASE WHEN i.image_content IS NOT NULL THEN '/api/media/products/' || i.id ELSE i.image_url END
                    FROM product_images i WHERE i.product_id = p.id ORDER BY i.is_primary DESC, i.display_order, i.id LIMIT 1) AS image_url,
                   (SELECT string_agg(concat_ws(' / ', v.color, v.size), ', ' ORDER BY v.id)
                    FROM product_variants v WHERE v.product_id = p.id) AS variants
            FROM products p LEFT JOIN categories c ON c.id = p.category_id
            """;
    private final JdbcTemplate jdbcTemplate;

    public InventoryAdminController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public List<InventoryResponse> list(
            @RequestParam(required = false, defaultValue = "") String search,
            HttpSession session) {
        requireAdmin(session);
        String pattern = "%" + search.trim().toLowerCase() + "%";
        return jdbcTemplate.query(STOCK_SELECT + """
                WHERE LOWER(p.name) LIKE ? OR LOWER(p.sku) LIKE ?
                ORDER BY p.name
                """, (rs, row) -> map(rs), pattern, pattern);
    }

    @PostMapping("/{productId}/adjust")
    @Transactional
    public InventoryResponse adjust(
            @PathVariable long productId,
            @Valid @RequestBody AdjustmentRequest request,
            HttpSession session) {
        requireAdmin(session);
        ProductStock product = jdbcTemplate.query("""
                SELECT stock_quantity, reserved_quantity FROM products WHERE id = ? FOR UPDATE
                """, result -> result.next() ? new ProductStock(result.getInt("stock_quantity"), result.getInt("reserved_quantity"))
                : null, productId);
        if (product == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found.");

        long calculated = (long) product.stockQuantity() + request.adjustmentQuantity();
        if (calculated < product.reservedQuantity() || calculated > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stock must fit the supported range and cover reserved units.");
        }
        int newStock = (int) calculated;
        int absoluteQuantity = Math.abs(request.adjustmentQuantity());
        if (absoluteQuantity == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Adjustment quantity cannot be zero.");
        }
        if (request.reason() != null && !List.of("SHIPMENT", "RECOUNT", "DAMAGE", "RETURN", "OTHER").contains(request.reason())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid adjustment reason.");
        }

        jdbcTemplate.update("""
                UPDATE products SET stock_quantity = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?
                """, newStock, productId);
        jdbcTemplate.update("""
                INSERT INTO inventory_transactions
                    (product_id, transaction_type, quantity, previous_stock, new_stock, reference_type, notes, adjustment_reason)
                VALUES (?, 'STOCK_ADJUSTMENT', ?, ?, ?, 'ADMIN', ?, ?)
                """, productId, absoluteQuantity, product.stockQuantity(), newStock, request.notes(), request.reason());
        return findInventory(productId);
    }

    private InventoryResponse findInventory(long productId) {
        return jdbcTemplate.query(STOCK_SELECT + "WHERE p.id = ?", rs -> rs.next() ? map(rs) : null, productId);
    }

    private InventoryResponse map(java.sql.ResultSet rs) throws java.sql.SQLException {
        int stock = rs.getInt("stock_quantity");
        int reserved = rs.getInt("reserved_quantity");
        int available = Math.max(0, stock - reserved);
        String status = available == 0 ? "OUT_OF_STOCK"
                : available <= rs.getInt("low_stock_threshold") ? "LOW_STOCK" : "IN_STOCK";
        return new InventoryResponse(rs.getLong("id"), rs.getString("name"), rs.getString("sku"), stock,
                rs.getString("category_name"), rs.getBoolean("is_active"), reserved, available, status,
                rs.getObject("updated_at", java.time.OffsetDateTime.class), rs.getString("image_url"), rs.getString("variants"));
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

    public record AdjustmentRequest(@NotNull Integer adjustmentQuantity, String notes, String reason) {
    }

    public record InventoryResponse(long id, String name, String sku, int stockQuantity, String categoryName,
            boolean active, int reservedQuantity, int availableQuantity, String status,
            java.time.OffsetDateTime updatedAt, String imageUrl, String variants) {
    }

    private record ProductStock(int stockQuantity, int reservedQuantity) {
    }
}
