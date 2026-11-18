package com.nextcart.backend.admin;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/products/{productId}/images")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class ProductImageAdminController {

    private final JdbcTemplate jdbcTemplate;

    public ProductImageAdminController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public List<ImageResponse> list(@PathVariable long productId, HttpSession session) {
        requireAdmin(session);
        requireProduct(productId);
        return jdbcTemplate.query("""
                SELECT id, CASE WHEN image_content IS NOT NULL THEN '/api/media/products/' || id ELSE image_url END AS image_url,
                       alt_text, display_order, is_primary
                FROM product_images WHERE product_id = ? ORDER BY display_order, id
                """, (rs, rowNum) -> new ImageResponse(rs.getLong("id"), rs.getString("image_url"),
                rs.getString("alt_text"), rs.getInt("display_order"), rs.getBoolean("is_primary")), productId);
    }

    @PostMapping
    @Transactional
    public ImageResponse add(@PathVariable long productId, @RequestBody ImageRequest request, HttpSession session) {
        requireAdmin(session);
        lockProduct(productId);
        String url = AdminAccess.imageUrl(request.imageUrl());
        if (request.primary()) jdbcTemplate.update("UPDATE product_images SET is_primary = FALSE WHERE product_id = ?", productId);
        long id = jdbcTemplate.queryForObject("""
                INSERT INTO product_images (product_id, image_url, alt_text, display_order, is_primary)
                VALUES (?, ?, ?, ?, ?) RETURNING id
                """, Long.class, productId, url, request.altText(), request.displayOrder(), request.primary());
        return findById(id);
    }

    @DeleteMapping("/{imageId}")
    @Transactional
    public void remove(@PathVariable long productId, @PathVariable long imageId, HttpSession session) {
        requireAdmin(session);
        lockProduct(productId);
        if (jdbcTemplate.update("DELETE FROM product_images WHERE id = ? AND product_id = ?", imageId, productId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product image not found.");
        }
    }

    private ImageResponse findById(long id) {
        return jdbcTemplate.query("SELECT id, image_url, alt_text, display_order, is_primary FROM product_images WHERE id = ?",
                result -> result.next() ? new ImageResponse(result.getLong("id"), result.getString("image_url"),
                        result.getString("alt_text"), result.getInt("display_order"), result.getBoolean("is_primary")) : null, id);
    }

    private void lockProduct(long productId) {
        if (jdbcTemplate.query("SELECT id FROM products WHERE id = ? FOR UPDATE", rs -> !rs.next(), productId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found.");
        }
    }

    private void requireProduct(long productId) {
        if (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products WHERE id = ?", Long.class, productId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found.");
        }
    }

    private void requireAdmin(HttpSession session) {
        Object userId = session.getAttribute("userId");
        if (!(userId instanceof Long id)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in is required.");
        String role = jdbcTemplate.query("SELECT role FROM users WHERE id = ? AND is_active = TRUE",
                result -> result.next() ? result.getString("role") : null, id);
        if (!"ADMIN".equals(role)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access is required.");
    }

    public record ImageRequest(String imageUrl, String altText, int displayOrder, boolean primary) {}
    public record ImageResponse(long id, String imageUrl, String altText, int displayOrder, boolean primary) {}
}
