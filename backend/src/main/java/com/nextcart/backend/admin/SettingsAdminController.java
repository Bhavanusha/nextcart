package com.nextcart.backend.admin;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/admin/settings")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class SettingsAdminController {
    private final JdbcTemplate jdbc;

    private final ObjectMapper json;

    public SettingsAdminController(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @GetMapping
    public SettingsResponse get(HttpSession session) {
        AdminAccess.require(jdbc, session);
        return find();
    }

    @PutMapping
    @Transactional
    public SettingsResponse update(@Valid @RequestBody SettingsRequest request, HttpSession session) {
        AdminAccess.require(jdbc, session);
        if (request.timezone() != null) {
            try {
                ZoneId.of(request.timezone());
            } catch (DateTimeException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a valid timezone.");
            }
        }
        jdbc.update("""
                UPDATE store_settings SET store_name = ?, contact_email = ?, contact_phone = ?, address_line1 = ?,
                    address_line2 = ?, city = ?, state_or_province = ?, postal_code = ?, country = ?,
                    currency_code = ?, tax_rate = ?, timezone = COALESCE(?, timezone), updated_at = CURRENT_TIMESTAMP
                WHERE id = 1
                """, request.storeName().trim(), request.contactEmail().trim(), request.contactPhone(), request.addressLine1(),
                request.addressLine2(), request.city(), request.stateOrProvince(), request.postalCode(), request.country(),
                request.currencyCode().toUpperCase(Locale.ROOT), request.taxRate(), request.timezone());
        if (request.logoData() != null) {
            if (request.logoData().isEmpty()) {
                jdbc.update("UPDATE store_settings SET logo_url = NULL, logo_content = NULL, logo_content_type = NULL WHERE id = 1");
            } else {
                var file = AdminMediaController.decode(request.logoData());
                jdbc.update("UPDATE store_settings SET logo_url = NULL, logo_content = ?, logo_content_type = ? WHERE id = 1",
                        file.bytes(), file.contentType());
            }
        }
        if (request.preferences() != null) {
            Preferences prefs = request.preferences();
            if ((prefs.deliveryMinDays() == null) != (prefs.deliveryMaxDays() == null)
                    || (prefs.deliveryMinDays() != null && prefs.deliveryMinDays() > prefs.deliveryMaxDays())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid delivery range.");
            }
            jdbc.update("""
                    UPDATE store_settings SET
                      shipping_config = shipping_config || jsonb_build_object('flatRate', ?::numeric, 'freeShippingMinimum', ?::numeric,
                        'deliveryMinDays', ?::integer, 'deliveryMaxDays', ?::integer),
                      payment_config = payment_config || jsonb_build_object('cashOnDelivery', ?::boolean),
                      notification_config = notification_config || jsonb_build_object('orderAlerts', ?::boolean, 'lowStockAlerts', ?::boolean)
                    WHERE id = 1
                    """, prefs.flatRate(), prefs.freeShippingMinimum(), prefs.deliveryMinDays(), prefs.deliveryMaxDays(), prefs.cashOnDelivery(),
                    prefs.orderAlerts(), prefs.lowStockAlerts());
        }
        if (request.storefront() != null) {
            validateImage(request.storefront().heroImage());
            validateImage(request.storefront().storyImage());
            validateImage(request.storefront().campaignImage());
            jdbc.update("UPDATE store_settings SET storefront_content = storefront_content || jsonb_strip_nulls(?::jsonb) WHERE id = 1",
                    json.writeValueAsString(request.storefront()));
        }
        return find();
    }

    private void validateImage(String value) {
        if (value == null || value.isEmpty()) return;
        if (value.startsWith("/") && !value.startsWith("//") && !value.contains("\\")) return;
        AdminAccess.imageUrl(value);
    }

    private SettingsResponse find() {
        return jdbc.query("""
                SELECT store_name, contact_email, contact_phone, address_line1, address_line2, city, state_or_province,
                       postal_code, country, currency_code, tax_rate, timezone, updated_at,
                       CASE WHEN logo_content IS NULL THEN logo_url
                            ELSE '/api/media/store-logo?v=' || EXTRACT(EPOCH FROM updated_at) END AS logo,
                       shipping_config->>'flatRate' AS flat_rate, shipping_config->>'freeShippingMinimum' AS free_minimum,
                       payment_config->>'cashOnDelivery' AS cash_on_delivery,
                       (shipping_config->>'deliveryMinDays')::integer AS delivery_min,
                       (shipping_config->>'deliveryMaxDays')::integer AS delivery_max,
                       notification_config->>'orderAlerts' AS order_alerts, notification_config->>'lowStockAlerts' AS low_stock_alerts,
                       storefront_content::text AS storefront_content
                FROM store_settings WHERE id = 1
                """, rs -> {
            if (!rs.next()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Store settings not found.");
            return new SettingsResponse(rs.getString("store_name"), rs.getString("contact_email"), rs.getString("contact_phone"),
                    rs.getString("address_line1"), rs.getString("address_line2"), rs.getString("city"), rs.getString("state_or_province"),
                    rs.getString("postal_code"), rs.getString("country"), rs.getString("currency_code"), rs.getBigDecimal("tax_rate"),
                    rs.getObject("updated_at", OffsetDateTime.class), rs.getString("timezone"), rs.getString("logo"),
                    new Preferences(rs.getBigDecimal("flat_rate"), rs.getBigDecimal("free_minimum"), rs.getBoolean("cash_on_delivery"),
                            rs.getString("order_alerts") == null || rs.getBoolean("order_alerts"),
                            rs.getString("low_stock_alerts") == null || rs.getBoolean("low_stock_alerts"),
                            rs.getObject("delivery_min", Integer.class), rs.getObject("delivery_max", Integer.class)),
                    json.readTree(rs.getString("storefront_content")));
        });
    }

    public record Preferences(
            @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal flatRate,
            @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal freeShippingMinimum,
            boolean cashOnDelivery, boolean orderAlerts, boolean lowStockAlerts,
            @Min(0) @Max(365) Integer deliveryMinDays, @Min(0) @Max(365) Integer deliveryMaxDays) {}

    public record Storefront(@Size(max = 160) String announcement, @Size(max = 120) String heroTitle,
            @Size(max = 120) String heroAccent, @Size(max = 500) String heroDescription, @Size(max = 2048) String heroImage,
            @Size(max = 120) String storyTitle, @Size(max = 500) String storyDescription, @Size(max = 2048) String storyImage,
            @Size(max = 120) String campaignTitle, @Size(max = 500) String campaignDescription, @Size(max = 2048) String campaignImage) {}

    public record SettingsRequest(
            @NotBlank @Size(max = 150) String storeName, @NotBlank @Email @Size(max = 320) String contactEmail,
            @Size(max = 30) String contactPhone, @Size(max = 255) String addressLine1, @Size(max = 255) String addressLine2,
            @Size(max = 100) String city, @Size(max = 100) String stateOrProvince, @Size(max = 20) String postalCode,
            @Size(max = 100) String country, @NotNull @Pattern(regexp = "[A-Za-z]{3}") String currencyCode,
            @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal taxRate,
            @Size(max = 100) String timezone, String logoData, @Valid Preferences preferences, @Valid Storefront storefront) {}

    public record SettingsResponse(String storeName, String contactEmail, String contactPhone, String addressLine1,
            String addressLine2, String city, String stateOrProvince, String postalCode, String country, String currencyCode,
            BigDecimal taxRate, OffsetDateTime updatedAt, String timezone, String logoUrl, Preferences preferences, JsonNode storefront) {}
}
