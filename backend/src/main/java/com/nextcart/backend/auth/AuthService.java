package com.nextcart.backend.auth;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AuthController.AuthResponse register(AuthController.RegisterRequest request) {
        String sql = """
                INSERT INTO users (first_name, last_name, email, phone, password_hash)
                VALUES (?, ?, ?, ?, ?)
                RETURNING id, first_name, last_name, email, role
                """;
        try {
            return jdbcTemplate.queryForObject(sql,
                    (rs, rowNum) -> new AuthController.AuthResponse(
                            rs.getLong("id"),
                            rs.getString("first_name"),
                            rs.getString("last_name"),
                            rs.getString("email"),
                            rs.getString("role")),
                    request.firstName().trim(),
                    request.lastName().trim(),
                    request.email().trim().toLowerCase(),
                    request.phone(),
                    passwordEncoder.encode(request.password()));
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account already exists for that email.");
        }
    }

    public AuthController.AuthResponse login(AuthController.LoginRequest request) {
        Map<String, Object> user = findUserByEmail(request.email());
        if (user == null || !Boolean.TRUE.equals(user.get("is_active"))
                || !passwordEncoder.matches(request.password(), (String) user.get("password_hash"))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        }
        return toResponse(user);
    }

    public void createPasswordResetToken(String email) {
        Map<String, Object> user = findUserByEmail(email);
        if (user == null) {
            return;
        }
        String token = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                UPDATE users
                SET password_reset_token = ?, password_reset_expires_at = ?
                WHERE id = ?
                """, token, OffsetDateTime.now().plusMinutes(30), user.get("id"));
    }

    public void resetPassword(AuthController.ResetPasswordRequest request) {
        int updated = jdbcTemplate.update("""
                UPDATE users
                SET password_hash = ?, password_reset_token = NULL, password_reset_expires_at = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE password_reset_token = ? AND password_reset_expires_at > CURRENT_TIMESTAMP
                """, passwordEncoder.encode(request.password()), request.token());
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The reset token is invalid or expired.");
        }
    }

    private Map<String, Object> findUserByEmail(String email) {
        return jdbcTemplate.query("""
                SELECT id, first_name, last_name, email, role, is_active, password_hash
                FROM users WHERE LOWER(email) = LOWER(?)
                """, rs -> rs.next() ? Map.of(
                "id", rs.getLong("id"),
                "first_name", rs.getString("first_name"),
                "last_name", rs.getString("last_name"),
                "email", rs.getString("email"),
                "role", rs.getString("role"),
                "is_active", rs.getBoolean("is_active"),
                "password_hash", rs.getString("password_hash")) : null, email);
    }

    private AuthController.AuthResponse toResponse(Map<String, Object> user) {
        return new AuthController.AuthResponse(
                (Long) user.get("id"),
                (String) user.get("first_name"),
                (String) user.get("last_name"),
                (String) user.get("email"),
                (String) user.get("role"));
    }
}
