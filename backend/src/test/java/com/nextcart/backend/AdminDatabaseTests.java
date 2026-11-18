
package com.nextcart.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@Transactional
class AdminDatabaseTests {
    private static final String PNG =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl6zAAAAABJRU5ErkJggg==";

    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    private MockMvc mvc;
    private MockHttpSession session;
    private String suffix;

    @BeforeEach
    void loginFixture() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        suffix = UUID.randomUUID().toString();
        long id = jdbc.queryForObject("""
                INSERT INTO users(first_name, last_name, email, password_hash, role)
                VALUES ('QA', 'Administrator', ?, 'unused', 'ADMIN') RETURNING id
                """, Long.class, suffix + "@example.test");
        session = new MockHttpSession();
        session.setAttribute("userId", id);
    }

    @Test
    void catalogChangesAndStockAdjustmentArePersisted() throws Exception {
        var category = mvc.perform(post("/api/admin/categories").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Catalog %s","active":true}
                                """.formatted(suffix)))
                .andExpect(status().isCreated()).andReturn();
        long categoryId = json.readTree(category.getResponse().getContentAsString()).get("id").asLong();
        long child = jdbc.queryForObject("""
                INSERT INTO categories(name, slug, parent_id) VALUES ('QA child', ?, ?) RETURNING id
                """, Long.class, "child-" + suffix, categoryId);
        mvc.perform(put("/api/admin/categories/{id}", categoryId).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Catalog %s","parentId":%d}
                                """.formatted(suffix, child)))
                .andExpect(status().isBadRequest());

        var product = mvc.perform(post("/api/admin/products").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Product %s","sku":"%s","categoryId":%d,"brandName":"QA Brand",
                                 "stockQuantity":10,"price":12,"compareAtPrice":15,"metaTitle":"Product preview",
                                 "status":"DRAFT","currencyCode":"USD",
                                 "variants":[{"color":"Blue","size":"M","skuOverride":"%s-blue","price":13}],
                                 "gallery":[{"source":"%s"}]}
                                """.formatted(suffix, suffix, categoryId, suffix, PNG)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.active").value(false)).andReturn();
        var saved = json.readTree(product.getResponse().getContentAsString());
        long id = saved.get("id").asLong();
        mvc.perform(get(saved.get("imageUrl").asText())).andExpect(status().isUnauthorized());
        mvc.perform(get(saved.get("imageUrl").asText()).session(session)).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
        mvc.perform(get("/api/admin/products/{id}/images", id).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].primary").value(true));
        mvc.perform(get("/api/admin/products/{id}/variants", id).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].color").value("Blue"));

        jdbc.update("UPDATE products SET reserved_quantity = 3 WHERE id = ?", id);
        mvc.perform(post("/api/admin/inventory/{id}/adjust", id).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"adjustmentQuantity":-4,"reason":"DAMAGE"}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.availableQuantity").value(3))
                .andExpect(jsonPath("$.status").value("LOW_STOCK"));
        assertEquals("DAMAGE", jdbc.queryForObject(
                "SELECT adjustment_reason FROM inventory_transactions WHERE product_id = ?", String.class, id));

        mvc.perform(put("/api/admin/products/{id}", id).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Product %s","sku":"%s","categoryId":%d,"stockQuantity":10,
                                 "expectedStockQuantity":10,"price":12,"status":"ACTIVE","currencyCode":"USD"}
                                """.formatted(suffix, suffix, child)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stockQuantity").value(6))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM product_images WHERE product_id = ?", Long.class, id));

        mvc.perform(delete("/api/admin/products/{id}", id).session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/products/{id}", id).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    void reportingAndOrderHistoryUseLiveData() throws Exception {
        long customer = jdbc.queryForObject("""
                INSERT INTO users(first_name, last_name, email, password_hash) VALUES ('QA', 'Customer', ?, 'unused')
                RETURNING id
                """, Long.class, "customer-" + suffix + "@example.test");
        String currency = jdbc.queryForObject("SELECT currency_code FROM store_settings WHERE id = 1", String.class);
        long orderId = jdbc.queryForObject("""
                INSERT INTO orders(order_number, user_id, subtotal, total_amount, payment_status, currency_code,
                    shipping_recipient_name, shipping_phone, shipping_address_line1, shipping_city, shipping_state_or_province,
                    shipping_postal_code, shipping_country)
                VALUES (?, ?, 24, 24, 'PAID', ?, 'QA Customer', '5550100', 'Test Street', 'Portland', 'Oregon', '97201', 'US')
                RETURNING id
                """, Long.class, suffix, customer, currency);
        jdbc.update("""
                INSERT INTO order_items (order_id, product_name, product_sku, quantity, unit_price, subtotal, variant_label)
                VALUES (?, 'Test Product', 'QA-SKU', 2, 12, 24, 'Ocean / M')
                """, orderId);

        mvc.perform(put("/api/admin/orders/{id}/status", orderId).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"SHIPPED","trackingNumber":"QA-0123"}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.itemCount").value(2));
        mvc.perform(post("/api/admin/orders/{id}/notes", orderId).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"note":"QA internal note"}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.author").value("QA Administrator"));
        mvc.perform(get("/api/admin/orders/{id}/detail", orderId).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.timeline[0].status").value("SHIPPED"))
                .andExpect(jsonPath("$.summary.trackingNumber").value("QA-0123"))
                .andExpect(jsonPath("$.items[0].variantLabel").value("Ocean / M"));
        mvc.perform(get("/api/admin/customers").param("search", suffix).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].spending[0].currency").value(currency))
                .andExpect(jsonPath("$[0].spending[0].amount").value(24));
        mvc.perform(get("/api/admin/dashboard").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlySales.length()").value(6))
                .andExpect(jsonPath("$.weeklyOrders.length()").value(7));
        mvc.perform(get("/api/admin/reports").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.currencyCode").value(currency))
                .andExpect(jsonPath("$.categorySales").isArray());
    }

    @Test
    void couponSchedulingAndReviewModerationUseIndependentStatuses() throws Exception {
        mvc.perform(post("/api/admin/coupons").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","discountType":"PERCENTAGE","discountValue":20,"minimumOrderAmount":50,
                                 "startsAt":"%s","active":true}
                                """.formatted(suffix, java.time.OffsetDateTime.now().plusDays(2))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SCHEDULED"));
        mvc.perform(get("/api/admin/coupons").param("search", suffix).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("SCHEDULED"));

        long customer = jdbc.queryForObject("""
                INSERT INTO users(first_name, last_name, email, password_hash)
                VALUES ('QA', 'Reviewer', ?, 'unused') RETURNING id
                """, Long.class, "review-" + suffix + "@example.test");
        long product = jdbc.queryForObject("""
                INSERT INTO products(name, sku, slug, price) VALUES ('QA Product', ?, ?, 10) RETURNING id
                """, Long.class, suffix, suffix);
        long review = jdbc.queryForObject("""
                INSERT INTO reviews(product_id, user_id, rating, comment) VALUES (?, ?, 5, ?) RETURNING id
                """, Long.class, product, customer, suffix);
        mvc.perform(put("/api/admin/reviews/{id}/moderation", review).param("status", "HIDDEN").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hidden").value(true))
                .andExpect(jsonPath("$.approved").value(false));
        mvc.perform(get("/api/admin/reviews").param("search", suffix).param("status", "HIDDEN").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(review));
    }

    @Test
    void storeSettingsAndUploadedLogoRoundTrip() throws Exception {
        mvc.perform(put("/api/admin/settings").session(session).contentType(MediaType.APPLICATION_JSON).content("""
                {"storeName":"QA Store","contactEmail":"qa@example.test","currencyCode":"USD","taxRate":8.25,
                 "timezone":"America/Los_Angeles","logoData":"%s",
                 "preferences":{"flatRate":5,"freeShippingMinimum":50,"cashOnDelivery":true,
                                "orderAlerts":false,"lowStockAlerts":true}}
                """.formatted(PNG)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.timezone").value("America/Los_Angeles"))
                .andExpect(jsonPath("$.preferences.flatRate").value(5))
                .andExpect(jsonPath("$.preferences.orderAlerts").value(false));
        mvc.perform(get("/api/media/store-logo")).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
    }
}
