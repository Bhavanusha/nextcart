package com.nextcart.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
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

@SpringBootTest(properties = {
        "PAYHERE_MERCHANT_ID=qa-merchant", "PAYHERE_MERCHANT_SECRET=qa-secret", "PAYHERE_SANDBOX=true",
        "PAYHERE_RETURN_BASE_URL=https://store.example.test", "PAYHERE_NOTIFY_URL=https://api.example.test/api/store/payhere/notify"
})
@Transactional
class StorefrontDatabaseTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper json;
    private MockMvc mvc;
    private MockHttpSession session;
    private long product;
    private long variant;
    private String slug;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        session = customer();
        slug = "product-" + UUID.randomUUID();
        long category = jdbc.queryForObject("INSERT INTO categories(name, slug) VALUES ('Test Ceramics', ?) RETURNING id",
                Long.class, slug);
        product = jdbc.queryForObject("""
                INSERT INTO products(name, slug, category_id, sku, currency_code, price, stock_quantity, catalog_status, is_featured)
                VALUES ('Test Cup', ?, ?, ?, 'USD', 20, 5, 'ACTIVE', TRUE) RETURNING id
                """, Long.class, slug, category, slug);
        variant = jdbc.queryForObject("""
                INSERT INTO product_variants(product_id,color,size,price) VALUES (?, 'Ivory', 'Standard', 22) RETURNING id
                """, Long.class, product);
        jdbc.update("""
                UPDATE store_settings SET currency_code = 'USD', tax_rate = 0, shipping_config = '{"flatRate":5}',
                    payment_config = '{"cashOnDelivery":true}' WHERE id = 1
                """);
    }

    @Test
    void publishedCatalogWishlistAndPayHereCheckoutRoundTrip() throws Exception {
        mvc.perform(get("/api/store/settings")).andExpect(status().isOk()).andExpect(jsonPath("$.content.heroTitle").exists());
        mvc.perform(get("/api/store/categories")).andExpect(status().isOk());
        mvc.perform(get("/api/store/products").param("category",slug)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/store/products/{slug}",slug)).andExpect(status().isOk()).andExpect(jsonPath("$.variants[0].id").value(variant));
        mvc.perform(put("/api/store/wishlist/{id}",product).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saved\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(product));
        mvc.perform(get("/api/store/account").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.wishlistCount").value(1));
        add();
        String key = UUID.randomUUID().toString();
        String number = checkout(key,"PAYHERE");
        assertEquals(number, checkout(key,"PAYHERE"));
        assertEquals(4, jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id = ?", Integer.class, product));
        mvc.perform(get("/api/store/orders/{number}", number).session(customer())).andExpect(status().isNotFound());
        mvc.perform(get("/api/store/orders/{number}", number).session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.order.paymentStatus").value("PENDING")).andExpect(jsonPath("$.items[0].variant").value("Ivory / Standard"));
        mvc.perform(notification(number,"2","00000000000000000000000000000000")).andExpect(status().isBadRequest());
        mvc.perform(notification(number,"2",signature(number,"2"))).andExpect(status().isNoContent());
        mvc.perform(notification(number,"2",signature(number,"2"))).andExpect(status().isNoContent());
        mvc.perform(get("/api/store/orders/{number}", number).session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.order.paymentStatus").value("PAID")).andExpect(jsonPath("$.steps[0].status").value("CONFIRMED"));
        mvc.perform(get("/api/store/orders").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.orders[0].number").value(number));
        mvc.perform(get("/api/store/addresses").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$[0].primary").value(true));
    }

    @Test
    void declinedPaymentRestoresStockExactlyOnce() throws Exception {
        add();
        String number = checkout(UUID.randomUUID().toString(),"PAYHERE");
        mvc.perform(notification(number,"-2",signature(number,"-2"))).andExpect(status().isNoContent());
        mvc.perform(notification(number,"-2",signature(number,"-2"))).andExpect(status().isNoContent());
        assertEquals(5, jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id = ?", Integer.class, product));
        mvc.perform(get("/api/store/orders/{number}", number).session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("CANCELLED"));
    }

    @Test
    void publishedProductsAndCustomerEndpointsRespectVisibility() throws Exception {
        jdbc.update("UPDATE products SET is_active = FALSE, catalog_status = 'DRAFT' WHERE id = ?", product);
        mvc.perform(get("/api/store/products/{slug}",slug)).andExpect(status().isNotFound());
        mvc.perform(get("/api/store/cart")).andExpect(status().isUnauthorized());
        var created = mvc.perform(post("/api/store/addresses").session(session).contentType(MediaType.APPLICATION_JSON).content(address()))
                .andExpect(status().isOk()).andReturn();
        long id = json.readTree(created.getResponse().getContentAsString()).get(0).get("id").asLong();
        mvc.perform(put("/api/store/addresses/{id}",id).session(customer()).contentType(MediaType.APPLICATION_JSON).content(address()))
                .andExpect(status().isNotFound());
    }

    private void add() throws Exception {
        mvc.perform(post("/api/store/cart/items").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":"+product+",\"variantId\":"+variant+",\"quantity\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(27));
    }

    private String checkout(String key, String method) throws Exception {
        var result = mvc.perform(post("/api/store/checkout").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"key":"%s","address":%s,"method":"%s","saveAddress":true,"currency":"USD","total":27}
                                """.formatted(key,address(),method)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.payment.action").value("https://sandbox.payhere.lk/pay/checkout"))
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("number").asText();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder notification(String number, String status, String signature) {
        return post("/api/store/payhere/notify").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("merchant_id","qa-merchant").param("order_id",number).param("payhere_amount","27.00")
                .param("payhere_currency","USD").param("status_code",status).param("md5sig",signature).param("payment_id","payment-reference");
    }

    private String signature(String number, String status) throws Exception {
        return md5("qa-merchant" + number + "27.00USD" + status + md5("qa-secret"));
    }

    private String md5(String value) throws Exception {
        return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private String address() {
        return """
                {"label":"Home","name":"Test Customer","phone":"+1 503 555 0142","line1":"Test Street",
                 "city":"Portland","state":"Oregon","postal":"97201","country":"United States"}
                """;
    }

    private MockHttpSession customer() {
        long user = jdbc.queryForObject("""
                INSERT INTO users(first_name,last_name,email,password_hash) VALUES ('Test','Customer',?,'unused') RETURNING id
                """, Long.class, UUID.randomUUID()+"@example.test");
        var result = new MockHttpSession();
        result.setAttribute("userId",user);
        return result;
    }
}
