
package com.nextcart.backend.admin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

final class AdminAnalytics {
    private final JdbcTemplate jdbc;
    private final Store store;

    AdminAnalytics(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.store = jdbc.queryForObject("SELECT currency_code, timezone FROM store_settings WHERE id = 1",
                (rs, row) -> new Store(rs.getString(1), ZoneId.of(rs.getString(2))));
    }

    String currency() {
        return store.currency();
    }

    Period period(LocalDate from, LocalDate to, int days, String range) {
        LocalDate end = to == null ? LocalDate.now(store.zone()) : to;
        LocalDate calculated = switch (range) {
            case "today" -> end;
            case "7d" -> end.minusDays(6);
            case "30d" -> end.minusDays(29);
            case "3m" -> end.plusDays(1).minusMonths(3);
            default -> end.minusDays(days - 1);
        };
        LocalDate start = from == null ? calculated : from;
        if (start.isAfter(end) || ChronoUnit.DAYS.between(start, end) > 3660
                || start.getYear() < 1000 || end.getYear() > 9998) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a valid date range of up to ten years.");
        }
        return new Period(start, end);
    }

    Period previous(Period selected) {
        long days = ChronoUnit.DAYS.between(selected.from(), selected.to()) + 1;
        return new Period(selected.from().minusDays(days), selected.from().minusDays(1));
    }

    Metrics metrics(Period period) {
        OffsetDateTime start = time(period.from());
        OffsetDateTime end = time(period.to().plusDays(1));
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(total_amount) FILTER (WHERE payment_status = 'PAID' AND currency_code = ?), 0) AS sales,
                       COUNT(*) AS orders,
                       COUNT(*) FILTER (WHERE payment_status = 'PAID' AND currency_code = ?) AS paid_orders,
                       COUNT(*) FILTER (WHERE order_status = 'CANCELLED') AS cancelled
                FROM orders WHERE created_at >= ? AND created_at < ?
                """, (rs, row) -> {
            BigDecimal revenue = rs.getBigDecimal("sales");
            long paid = rs.getLong("paid_orders");
            BigDecimal average = paid == 0 ? BigDecimal.ZERO : revenue.divide(BigDecimal.valueOf(paid), 2, RoundingMode.HALF_UP);
            return new Metrics(revenue, rs.getLong("orders"), average, rs.getLong("cancelled"),
                    count("""
                            SELECT COUNT(*) FROM users WHERE role = 'CUSTOMER' AND created_at >= ? AND created_at < ?
                            """, start, end));
        }, currency(), currency(), start, end);
    }

    long customersBefore(LocalDate date) {
        return count("SELECT COUNT(*) FROM users WHERE role = 'CUSTOMER' AND created_at < ?", time(date));
    }

    long productsBefore(LocalDate date) {
        return count("SELECT COUNT(*) FROM products WHERE created_at < ?", time(date));
    }

    long count(String query, Object... parameters) {
        return jdbc.queryForObject(query, Long.class, parameters);
    }

    List<Bucket> monthly(LocalDate end) {
        return buckets(new Period(end.withDayOfMonth(1).minusMonths(5), end), true);
    }

    List<Bucket> buckets(Period period, boolean monthly) {
        String bucket = monthly ? "date_trunc('month', created_at AT TIME ZONE ?)::date"
                : "(created_at AT TIME ZONE ?)::date";
        var rows = jdbc.query("""
                SELECT %s AS bucket, COUNT(*) AS order_count,
                       COALESCE(SUM(total_amount) FILTER (WHERE payment_status = 'PAID' AND currency_code = ?), 0) AS sales
                FROM orders WHERE created_at >= ? AND created_at < ?
                GROUP BY 1 ORDER BY 1
                """.formatted(bucket), (rs, row) -> new Bucket(rs.getObject("bucket", LocalDate.class),
                rs.getBigDecimal("sales"), rs.getLong("order_count")), store.zone().getId(), currency(),
                time(period.from()), time(period.to().plusDays(1)));
        var dates = new java.util.ArrayList<Bucket>();
        int index = 0;
        for (LocalDate date = monthly ? period.from().withDayOfMonth(1) : period.from();
                !date.isAfter(period.to()); date = monthly ? date.plusMonths(1) : date.plusDays(1)) {
            dates.add(index < rows.size() && rows.get(index).date().equals(date)
                    ? rows.get(index++) : new Bucket(date, BigDecimal.ZERO, 0));
        }
        return dates;
    }

    List<TopProduct> topProducts(Period period) {
        return jdbc.query("""
                SELECT oi.product_name, SUM(oi.quantity) AS units_sold, SUM(oi.subtotal) AS sales
                FROM order_items oi JOIN orders o ON o.id = oi.order_id
                WHERE o.created_at >= ? AND o.created_at < ? AND o.payment_status = 'PAID' AND o.currency_code = ?
                GROUP BY oi.product_name ORDER BY units_sold DESC, oi.product_name LIMIT 5
                """, (rs, row) -> new TopProduct(rs.getString(1), rs.getLong(2), rs.getBigDecimal(3)),
                time(period.from()), time(period.to().plusDays(1)), currency());
    }

    List<OrderStatus> orderStatuses(Period period) {
        return jdbc.query("""
                SELECT order_status, COUNT(*) FROM orders WHERE created_at >= ? AND created_at < ?
                GROUP BY order_status ORDER BY COUNT(*) DESC, order_status
                """, (rs, row) -> new OrderStatus(rs.getString(1), rs.getLong(2)),
                time(period.from()), time(period.to().plusDays(1)));
    }

    List<CategorySales> categorySales(Period period) {
        return jdbc.query("""
                SELECT COALESCE(c.name, 'Uncategorized') AS name, COUNT(DISTINCT oi.product_id) AS products,
                       SUM(oi.subtotal) AS revenue, COUNT(DISTINCT o.id) AS orders,
                       ROUND(SUM(oi.subtotal) / NULLIF(SUM(oi.quantity), 0), 2) AS average_price
                FROM orders o JOIN order_items oi ON oi.order_id = o.id
                LEFT JOIN products p ON p.id = oi.product_id LEFT JOIN categories c ON c.id = p.category_id
                WHERE o.created_at >= ? AND o.created_at < ? AND o.payment_status = 'PAID' AND o.currency_code = ?
                GROUP BY c.id ORDER BY revenue DESC, name
                """, (rs, row) -> new CategorySales(rs.getString(1), rs.getLong(2), rs.getBigDecimal(3),
                rs.getLong(4), rs.getBigDecimal(5)), time(period.from()), time(period.to().plusDays(1)), currency());
    }

    private OffsetDateTime time(LocalDate date) {
        return date.atStartOfDay(store.zone()).toOffsetDateTime();
    }

    record Store(String currency, ZoneId zone) {}
    record Period(LocalDate from, LocalDate to) {}
    record Metrics(BigDecimal revenue, long orders, BigDecimal averageOrderValue, long cancelled, long newCustomers) {}
    record Bucket(LocalDate date, BigDecimal sales, long orderCount) {}
    record TopProduct(String productName, long unitsSold, BigDecimal sales) {}
    record OrderStatus(String status, long count) {}
    record CategorySales(String categoryName, long productCount, BigDecimal revenue, long orders, BigDecimal averagePrice) {}
}
