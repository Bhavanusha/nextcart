
package com.nextcart.backend.admin;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/products")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class ProductAdminController {
    private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9]+");
    private static final String PRODUCT_SELECT = """
            SELECT p.*, c.name AS category_name, b.name AS brand_name,
                   COALESCE(p.catalog_status, CASE WHEN p.is_active THEN 'ACTIVE' ELSE 'ARCHIVED' END) AS status,
                   (SELECT CASE WHEN i.image_content IS NOT NULL THEN '/api/media/products/' || i.id ELSE i.image_url END
                    FROM product_images i WHERE i.product_id = p.id ORDER BY i.is_primary DESC, i.display_order, i.id LIMIT 1) AS image_url
            FROM products p LEFT JOIN categories c ON c.id = p.category_id LEFT JOIN brands b ON b.id = p.brand_id
            """;

    private final JdbcTemplate jdbc;

    public ProductAdminController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<ProductResponse> list(@RequestParam(defaultValue = "") String search, HttpSession session) {
        AdminAccess.require(jdbc, session);
        String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        return jdbc.query(PRODUCT_SELECT + """
                WHERE LOWER(p.name) LIKE ? OR LOWER(p.sku) LIKE ?
                ORDER BY p.created_at DESC, p.id DESC
                """, (rs, row) -> toResponse(rs), pattern, pattern);
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable long id, HttpSession session) {
        AdminAccess.require(jdbc, session);
        ProductResponse product = find(id);
        if (product == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found.");
        return product;
    }

    @GetMapping("/{id}/variants")
    public List<Variant> variants(@PathVariable long id, HttpSession session) {
        get(id, session);
        return jdbc.query("SELECT color, size, sku_override, price FROM product_variants WHERE product_id = ? ORDER BY id",
                (rs, row) -> new Variant(rs.getString(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4)), id);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request, HttpSession session) {
        AdminAccess.require(jdbc, session);
        return ResponseEntity.status(HttpStatus.CREATED).body(save(null, request));
    }

    @PutMapping("/{id}")
    @Transactional
    public ProductResponse update(@PathVariable long id, @Valid @RequestBody ProductRequest request, HttpSession session) {
        AdminAccess.require(jdbc, session);
        return save(id, request);
    }

    private ProductResponse save(Long id, ProductRequest request) {
        String status = request.status();
        if (status != null && !List.of("ACTIVE", "DRAFT", "ARCHIVED").contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid product status.");
        }
        String slug = slug(request.slug() == null || request.slug().isBlank() ? request.name() : request.slug());
        if (slug.isBlank() || slug.length() > 180 || !request.currencyCode().matches("[A-Za-z]{3}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a valid URL slug and currency code.");
        }
        validateReference("categories", request.categoryId(), "Category");
        Long brand = resolveBrand(request);
        String currency = request.currencyCode().toUpperCase(Locale.ROOT);
        Boolean active = status == null ? null : status.equals("ACTIVE");
        try {
            if (id == null) {
                id = jdbc.queryForObject("""
                        INSERT INTO products (name, description, price, stock_quantity, category_id, brand_id, sku,
                            slug, currency_code, is_featured, compare_at_price, meta_title, catalog_status, is_active)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, COALESCE(?, 'ACTIVE'), COALESCE(?, TRUE))
                        RETURNING id
                        """, Long.class, request.name().trim(), request.description(), request.price(), request.stockQuantity(),
                        request.categoryId(), brand, request.sku().trim(), slug, currency, Boolean.TRUE.equals(request.featured()),
                        request.compareAtPrice(), request.metaTitle(), status, active);
            } else {
                Stock current = jdbc.query("SELECT stock_quantity, reserved_quantity FROM products WHERE id = ? FOR UPDATE",
                        rs -> rs.next() ? new Stock(rs.getInt(1), rs.getInt(2)) : null, id);
                if (current == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found.");
                int stock = request.stockQuantity();
                if (request.expectedStockQuantity() != null) {
                    if (stock == request.expectedStockQuantity()) stock = current.quantity();
                    else if (current.quantity() != request.expectedStockQuantity()) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock has changed. Reload the product before changing stock.");
                    }
                }
                if (stock < current.reserved()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stock cannot be below reserved quantity.");
                }
                jdbc.update("""
                        UPDATE products SET name = ?, description = ?, price = ?, stock_quantity = ?, category_id = ?,
                            brand_id = ?, sku = ?, slug = ?, currency_code = ?, is_featured = COALESCE(?, is_featured),
                            compare_at_price = CASE WHEN ? THEN ? ELSE compare_at_price END,
                            meta_title = CASE WHEN ? THEN ? ELSE meta_title END,
                            catalog_status = COALESCE(?, catalog_status), is_active = COALESCE(?, is_active),
                            updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                        """, request.name().trim(), request.description(), request.price(), stock,
                        request.categoryId(), brand, request.sku().trim(), slug, currency, request.featured(),
                        status != null, request.compareAtPrice(), status != null, request.metaTitle(),
                        status, active, id);
                if (stock != current.quantity()) {
                    jdbc.update("""
                            INSERT INTO inventory_transactions
                                (product_id, transaction_type, quantity, previous_stock, new_stock, reference_type)
                            VALUES (?, 'STOCK_ADJUSTMENT', ?, ?, ?, 'ADMIN')
                            """, id, Math.abs(stock - current.quantity()), current.quantity(), stock);
                }
            }
            if (request.variants() != null) saveVariants(id, request.variants());
            if (request.gallery() != null) saveGallery(id, request.gallery());
            return find(id);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Product slug, SKU or variant SKU already exists.");
        }
    }

    private void saveVariants(long id, List<Variant> variants) {
        jdbc.update("DELETE FROM product_variants WHERE product_id = ?", id);
        for (Variant variant : variants) {
            if (variant.color() == null && variant.size() == null && variant.skuOverride() == null && variant.price() == null) {
                continue;
            }
            jdbc.update("INSERT INTO product_variants (product_id, color, size, sku_override, price) VALUES (?, ?, ?, ?, ?)",
                    id, variant.color(), variant.size(), variant.skuOverride(), variant.price());
        }
    }

    private void saveGallery(long id, List<GalleryImage> gallery) {
        List<Long> retained = gallery.stream().map(GalleryImage::id).filter(java.util.Objects::nonNull).toList();
        if (retained.stream().distinct().count() != retained.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Gallery contains duplicate images.");
        }
        jdbc.update("UPDATE product_images SET is_primary = FALSE WHERE product_id = ?", id);
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "DELETE FROM product_images WHERE product_id = ? AND NOT (id = ANY (?))");
            statement.setLong(1, id);
            statement.setArray(2, connection.createArrayOf("bigint", retained.toArray()));
            return statement;
        });
        int order = 0;
        for (GalleryImage image : gallery) {
            boolean primary = order == 0;
            if (image.id() != null) {
                int updated = jdbc.update("""
                        UPDATE product_images SET display_order = ?, is_primary = ? WHERE id = ? AND product_id = ?
                        """, order, primary, image.id(), id);
                if (updated == 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Gallery image not found.");
            } else {
                String source = image.source();
                if (source != null && source.startsWith("data:")) {
                    var file = AdminMediaController.decode(source);
                    jdbc.update("""
                            INSERT INTO product_images (product_id, image_url, display_order, is_primary, image_content, content_type)
                            VALUES (?, '', ?, ?, ?, ?)
                            """, id, order, primary, file.bytes(), file.contentType());
                } else {
                    jdbc.update("INSERT INTO product_images (product_id, image_url, display_order, is_primary) VALUES (?, ?, ?, ?)",
                            id, AdminAccess.imageUrl(source), order, primary);
                }
            }
            order++;
        }
    }

    @DeleteMapping("/{id}")
    public void deactivate(@PathVariable long id, HttpSession session) {
        AdminAccess.require(jdbc, session);
        if (jdbc.update("""
                UPDATE products SET is_active = FALSE, catalog_status = 'ARCHIVED', updated_at = CURRENT_TIMESTAMP WHERE id = ?
                """, id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found.");
        }
    }

    private ProductResponse find(long id) {
        return jdbc.query(PRODUCT_SELECT + "WHERE p.id = ?", rs -> rs.next() ? toResponse(rs) : null, id);
    }

    private ProductResponse toResponse(ResultSet rs) throws SQLException {
        return new ProductResponse(rs.getLong("id"), rs.getString("name"), rs.getString("description"),
                rs.getBigDecimal("price"), rs.getInt("stock_quantity"), rs.getObject("category_id", Long.class),
                rs.getString("category_name"), rs.getObject("brand_id", Long.class), rs.getString("sku"),
                rs.getString("slug"), rs.getBoolean("is_active"), rs.getBoolean("is_featured"),
                rs.getString("currency_code"), rs.getString("brand_name"), rs.getBigDecimal("compare_at_price"),
                rs.getString("meta_title"), rs.getString("status"), rs.getString("image_url"), rs.getInt("reserved_quantity"),
                rs.getInt("low_stock_threshold"));
    }

    private Long resolveBrand(ProductRequest request) {
        String name = request.brandName();
        if (name == null || name.isBlank()) {
            validateReference("brands", request.brandId(), "Brand");
            return request.brandId();
        }
        String normalized = name.trim();
        String slug = slug(normalized);
        if (slug.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a brand name with letters or numbers.");
        return jdbc.queryForObject("""
                INSERT INTO brands (name, slug) VALUES (?, ?)
                ON CONFLICT (slug) DO UPDATE SET slug = EXCLUDED.slug RETURNING id
                """, Long.class, normalized, slug);
    }

    private void validateReference(String table, Long id, String label) {
        if (id != null && jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id = ?", Long.class, id) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " not found.");
        }
    }

    private String slug(String value) {
        return NON_SLUG.matcher(value.trim().toLowerCase(Locale.ROOT)).replaceAll("-").replaceAll("(^-+|-+$)", "");
    }

    private record Stock(int quantity, int reserved) {}

    public record Variant(@Size(max = 100) String color, @Size(max = 100) String size,
            @Size(max = 100) String skuOverride, @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal price) {}

    public record GalleryImage(Long id, String source) {}

    public record ProductRequest(
            @NotBlank @Size(max = 255) String name, @Size(max = 10000) String description,
            @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @NotNull @Min(0) Integer stockQuantity, Long categoryId, Long brandId,
            @NotBlank @Size(max = 100) String sku, @Size(max = 180) String slug,
            @NotBlank @Size(min = 3, max = 3) String currencyCode, Boolean featured,
            @Size(max = 150) String brandName, @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal compareAtPrice,
            @Size(max = 255) String metaTitle, String status, @Size(max = 100) List<@NotNull @Valid Variant> variants,
            @Size(max = 12) List<@NotNull GalleryImage> gallery, @Min(0) Integer expectedStockQuantity) {}

    public record ProductResponse(
            Long id, String name, String description, BigDecimal price, int stockQuantity, Long categoryId,
            String categoryName, Long brandId, String sku, String slug, boolean active, boolean featured,
            String currencyCode, String brandName, BigDecimal compareAtPrice, String metaTitle, String status,
            String imageUrl, int reservedQuantity, int lowStockThreshold) {}
}
