package com.nextcart.backend.store;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/store")
@CrossOrigin(origins = StoreAccess.ORIGIN, allowCredentials = "true")
public class AccountController {
    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

    public AccountController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/account")
    public Profile profile(HttpSession session) { return profile(StoreAccess.customer(jdbc, session)); }

    Profile profile(long id) {
        return jdbc.queryForObject("""
                SELECT first_name, last_name, email, phone, avatar_data, avatar_type,
                    (SELECT COUNT(*) FROM orders WHERE user_id = u.id),
                    (SELECT COUNT(*) FROM wishlist_items wi JOIN wishlists w ON w.id = wi.wishlist_id
                     JOIN products p ON p.id = wi.product_id WHERE w.user_id = u.id AND p.is_active),
                    (SELECT COUNT(*) FROM addresses WHERE user_id = u.id)
                FROM users u WHERE u.id = ?
                """, (rs, row) -> {
            byte[] bytes = rs.getBytes(5);
            String avatar = bytes == null ? null : "data:" + rs.getString(6) + ";base64," + Base64.getEncoder().encodeToString(bytes);
            return new Profile(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), avatar,
                    rs.getLong(7), rs.getLong(8), rs.getLong(9));
        }, id);
    }

    @PutMapping("/account")
    @Transactional
    public Profile update(@Valid @RequestBody ProfileRequest input, HttpSession session) {
        long user = StoreAccess.customer(jdbc, session);
        StoreAccess.lockCustomer(jdbc, user);
        if (input.phone() != null && !input.phone().isBlank() && !validPhone(input.phone())) {
            throw StoreAccess.error(HttpStatus.BAD_REQUEST, "Enter a valid phone number.");
        }
        String email = input.email().trim().toLowerCase(Locale.ROOT);
        if (input.avatar() != null) {
            CatalogController.Binary avatar = avatar(input.avatar());
            jdbc.update("UPDATE users SET avatar_data = ?, avatar_type = ? WHERE id = ?",
                    avatar == null ? null : avatar.bytes(), avatar == null ? null : avatar.type(), user);
        }
        try {
            jdbc.update("""
                    UPDATE users SET first_name = ?, last_name = ?, email = ?, phone = ?,
                        email_verified = email_verified AND lower(email) = ?,
                        password_reset_token = CASE WHEN lower(email) = ? THEN password_reset_token ELSE NULL END,
                        password_reset_expires_at = CASE WHEN lower(email) = ? THEN password_reset_expires_at ELSE NULL END,
                        updated_at = CURRENT_TIMESTAMP WHERE id = ?
                    """, input.firstName().trim(), input.lastName().trim(), email, input.phone(),
                    email, email, email, user);
        } catch (DuplicateKeyException exception) {
            throw StoreAccess.error(HttpStatus.CONFLICT, "This email is already in use.");
        }
        return profile(user);
    }

    @PutMapping("/account/password")
    @Transactional
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void password(@Valid @RequestBody PasswordRequest input, HttpSession session) {
        long user = StoreAccess.customer(jdbc, session);
        StoreAccess.lockCustomer(jdbc, user);
        String current = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, user);
        if (input.newPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw StoreAccess.error(HttpStatus.BAD_REQUEST, "Use a password with at most 72 UTF-8 bytes.");
        }
        if (input.currentPassword().getBytes(StandardCharsets.UTF_8).length > 72 || !passwords.matches(input.currentPassword(), current)) {
            throw StoreAccess.error(HttpStatus.BAD_REQUEST, "Current password is incorrect.");
        }
        jdbc.update("""
                UPDATE users SET password_hash = ?, password_reset_token = NULL, password_reset_expires_at = NULL,
                    updated_at = CURRENT_TIMESTAMP WHERE id = ?
                """, passwords.encode(input.newPassword()), user);
    }

    @GetMapping("/addresses")
    public List<Address> addresses(HttpSession session) {
        return addresses(StoreAccess.customer(jdbc, session));
    }

    List<Address> addresses(long user) {
        return jdbc.query("""
                SELECT id, label, recipient_name, phone, address_line1, address_line2, city, state_or_province,
                       postal_code, country, is_default FROM addresses WHERE user_id = ? ORDER BY is_default DESC, id
                """, (rs, row) -> new Address(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
                rs.getString(9), rs.getString(10), rs.getBoolean(11)), user);
    }

    @PostMapping("/addresses")
    @Transactional
    public List<Address> createAddress(@Valid @RequestBody AddressInput input, HttpSession session) {
        long user = StoreAccess.customer(jdbc, session);
        StoreAccess.lockCustomer(jdbc, user);
        saveAddress(user, input);
        return addresses(user);
    }

    void saveAddress(long user, AddressInput input) {
        requirePhone(input.phone());
        jdbc.update("""
                INSERT INTO addresses (user_id, label, recipient_name, phone, address_line1, address_line2, city,
                                       state_or_province, postal_code, country, is_default)
                SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOT EXISTS (SELECT 1 FROM addresses WHERE user_id = ?)
                """, user, input.label().trim(), input.name().trim(), input.phone().trim(), input.line1().trim(), input.line2(),
                input.city().trim(), input.state().trim(), input.postal().trim(), input.country().trim(), user);
    }

    @PutMapping("/addresses/{id}")
    @Transactional
    public List<Address> updateAddress(@PathVariable long id, @Valid @RequestBody AddressInput input, HttpSession session) {
        long user = StoreAccess.customer(jdbc, session);
        StoreAccess.lockCustomer(jdbc, user);
        requirePhone(input.phone());
        requireAddress(jdbc.update("""
                UPDATE addresses SET label = ?, recipient_name = ?, phone = ?, address_line1 = ?, address_line2 = ?,
                    city = ?, state_or_province = ?, postal_code = ?, country = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND user_id = ?
                """, input.label().trim(), input.name().trim(), input.phone().trim(), input.line1().trim(), input.line2(),
                input.city().trim(), input.state().trim(), input.postal().trim(), input.country().trim(), id, user));
        return addresses(user);
    }

    @PutMapping("/addresses/{id}/default")
    @Transactional
    public List<Address> defaultAddress(@PathVariable long id, HttpSession session) {
        long user = StoreAccess.customer(jdbc, session);
        StoreAccess.lockCustomer(jdbc, user);
        jdbc.update("UPDATE addresses SET is_default = FALSE WHERE user_id = ? AND is_default", user);
        requireAddress(jdbc.update("UPDATE addresses SET is_default = TRUE WHERE id = ? AND user_id = ?", id, user));
        return addresses(user);
    }

    @DeleteMapping("/addresses/{id}")
    @Transactional
    public List<Address> deleteAddress(@PathVariable long id, HttpSession session) {
        long user = StoreAccess.customer(jdbc, session);
        StoreAccess.lockCustomer(jdbc, user);
        requireAddress(jdbc.update("DELETE FROM addresses WHERE id = ? AND user_id = ?", id, user));
        jdbc.update("""
                UPDATE addresses SET is_default = TRUE WHERE id = (SELECT min(id) FROM addresses WHERE user_id = ?)
                    AND NOT EXISTS (SELECT 1 FROM addresses WHERE user_id = ? AND is_default)
                """, user, user);
        return addresses(user);
    }

    @GetMapping("/wishlist")
    public List<CatalogController.ProductCard> wishlist(HttpSession session) {
        return wishlist(StoreAccess.customer(jdbc, session));
    }

    private List<CatalogController.ProductCard> wishlist(long user) {
        return jdbc.query(CatalogController.CATEGORY_TREE + CatalogController.CARDS + """
                JOIN wishlist_items wi ON wi.product_id = p.id JOIN wishlists w ON w.id = wi.wishlist_id
                WHERE w.user_id = ? AND
                """ + CatalogController.PUBLISHED + " ORDER BY wi.added_at DESC, wi.id DESC",
                (rs, row) -> CatalogController.card(rs), user);
    }

    @PutMapping("/wishlist/{product}")
    @Transactional
    public List<CatalogController.ProductCard> wish(@PathVariable long product, @Valid @RequestBody Wish input, HttpSession session) {
        long user = StoreAccess.customer(jdbc, session);
        StoreAccess.lockCustomer(jdbc, user);
        if (input.saved()) {
            if (jdbc.queryForObject(CatalogController.CATEGORY_TREE + """
                    SELECT COUNT(*) FROM products p LEFT JOIN visible_categories c ON p.category_id = c.id WHERE p.id = ? AND
                    """ + CatalogController.PUBLISHED, Long.class, product) == 0) {
                throw StoreAccess.error(HttpStatus.NOT_FOUND, "Product not found.");
            }
            jdbc.update("INSERT INTO wishlists(user_id) VALUES (?) ON CONFLICT DO NOTHING", user);
            jdbc.update("""
                    INSERT INTO wishlist_items(wishlist_id, product_id)
                    SELECT id, ? FROM wishlists WHERE user_id = ? ON CONFLICT DO NOTHING
                    """, product, user);
        } else {
            jdbc.update("""
                    DELETE FROM wishlist_items wi USING wishlists w WHERE wi.wishlist_id = w.id AND w.user_id = ? AND wi.product_id = ?
                    """, user, product);
        }
        return wishlist(user);
    }

    private static void requireAddress(int changed) {
        if (changed == 0) throw StoreAccess.error(HttpStatus.NOT_FOUND, "Address not found.");
    }

    static void requirePhone(String phone) {
        if (!validPhone(phone)) throw StoreAccess.error(HttpStatus.BAD_REQUEST, "Enter a valid phone number.");
    }

    private static boolean validPhone(String phone) {
        return phone.trim().matches("[+0-9(). -]+") && phone.replaceAll("[^0-9]", "").matches("[0-9]{7,15}");
    }

    private CatalogController.Binary avatar(String value) {
        if (value.isEmpty()) return null;
        if (value.length() < 2_800_000) {
            int comma = value.indexOf(',');
            if (comma > 0) {
                String type = switch (value.substring(0, comma)) {
                    case "data:image/png;base64" -> "image/png";
                    case "data:image/jpeg;base64" -> "image/jpeg";
                    case "data:image/gif;base64" -> "image/gif";
                    default -> "";
                };
                try {
                    byte[] bytes = Base64.getDecoder().decode(value.substring(comma + 1));
                    boolean matches = bytes.length >= 8 && switch (type) {
                        case "image/png" -> (bytes[0] & 255) == 137 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
                        case "image/jpeg" -> (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255;
                        case "image/gif" -> bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F';
                        default -> false;
                    };
                    if (matches && bytes.length <= 2 * 1024 * 1024) return new CatalogController.Binary(bytes, type);
                } catch (IllegalArgumentException ignored) {
                    // Return the same response for malformed data and unsupported files.
                }
            }
        }
        throw StoreAccess.error(HttpStatus.BAD_REQUEST, "Choose a JPG, PNG or GIF image up to 2 MB.");
    }

    record Profile(String firstName, String lastName, String email, String phone, String avatar,
            long orderCount, long wishlistCount, long addressCount) {}
    record ProfileRequest(@NotBlank @Size(max = 100) String firstName, @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Email @Size(max = 320) String email, @Size(max = 30) String phone, String avatar) {}
    record PasswordRequest(@NotBlank @Size(max = 72) String currentPassword,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {}
    record Address(long id, String label, String name, String phone, String line1, String line2, String city, String state,
            String postal, String country, boolean primary) {}
    record AddressInput(@NotBlank @Size(max = 40) String label, @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 30) String phone, @NotBlank @Size(max = 255) String line1, @Size(max = 255) String line2,
            @NotBlank @Size(max = 100) String city, @NotBlank @Size(max = 100) String state,
            @NotBlank @Size(max = 20) String postal, @NotBlank @Size(max = 100) String country) {}
    record Wish(@NotNull Boolean saved) {}
}
