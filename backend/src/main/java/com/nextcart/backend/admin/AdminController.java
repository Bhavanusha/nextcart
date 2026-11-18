
package com.nextcart.backend.admin;

import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class AdminController {
    private final JdbcTemplate jdbc;

    public AdminController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/session")
    public AdminSession session(HttpSession session) {
        long id = AdminAccess.require(jdbc, session);
        return jdbc.query("""
                SELECT first_name, last_name FROM users WHERE id = ?
                """, rs -> rs.next() ? new AdminSession(rs.getString(1), rs.getString(2)) : null, id);
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard(@RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to, @RequestParam(defaultValue = "7d") String range, HttpSession session) {
        AdminAccess.require(jdbc, session);
        AdminAnalytics analytics = new AdminAnalytics(jdbc);
        var period = analytics.period(from, to, 7, range);
        var totals = analytics.metrics(period);
        var prior = analytics.metrics(analytics.previous(period));
        return new DashboardResponse(totals.orders(), analytics.customersBefore(period.to().plusDays(1)),
                analytics.productsBefore(period.to().plusDays(1)), totals.revenue(),
                analytics.count("SELECT COUNT(*) FROM orders WHERE order_status = 'PENDING'"),
                analytics.count("SELECT COUNT(*) FROM products WHERE is_active AND stock_quantity <= reserved_quantity"),
                totals.cancelled(), recentOrders(), analytics.topProducts(period),
                analytics.count("""
                        SELECT COUNT(*) FROM products WHERE is_active AND stock_quantity - reserved_quantity <= low_stock_threshold
                        """), analytics.monthly(period.to()), analytics.buckets(new AdminAnalytics.Period(period.to().minusDays(6), period.to()), false),
                analytics.currency(), new Previous(prior.revenue(), prior.orders(), analytics.customersBefore(period.from()),
                analytics.productsBefore(period.from())), OffsetDateTime.now());
    }

    private List<RecentOrder> recentOrders() {
        return jdbc.query("""
                SELECT id, order_number, shipping_recipient_name, total_amount, order_status, currency_code
                FROM orders ORDER BY created_at DESC, id DESC LIMIT 5
                """, (rs, row) -> new RecentOrder(rs.getString("order_number"), rs.getString("shipping_recipient_name"),
                rs.getBigDecimal("total_amount"), rs.getString("order_status"), rs.getLong("id"), rs.getString("currency_code")));
    }

    public record AdminSession(String firstName, String lastName) {}
    public record DashboardResponse(long totalOrders, long totalCustomers, long totalProducts, BigDecimal totalSales,
            long pendingOrders, long outOfStockProducts, long cancelledOrders, List<RecentOrder> recentOrders,
            List<AdminAnalytics.TopProduct> topProducts, long lowStockProducts, List<AdminAnalytics.Bucket> monthlySales,
            List<AdminAnalytics.Bucket> weeklyOrders, String currencyCode, Previous previous, OffsetDateTime updatedAt) {}
    public record RecentOrder(String orderNumber, String customerName, BigDecimal total, String status, long id, String currencyCode) {}
    public record Previous(BigDecimal totalSales, long totalOrders, long totalCustomers, long totalProducts) {}
}
