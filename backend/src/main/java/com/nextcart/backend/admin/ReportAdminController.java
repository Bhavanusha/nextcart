
package com.nextcart.backend.admin;

import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/reports")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class ReportAdminController {
    private final JdbcTemplate jdbc;

    public ReportAdminController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public ReportResponse report(@RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to, @RequestParam(defaultValue = "30d") String range, HttpSession session) {
        AdminAccess.require(jdbc, session);
        AdminAnalytics analytics = new AdminAnalytics(jdbc);
        var period = analytics.period(from, to, 30, range);
        var totals = analytics.metrics(period);
        var prior = analytics.metrics(analytics.previous(period));
        return new ReportResponse(period.from(), period.to(), totals.revenue(), totals.orders(),
                totals.averageOrderValue(), totals.newCustomers(), analytics.topProducts(period),
                analytics.buckets(period, false), analytics.monthly(period.to()), analytics.orderStatuses(period),
                analytics.categorySales(period), prior, analytics.currency());
    }

    public record ReportResponse(LocalDate from, LocalDate to, BigDecimal totalSales, long totalOrders,
            BigDecimal averageOrderValue, long newCustomers, List<AdminAnalytics.TopProduct> topProducts,
            List<AdminAnalytics.Bucket> salesByDay, List<AdminAnalytics.Bucket> monthlySales,
            List<AdminAnalytics.OrderStatus> orderStatuses, List<AdminAnalytics.CategorySales> categorySales,
            AdminAnalytics.Metrics previous, String currencyCode) {}
}
