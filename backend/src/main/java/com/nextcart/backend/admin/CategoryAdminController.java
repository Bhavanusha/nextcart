package com.nextcart.backend.admin;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/categories")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class CategoryAdminController {

    private static final Pattern NON_SLUG_CHARACTERS = Pattern.compile("[^a-z0-9]+");
    private static final String CATEGORY_SELECT = """
            WITH RECURSIVE tree(root_id, category_id) AS (
                SELECT id, id FROM categories
                UNION
                SELECT tree.root_id, child.id FROM tree JOIN categories child ON child.parent_id = tree.category_id
            )
            SELECT c.id, c.name, c.slug, c.description, c.image_url, c.parent_id, c.is_active, c.created_at,
                   COUNT(p.id) AS product_count
            FROM categories c JOIN tree ON tree.root_id = c.id
            LEFT JOIN products p ON p.category_id = tree.category_id
            """;
    private final JdbcTemplate jdbcTemplate;

    public CategoryAdminController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public List<CategoryResponse> list(HttpSession session) {
        requireAdmin(session);
        return jdbcTemplate.query(CATEGORY_SELECT + "GROUP BY c.id ORDER BY c.parent_id NULLS FIRST, c.name",
                (rs, row) -> map(rs));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<CategoryResponse> create(
            @Valid @RequestBody CategoryRequest request,
            HttpSession session) {
        requireAdmin(session);
        validateParent(null, request.parentId());
        String slug = toSlug(request.name());
        try {
            Long id = jdbcTemplate.queryForObject("""
                    INSERT INTO categories (name, slug, description, image_url, parent_id, is_active)
                    VALUES (?, ?, ?, ?, ?, COALESCE(?, TRUE))
                    RETURNING id
                    """, Long.class, request.name().trim(), slug, request.description(),
                    request.imageUrl(), request.parentId(), request.active());
            return ResponseEntity.status(HttpStatus.CREATED).body(findById(id));
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A category with this name already exists.");
        }
    }

    @PutMapping("/{id}")
    @Transactional
    public CategoryResponse update(
            @PathVariable long id,
            @Valid @RequestBody CategoryRequest request,
            HttpSession session) {
        requireAdmin(session);
        validateParent(id, request.parentId());
        String slug = toSlug(request.name());
        try {
            int updated = jdbcTemplate.update("""
                    UPDATE categories
                    SET name = ?, slug = ?, description = ?, image_url = ?, parent_id = ?, is_active = COALESCE(?, is_active), updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, request.name().trim(), slug, request.description(), request.imageUrl(), request.parentId(), request.active(), id);
            if (updated == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found.");
            }
            return findById(id);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A category with this name already exists.");
        }
    }

    @DeleteMapping("/{id}")
    public void deactivate(@PathVariable long id, HttpSession session) {
        requireAdmin(session);
        int updated = jdbcTemplate.update("""
                UPDATE categories SET is_active = FALSE, updated_at = CURRENT_TIMESTAMP WHERE id = ?
                """, id);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found.");
        }
    }

    private CategoryResponse findById(long id) {
        return jdbcTemplate.query(CATEGORY_SELECT + "WHERE c.id = ? GROUP BY c.id",
                rs -> rs.next() ? map(rs) : null, id);
    }

    private CategoryResponse map(ResultSet rs) throws SQLException {
        return new CategoryResponse(rs.getLong("id"), rs.getString("name"), rs.getString("slug"),
                rs.getString("description"), rs.getString("image_url"), rs.getObject("parent_id", Long.class),
                rs.getBoolean("is_active"), rs.getLong("product_count"), rs.getObject("created_at", OffsetDateTime.class));
    }

    private void requireAdmin(HttpSession session) {
        Object userId = session.getAttribute("userId");
        if (!(userId instanceof Long id)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in is required.");
        }
        String role = jdbcTemplate.query("""
                SELECT role FROM users WHERE id = ? AND is_active = TRUE
                """, result -> result.next() ? result.getString("role") : null, id);
        if (!"ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access is required.");
        }
    }

    private void validateParent(Long id, Long parentId) {
        jdbcTemplate.execute("LOCK TABLE categories IN SHARE ROW EXCLUSIVE MODE");
        if (parentId == null) return;
        List<Long> ancestors = jdbcTemplate.query("""
                WITH RECURSIVE ancestors AS (
                    SELECT id, parent_id FROM categories WHERE id = ?
                    UNION
                    SELECT c.id, c.parent_id FROM categories c JOIN ancestors a ON c.id = a.parent_id
                )
                SELECT id FROM ancestors
                """, (rs, row) -> rs.getLong(1), parentId);
        if (ancestors.isEmpty() || (id != null && ancestors.contains(id))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a valid parent category.");
        }
    }

    private String toSlug(String name) {
        String slug = NON_SLUG_CHARACTERS.matcher(name.trim().toLowerCase(Locale.ROOT))
                .replaceAll("-").replaceAll("(^-+|-+$)", "");
        if (slug.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a category name with letters or numbers.");
        return slug;
    }

    public record CategoryRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 2000) String description,
            @Size(max = 2048) String imageUrl,
            Long parentId, Boolean active) {
    }

    public record CategoryResponse(
            Long id,
            String name,
            String slug,
            String description,
            String imageUrl,
            Long parentId,
            boolean active,
            long productCount,
            OffsetDateTime createdAt) {
    }
}
