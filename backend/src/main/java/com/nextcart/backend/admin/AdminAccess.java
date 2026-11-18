
package com.nextcart.backend.admin;

import jakarta.servlet.http.HttpSession;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

final class AdminAccess {
    private AdminAccess() {}

    static long require(JdbcTemplate jdbc, HttpSession session) {
        if (!(session.getAttribute("userId") instanceof Long id)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in is required.");
        }
        String role = jdbc.query("SELECT role FROM users WHERE id = ? AND is_active = TRUE",
                rs -> rs.next() ? rs.getString("role") : null, id);
        if (!"ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access is required.");
        }
        return id;
    }

    static String imageUrl(String source) {
        if (source != null && source.length() <= 2048) {
            try {
                URI uri = URI.create(source.trim());
                if (uri.getHost() != null && ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) {
                    return uri.toString();
                }
            } catch (IllegalArgumentException ignored) {
                // Use the same validation message for all malformed URLs.
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid HTTP or HTTPS image URL.");
    }
}
