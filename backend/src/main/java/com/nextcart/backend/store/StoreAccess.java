package com.nextcart.backend.store;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

final class StoreAccess {
    static final String ORIGIN = "${nextcart.store.origin:http://localhost:3000}";

    private StoreAccess() {}

    static long customer(JdbcTemplate jdbc, HttpSession session) {
        if (session == null || !(session.getAttribute("userId") instanceof Long id)) {
            throw error(HttpStatus.UNAUTHORIZED, "Sign in to continue.");
        }
        if (!Boolean.TRUE.equals(jdbc.query("""
                SELECT is_active AND role = 'CUSTOMER' FROM users WHERE id = ?
                """, rs -> rs.next() && rs.getBoolean(1), id))) {
            throw error(HttpStatus.FORBIDDEN, "A customer account is required.");
        }
        return id;
    }

    static void lockCustomer(JdbcTemplate jdbc, long id) {
        if (!Boolean.TRUE.equals(jdbc.query("""
                SELECT is_active AND role = 'CUSTOMER' FROM users WHERE id = ? FOR UPDATE
                """, rs -> rs.next() && rs.getBoolean(1), id))) {
            throw error(HttpStatus.FORBIDDEN, "A customer account is required.");
        }
    }

    static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
