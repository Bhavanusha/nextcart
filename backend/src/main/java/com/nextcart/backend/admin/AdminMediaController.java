
package com.nextcart.backend.admin;

import jakarta.servlet.http.HttpSession;
import java.util.Base64;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/media")
public class AdminMediaController {
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final Pattern IMAGE_DATA = Pattern.compile("^data:(image/png|image/jpeg);base64,");
    private final JdbcTemplate jdbc;

    public AdminMediaController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<byte[]> product(@PathVariable long id, HttpSession session) {
        AdminAccess.require(jdbc, session);
        return response(jdbc.query("""
                SELECT image_content, content_type FROM product_images WHERE id = ?
                """, rs -> rs.next() ? new Image(rs.getBytes(1), rs.getString(2)) : null, id));
    }

    @GetMapping("/store-logo")
    public ResponseEntity<byte[]> logo() {
        return response(jdbc.query("""
                SELECT logo_content, logo_content_type FROM store_settings WHERE id = 1
                """, rs -> rs.next() ? new Image(rs.getBytes(1), rs.getString(2)) : null));
    }

    private ResponseEntity<byte[]> response(Image image) {
        if (image == null || image.bytes() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found.");
        }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType()))
                .header("X-Content-Type-Options", "nosniff").body(image.bytes());
    }

    static Image decode(String source) {
        if (source != null && source.length() <= (MAX_BYTES * 4 / 3) + 32) {
            var prefix = IMAGE_DATA.matcher(source);
            if (prefix.find()) {
                try {
                    byte[] bytes = Base64.getDecoder().decode(source.substring(prefix.end()));
                    String type = prefix.group(1);
                    boolean png = bytes.length >= 8 && (bytes[0] & 255) == 137 && bytes[1] == 80
                            && bytes[2] == 78 && bytes[3] == 71 && bytes[4] == 13
                            && bytes[5] == 10 && bytes[6] == 26 && bytes[7] == 10;
                    boolean jpeg = bytes.length >= 3 && (bytes[0] & 255) == 255
                            && (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255;
                    if (bytes.length <= MAX_BYTES && (type.equals("image/png") ? png : jpeg)) {
                        return new Image(bytes, type);
                    }
                } catch (IllegalArgumentException ignored) {
                    // Return the same validation error for malformed data and unsupported images.
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a PNG or JPEG image up to 10 MB.");
    }

    record Image(byte[] bytes, String contentType) {}
}
