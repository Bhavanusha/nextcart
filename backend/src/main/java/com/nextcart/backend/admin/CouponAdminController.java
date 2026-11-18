package com.nextcart.backend.admin;

import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/coupons")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class CouponAdminController {

    private final JdbcTemplate jdbcTemplate;

    public CouponAdminController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public List<CouponResponse> list(@RequestParam(defaultValue = "") String search, HttpSession session) {
        requireAdmin(session);
        String pattern = "%" + search.trim().toLowerCase() + "%";
        return jdbcTemplate.query("""
                SELECT id, code, description, discount_type, discount_value, minimum_order_amount,
                       maximum_discount, usage_limit, used_count, starts_at, expires_at, is_active, created_at,
                       CASE WHEN NOT is_active THEN 'INACTIVE'
                            WHEN expires_at <= CURRENT_TIMESTAMP OR used_count >= usage_limit THEN 'EXPIRED'
                            WHEN starts_at > CURRENT_TIMESTAMP THEN 'SCHEDULED' ELSE 'ACTIVE' END AS coupon_status
                FROM coupons WHERE LOWER(code) LIKE ? OR LOWER(COALESCE(description, '')) LIKE ?
                ORDER BY created_at DESC
                """, (rs, rowNum) -> mapCoupon(rs), pattern, pattern);
    }

    @PostMapping
    public CouponResponse create(@RequestBody CouponRequest request, HttpSession session) {
        requireAdmin(session);
        validate(request);
        long id = jdbcTemplate.queryForObject("""
                INSERT INTO coupons (code, description, discount_type, discount_value, minimum_order_amount,
                    maximum_discount, usage_limit, starts_at, expires_at, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, COALESCE(?, TRUE)) RETURNING id
                """, Long.class, normalizeCode(request.code()), request.description(), request.discountType(),
                request.discountValue(), request.minimumOrderAmount(), request.maximumDiscount(),
                request.usageLimit(), request.startsAt(), request.expiresAt(), request.active());
        return findById(id);
    }

    @PutMapping("/{id}")
    public CouponResponse update(@PathVariable long id, @RequestBody CouponRequest request, HttpSession session) {
        requireAdmin(session);
        validate(request);
        int updated = jdbcTemplate.update("""
                UPDATE coupons SET code = ?, description = ?, discount_type = ?, discount_value = ?,
                    minimum_order_amount = ?, maximum_discount = ?, usage_limit = ?, starts_at = ?,
                    expires_at = ?, is_active = COALESCE(?, is_active), updated_at = CURRENT_TIMESTAMP WHERE id = ?
                """, normalizeCode(request.code()), request.description(), request.discountType(),
                request.discountValue(), request.minimumOrderAmount(), request.maximumDiscount(),
                request.usageLimit(), request.startsAt(), request.expiresAt(), request.active(), id);
        if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Coupon not found.");
        return findById(id);
    }

    @DeleteMapping("/{id}")
    public void deactivate(@PathVariable long id, HttpSession session) {
        requireAdmin(session);
        if (jdbcTemplate.update("UPDATE coupons SET is_active = FALSE, updated_at = CURRENT_TIMESTAMP WHERE id = ?", id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Coupon not found.");
        }
    }

    private CouponResponse findById(long id) {
        return jdbcTemplate.query("""
                SELECT id, code, description, discount_type, discount_value, minimum_order_amount, maximum_discount,
                       usage_limit, used_count, starts_at, expires_at, is_active, created_at,
                       CASE WHEN NOT is_active THEN 'INACTIVE'
                            WHEN expires_at <= CURRENT_TIMESTAMP OR used_count >= usage_limit THEN 'EXPIRED'
                            WHEN starts_at > CURRENT_TIMESTAMP THEN 'SCHEDULED' ELSE 'ACTIVE' END AS coupon_status
                FROM coupons WHERE id = ?
                """,
                result -> result.next() ? mapCoupon(result) : null, id);
    }

    private CouponResponse mapCoupon(java.sql.ResultSet result) throws java.sql.SQLException {
        return new CouponResponse(result.getLong("id"), result.getString("code"), result.getString("description"),
                result.getString("discount_type"), result.getBigDecimal("discount_value"),
                result.getBigDecimal("minimum_order_amount"), result.getBigDecimal("maximum_discount"),
                (Integer) result.getObject("usage_limit"), result.getInt("used_count"),
                result.getObject("starts_at", OffsetDateTime.class), result.getObject("expires_at", OffsetDateTime.class),
                result.getBoolean("is_active"), result.getObject("created_at", OffsetDateTime.class), result.getString("coupon_status"));
    }

    private void validate(CouponRequest request) {
        if (request.code() == null || request.code().isBlank() || request.code().trim().length() > 50
                || request.discountType() == null || !List.of("FIXED_AMOUNT", "PERCENTAGE").contains(request.discountType())
                || request.minimumOrderAmount() == null || request.minimumOrderAmount().signum() < 0
                || (request.maximumDiscount() != null && request.maximumDiscount().signum() <= 0)
                || (request.usageLimit() != null && request.usageLimit() <= 0)
                || request.discountValue() == null || request.discountValue().signum() <= 0
                || request.startsAt() == null || (request.expiresAt() != null && !request.expiresAt().isAfter(request.startsAt()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid coupon details.");
        }
        if ("PERCENTAGE".equals(request.discountType()) && request.discountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Percentage discount cannot exceed 100.");
        }
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(DuplicateKeyException.class)
    org.springframework.http.ResponseEntity<java.util.Map<String, String>> duplicateCode() {
        return org.springframework.http.ResponseEntity.status(HttpStatus.CONFLICT)
                .body(java.util.Map.of("detail", "A coupon with this code already exists."));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(DataIntegrityViolationException.class)
    org.springframework.http.ResponseEntity<java.util.Map<String, String>> invalidValues() {
        return org.springframework.http.ResponseEntity.badRequest()
                .body(java.util.Map.of("detail", "Coupon values exceed allowed limits or current usage."));
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase();
    }

    private void requireAdmin(HttpSession session) {
        Object userId = session.getAttribute("userId");
        if (!(userId instanceof Long id)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in is required.");
        String role = jdbcTemplate.query("SELECT role FROM users WHERE id = ? AND is_active = TRUE",
                result -> result.next() ? result.getString("role") : null, id);
        if (!"ADMIN".equals(role)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access is required.");
    }

    public record CouponRequest(
            String code, String description, String discountType, BigDecimal discountValue,
            BigDecimal minimumOrderAmount, BigDecimal maximumDiscount, Integer usageLimit,
            OffsetDateTime startsAt, OffsetDateTime expiresAt, Boolean active) {}

    public record CouponResponse(
            long id, String code, String description, String discountType, BigDecimal discountValue,
            BigDecimal minimumOrderAmount, BigDecimal maximumDiscount, Integer usageLimit, int usedCount,
            OffsetDateTime startsAt, OffsetDateTime expiresAt, boolean active, OffsetDateTime createdAt, String status) {}
}
