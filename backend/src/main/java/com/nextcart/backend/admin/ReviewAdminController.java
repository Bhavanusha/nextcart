
package com.nextcart.backend.admin;

import jakarta.servlet.http.HttpSession;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/reviews")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class ReviewAdminController {
    private static final String REVIEW_SELECT = """
            SELECT r.id, r.rating, r.title, r.comment, r.is_verified_purchase, r.is_approved, r.is_hidden, r.created_at,
                   p.name AS product_name, u.first_name, u.last_name, u.email
            FROM reviews r JOIN products p ON p.id = r.product_id JOIN users u ON u.id = r.user_id
            """;
    private final JdbcTemplate jdbc;

    public ReviewAdminController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<ReviewResponse> list(@RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String status, HttpSession session) {
        AdminAccess.require(jdbc, session);
        String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        String filter = status.trim().toUpperCase(Locale.ROOT);
        return jdbc.query(REVIEW_SELECT + """
                WHERE LOWER(concat_ws(' ', p.name, u.first_name, u.last_name, u.email, r.title, r.comment)) LIKE ?
                  AND (? = '' OR ? = CASE WHEN r.is_hidden THEN 'HIDDEN'
                                         WHEN r.is_approved THEN 'APPROVED' ELSE 'PENDING' END)
                ORDER BY r.created_at DESC, r.id DESC
                """, (rs, row) -> map(rs), pattern, filter, filter);
    }

    @PutMapping("/{id}/moderation")
    public ReviewResponse moderate(@PathVariable long id, @RequestParam(required = false) Boolean approved,
            @RequestParam(required = false) String status, HttpSession session) {
        long actor = AdminAccess.require(jdbc, session);
        String action = status == null && approved != null ? (approved ? "APPROVED" : "PENDING") : status;
        if (action == null || !List.of("APPROVED", "PENDING", "HIDDEN").contains(action)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid review status.");
        }
        if (jdbc.update("""
                UPDATE reviews SET is_approved = ?, is_hidden = ?, moderated_by_user_id = ?,
                    moderated_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?
                """, action.equals("APPROVED"), action.equals("HIDDEN"), actor, id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found.");
        }
        return jdbc.query(REVIEW_SELECT + "WHERE r.id = ?", rs -> rs.next() ? map(rs) : null, id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id, HttpSession session) {
        AdminAccess.require(jdbc, session);
        if (jdbc.update("DELETE FROM reviews WHERE id = ?", id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found.");
        }
    }

    private ReviewResponse map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ReviewResponse(rs.getLong("id"), rs.getString("product_name"),
                rs.getString("first_name") + " " + rs.getString("last_name"), rs.getString("email"), rs.getInt("rating"),
                rs.getString("title"), rs.getString("comment"), rs.getBoolean("is_verified_purchase"),
                rs.getBoolean("is_approved"), rs.getBoolean("is_hidden"), rs.getObject("created_at", OffsetDateTime.class));
    }

    public record ReviewResponse(long id, String productName, String customerName, String customerEmail, int rating,
            String title, String comment, boolean verifiedPurchase, boolean approved, boolean hidden, OffsetDateTime createdAt) {}
}
