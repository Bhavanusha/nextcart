package com.nextcart.backend.admin;

import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/customers")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class CustomerAdminController {

    private final JdbcTemplate jdbcTemplate;

    public CustomerAdminController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public List<CustomerResponse> list(
            @RequestParam(required = false, defaultValue = "") String search,
            @RequestParam(required = false, defaultValue = "") String status,
            HttpSession session) {
        requireAdmin(session);
        String pattern = "%" + search.trim().toLowerCase() + "%";
        String normalizedStatus = status.trim().toUpperCase();
        return jdbcTemplate.query("""
                SELECT u.id, u.first_name, u.last_name, u.email, u.phone, u.is_active, u.created_at,
                       COUNT(DISTINCT o.id) AS order_count,
                       COALESCE(SUM(CASE WHEN o.payment_status = 'PAID' THEN o.total_amount ELSE 0 END), 0) AS total_spent,
                       ARRAY(SELECT concat(trim(paid.currency_code), ':', SUM(paid.total_amount))
                             FROM orders paid WHERE paid.user_id = u.id AND paid.payment_status = 'PAID'
                             GROUP BY paid.currency_code ORDER BY paid.currency_code) AS spending
                FROM users u
                LEFT JOIN orders o ON o.user_id = u.id
                WHERE u.role = 'CUSTOMER'
                  AND (LOWER(u.first_name) LIKE ? OR LOWER(u.last_name) LIKE ? OR LOWER(u.email) LIKE ?)
                  AND (? = '' OR (? = 'ACTIVE' AND u.is_active = TRUE) OR (? = 'INACTIVE' AND u.is_active = FALSE))
                GROUP BY u.id
                ORDER BY u.created_at DESC
                """, (rs, rowNum) -> new CustomerResponse(
                rs.getLong("id"), rs.getString("first_name"), rs.getString("last_name"),
                rs.getString("email"), rs.getString("phone"), rs.getBoolean("is_active"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getLong("order_count"),
                rs.getBigDecimal("total_spent"), spending(rs)), pattern, pattern, pattern,
                normalizedStatus, normalizedStatus, normalizedStatus);
    }

    @PutMapping("/{id}/status")
    public CustomerResponse updateStatus(
            @PathVariable long id,
            @RequestParam boolean active,
            HttpSession session) {
        requireAdmin(session);
        int updated = jdbcTemplate.update("""
                UPDATE users SET is_active = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND role = 'CUSTOMER'
                """, active, id);
        if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found.");
        return findById(id);
    }

    private CustomerResponse findById(long id) {
        return jdbcTemplate.query("""
                SELECT u.id, u.first_name, u.last_name, u.email, u.phone, u.is_active, u.created_at,
                       COUNT(DISTINCT o.id) AS order_count,
                       COALESCE(SUM(CASE WHEN o.payment_status = 'PAID' THEN o.total_amount ELSE 0 END), 0) AS total_spent,
                       ARRAY(SELECT concat(trim(paid.currency_code), ':', SUM(paid.total_amount))
                             FROM orders paid WHERE paid.user_id = u.id AND paid.payment_status = 'PAID'
                             GROUP BY paid.currency_code ORDER BY paid.currency_code) AS spending
                FROM users u LEFT JOIN orders o ON o.user_id = u.id
                WHERE u.id = ? AND u.role = 'CUSTOMER' GROUP BY u.id
                """, result -> result.next() ? new CustomerResponse(
                result.getLong("id"), result.getString("first_name"), result.getString("last_name"),
                result.getString("email"), result.getString("phone"), result.getBoolean("is_active"),
                result.getObject("created_at", OffsetDateTime.class), result.getLong("order_count"),
                result.getBigDecimal("total_spent"), spending(result)) : null, id);
    }

    private List<Spending> spending(ResultSet rs) throws SQLException {
        var array = rs.getArray("spending");
        try {
            List<Spending> values = new ArrayList<>();
            for (Object entry : (Object[]) array.getArray()) {
                String[] parts = entry.toString().split(":", 2);
                values.add(new Spending(parts[0], new BigDecimal(parts[1])));
            }
            return values;
        } finally {
            array.free();
        }
    }

    public record Spending(String currency, BigDecimal amount) {}

    private void requireAdmin(HttpSession session) {
        Object userId = session.getAttribute("userId");
        if (!(userId instanceof Long id)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in is required.");
        String role = jdbcTemplate.query("SELECT role FROM users WHERE id = ? AND is_active = TRUE",
                result -> result.next() ? result.getString("role") : null, id);
        if (!"ADMIN".equals(role)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access is required.");
    }

    public record CustomerResponse(
            long id, String firstName, String lastName, String email, String phone, boolean active,
            OffsetDateTime joinedAt, long orderCount, BigDecimal totalSpent, List<Spending> spending) {
    }
}
