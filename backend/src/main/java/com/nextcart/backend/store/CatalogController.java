package com.nextcart.backend.store;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/store")
@CrossOrigin(origins = StoreAccess.ORIGIN, allowCredentials = "true")
public class CatalogController {
    static final String CATEGORY_TREE = """
            WITH RECURSIVE visible_categories AS (
                SELECT id, parent_id, name, slug, image_url, sort_order FROM categories WHERE is_active AND parent_id IS NULL
                UNION ALL
                SELECT c.id, c.parent_id, c.name, c.slug, c.image_url, c.sort_order
                FROM categories c JOIN visible_categories parent ON c.parent_id = parent.id WHERE c.is_active
            )
            """;
    static final String PUBLISHED = """
            p.is_active AND COALESCE(p.catalog_status, 'ACTIVE') = 'ACTIVE'
            AND (p.category_id IS NULL OR c.id IS NOT NULL)
            """;
    static final String IMAGE = """
            (SELECT CASE WHEN pi.image_content IS NOT NULL THEN '/api/store/images/' || pi.id ELSE pi.image_url END
             FROM product_images pi WHERE pi.product_id = p.id ORDER BY pi.is_primary DESC, pi.display_order, pi.id LIMIT 1)
            """;
    static final String CARDS = """
            SELECT p.id, p.slug, p.name, p.price, p.compare_at_price, p.currency_code, p.created_at,
                c.name AS category_name, c.slug AS category_slug, p.is_featured, p.stock_quantity - p.reserved_quantity AS available,
                EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id = p.id) AS has_variants,
                %s AS image_url
            FROM products p LEFT JOIN visible_categories c ON p.category_id = c.id
            """.formatted(IMAGE);

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public CatalogController(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @GetMapping("/settings")
    public StoreSettings settings() {
        return jdbc.queryForObject("""
                SELECT store_name, currency_code, contact_email, contact_phone, country, tax_rate,
                       (shipping_config->>'flatRate')::numeric, (shipping_config->>'freeShippingMinimum')::numeric,
                       storefront_content::text, CASE WHEN logo_content IS NULL THEN logo_url ELSE '/api/media/store-logo' END
                FROM store_settings WHERE id = 1
                """, (rs, row) -> new StoreSettings(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getBigDecimal(6), rs.getBigDecimal(7), rs.getBigDecimal(8),
                json.readTree(rs.getString(9)), rs.getString(10)));
    }

    @GetMapping("/categories")
    public List<Category> categories() {
        return jdbc.query(CATEGORY_TREE + """
                SELECT c.id, c.parent_id, c.name, c.slug, c.image_url, COUNT(p.id)
                FROM visible_categories c LEFT JOIN products p ON p.category_id = c.id
                    AND p.is_active AND COALESCE(p.catalog_status, 'ACTIVE') = 'ACTIVE'
                GROUP BY c.id, c.parent_id, c.name, c.slug, c.image_url, c.sort_order
                ORDER BY c.sort_order, c.name
                """, (rs, row) -> new Category(rs.getLong(1), rs.getObject(2, Long.class), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getLong(6)));
    }

    @GetMapping("/products")
    public Catalog products(@RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") List<String> category, @RequestParam(defaultValue = "") String stock,
            @RequestParam(required = false) BigDecimal min, @RequestParam(required = false) BigDecimal max,
            @RequestParam(defaultValue = "featured") String sort, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "9") int size, @RequestParam(defaultValue = "") String collection) {
        if (q.length() > 150 || category.size() > 30 || page < 1 || page > 10000 || size < 1 || size > 36
                || (min != null && min.signum() < 0) || (max != null && max.signum() < 0)
                || (min != null && max != null && min.compareTo(max) > 0)) {
            throw StoreAccess.error(HttpStatus.BAD_REQUEST, "Check the catalog filters.");
        }
        var filters = new StringBuilder(" WHERE ").append(PUBLISHED);
        var params = new ArrayList<>();
        if (!q.isBlank()) {
            filters.append(" AND strpos(lower(concat_ws(' ', p.name, p.description, p.sku, c.name)), ?) > 0");
            params.add(q.trim().toLowerCase(Locale.ROOT));
        }
        if (!category.isEmpty()) {
            filters.append(" AND c.id IN (WITH RECURSIVE selected AS (SELECT id FROM visible_categories WHERE slug IN (")
                    .append(String.join(",", java.util.Collections.nCopies(category.size(), "?")))
                    .append(") UNION SELECT c2.id FROM visible_categories c2 JOIN selected s ON c2.parent_id = s.id) SELECT id FROM selected)");
            params.addAll(category);
        }
        if ("in".equals(stock)) filters.append(" AND p.stock_quantity > p.reserved_quantity");
        if ("out".equals(stock)) filters.append(" AND p.stock_quantity <= p.reserved_quantity");
        if (min != null) { filters.append(" AND p.price >= ?"); params.add(min); }
        if (max != null) { filters.append(" AND p.price <= ?"); params.add(max); }
        if ("featured".equals(collection)) filters.append(" AND p.is_featured");
        if ("sale".equals(collection)) filters.append(" AND p.compare_at_price > p.price");
        String ordering = switch (sort) {
            case "new" -> "p.created_at DESC";
            case "price-low" -> "p.price ASC";
            case "price-high" -> "p.price DESC";
            case "popular" -> """
                (SELECT COALESCE(SUM(oi.quantity), 0) FROM order_items oi JOIN orders o ON o.id = oi.order_id
                 WHERE oi.product_id = p.id AND o.order_status <> 'CANCELLED' AND o.payment_status = 'PAID') DESC
                """;
            default -> "p.is_featured DESC, p.created_at DESC";
        };
        long total = jdbc.queryForObject(CATEGORY_TREE + """
                SELECT COUNT(*) FROM products p LEFT JOIN visible_categories c ON p.category_id = c.id
                """ + filters, Long.class, params.toArray());
        BigDecimal ceiling = jdbc.queryForObject(CATEGORY_TREE + """
                SELECT COALESCE(MAX(p.price), 0) FROM products p LEFT JOIN visible_categories c ON p.category_id = c.id WHERE
                """ + PUBLISHED, BigDecimal.class);
        params.add(size);
        params.add((page - 1) * size);
        return new Catalog(jdbc.query(CATEGORY_TREE + CARDS + filters + " ORDER BY " + ordering + ", p.id DESC LIMIT ? OFFSET ?",
                (rs, row) -> card(rs), params.toArray()), total, page, size, ceiling);
    }

    @GetMapping("/products/{slug}")
    public ProductDetail detail(@PathVariable String slug) {
        ProductCard product = jdbc.query(CATEGORY_TREE + CARDS + " WHERE " + PUBLISHED + " AND p.slug = ?",
                rs -> rs.next() ? card(rs) : null, slug);
        if (product == null) throw StoreAccess.error(HttpStatus.NOT_FOUND, "Product not found.");
        long id = product.id();
        var info = jdbc.queryForObject("""
                SELECT p.description, b.name, p.weight FROM products p LEFT JOIN brands b ON b.id = p.brand_id WHERE p.id = ?
                """, (rs, row) -> new ProductInfo(rs.getString(1), rs.getString(2), rs.getBigDecimal(3)), id);
        var images = jdbc.query("""
                SELECT CASE WHEN image_content IS NOT NULL THEN '/api/store/images/' || id ELSE image_url END, alt_text
                FROM product_images WHERE product_id = ? ORDER BY is_primary DESC, display_order, id
                """, (rs, row) -> new Photo(rs.getString(1), rs.getString(2)), id);
        var variants = jdbc.query("SELECT id, color, size, price FROM product_variants WHERE product_id = ? ORDER BY id",
                (rs, row) -> new Variant(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4)), id);
        var reviews = jdbc.query("""
                SELECT r.rating, r.title, r.comment, r.created_at, concat(u.first_name, ' ', left(u.last_name, 1), '.') AS author
                FROM reviews r JOIN users u ON r.user_id = u.id
                WHERE r.product_id = ? AND r.is_approved AND NOT r.is_hidden
                ORDER BY r.created_at DESC LIMIT 20
                """, (rs, row) -> new Review(rs.getInt(1), rs.getString(2), rs.getString(3),
                rs.getObject(4, OffsetDateTime.class), rs.getString(5)), id);
        var ratings = jdbc.query("""
                SELECT rating, COUNT(*) FROM reviews WHERE product_id = ? AND is_approved AND NOT is_hidden GROUP BY rating
                """, (rs, row) -> new Rating(rs.getInt(1), rs.getLong(2)), id);
        var related = jdbc.query(CATEGORY_TREE + CARDS + " WHERE " + PUBLISHED + """
                 AND p.id <> ? ORDER BY COALESCE(c.slug = ?, FALSE) DESC, p.is_featured DESC, p.created_at DESC, p.id DESC LIMIT 4
                """, (rs, row) -> card(rs), id, product.categorySlug());
        return new ProductDetail(product, info, images, variants, reviews, ratings, related);
    }

    @GetMapping("/images/{id}")
    public ResponseEntity<byte[]> image(@PathVariable long id, HttpServletRequest request) {
        var session = request.getSession(false);
        Long viewer = session != null && session.getAttribute("userId") instanceof Long user ? user : null;
        var image = jdbc.query(CATEGORY_TREE + """
                SELECT pi.image_content, pi.content_type FROM product_images pi JOIN products p ON p.id = pi.product_id
                LEFT JOIN visible_categories c ON c.id = p.category_id WHERE pi.id = ? AND pi.image_content IS NOT NULL AND
                ((
                """ + PUBLISHED + """
                ) OR EXISTS (SELECT 1 FROM order_items oi JOIN orders o ON o.id = oi.order_id
                             JOIN users u ON u.id = o.user_id AND u.is_active
                             WHERE o.user_id = ? AND oi.image_snapshot = '/api/store/images/' || pi.id))
                """, rs -> rs.next() ? new Binary(rs.getBytes(1), rs.getString(2)) : null, id, viewer);
        if (image == null) throw StoreAccess.error(HttpStatus.NOT_FOUND, "Image not found.");
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.type()))
                .header("Cache-Control", "private, no-store").header("X-Content-Type-Options", "nosniff").body(image.bytes());
    }

    @PostMapping("/newsletter")
    public Map<String, String> subscribe(@Valid @RequestBody Newsletter request) {
        jdbc.update("INSERT INTO newsletter_subscriptions(email) VALUES (?) ON CONFLICT DO NOTHING",
                request.email().trim().toLowerCase(Locale.ROOT));
        return Map.of("message", "You’re subscribed.");
    }

    static ProductCard card(ResultSet rs) throws SQLException {
        return new ProductCard(rs.getLong("id"), rs.getString("slug"), rs.getString("name"), rs.getBigDecimal("price"),
                rs.getBigDecimal("compare_at_price"), rs.getString("currency_code"), rs.getString("category_name"),
                rs.getString("category_slug"), Math.max(0, rs.getInt("available")), rs.getBoolean("has_variants"),
                rs.getString("image_url"), rs.getBoolean("is_featured"), rs.getObject("created_at", OffsetDateTime.class));
    }

    record Newsletter(@NotBlank @Email @Size(max = 320) String email) {}
    record StoreSettings(String name, String currency, String email, String phone, String country, BigDecimal taxRate,
            BigDecimal shippingRate, BigDecimal freeShippingMinimum, JsonNode content, String logo) {}
    record Category(long id, Long parentId, String name, String slug, String image, long count) {}
    record Binary(byte[] bytes, String type) {}
    record ProductCard(long id, String slug, String name, BigDecimal price, BigDecimal compareAt, String currency,
            String category, String categorySlug, int available, boolean hasVariants, String image, boolean featured, OffsetDateTime createdAt) {}
    record Catalog(List<ProductCard> products, long total, int page, int size, BigDecimal priceCeiling) {}
    record ProductInfo(String description, String brand, BigDecimal weight) {}
    record Photo(String url, String alt) {}
    record Variant(long id, String color, String size, BigDecimal price) {}
    record Review(int rating, String title, String comment, OffsetDateTime createdAt, String author) {}
    record Rating(int rating, long count) {}
    record ProductDetail(ProductCard product, ProductInfo info, List<Photo> images, List<Variant> variants,
            List<Review> reviews, List<Rating> ratings, List<ProductCard> related) {}
}
