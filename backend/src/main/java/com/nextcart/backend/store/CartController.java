package com.nextcart.backend.store;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/store/cart")
@CrossOrigin(origins = StoreAccess.ORIGIN, allowCredentials = "true")
public class CartController {
    private final JdbcTemplate jdbc;
    private final CartService carts;

    public CartController(JdbcTemplate jdbc, CartService carts) { this.jdbc = jdbc; this.carts = carts; }

    @GetMapping
    public CartService.Quote get(HttpSession session) {
        return carts.quote(StoreAccess.customer(jdbc, session), false, false);
    }

    @PostMapping("/items")
    @Transactional
    public CartService.Quote add(@Valid @RequestBody Add input, HttpSession session) {
        long user = StoreAccess.customer(jdbc, session);
        StoreAccess.lockCustomer(jdbc, user);
        long variant = input.variantId() == null ? 0 : input.variantId();
        if (!carts.activeVariant(input.productId(), variant)) {
            throw StoreAccess.error(HttpStatus.BAD_REQUEST, "Choose a valid product option.");
        }
        carts.ensure(user);
        jdbc.update("""
                INSERT INTO cart_items(cart_id, product_id, variant_id, quantity)
                SELECT id, ?, ?, ? FROM carts WHERE user_id = ?
                ON CONFLICT (cart_id, product_id, variant_id) DO UPDATE
                SET quantity = cart_items.quantity + EXCLUDED.quantity, updated_at = CURRENT_TIMESTAMP
                """, input.productId(), variant, input.quantity(), user);
        return checked(user, input.productId());
    }

    @PutMapping("/items/{id}")
    @Transactional
    public CartService.Quote change(@PathVariable long id, @Valid @RequestBody Quantity input, HttpSession session) {
        long user = StoreAccess.customer(jdbc, session);
        StoreAccess.lockCustomer(jdbc, user);
        Long product = jdbc.query("""
                UPDATE cart_items ci SET quantity = ?, updated_at = CURRENT_TIMESTAMP
                FROM carts c WHERE ci.cart_id = c.id AND c.user_id = ? AND ci.id = ? RETURNING ci.product_id
                """, rs -> rs.next() ? rs.getLong(1) : null, input.quantity(), user, id);
        if (product == null) throw StoreAccess.error(HttpStatus.NOT_FOUND, "Item not found.");
        return checked(user, product);
    }

    @DeleteMapping("/items/{id}")
    @Transactional
    public CartService.Quote remove(@PathVariable long id, HttpSession session) {
        long user = StoreAccess.customer(jdbc, session);
        StoreAccess.lockCustomer(jdbc, user);
        jdbc.update("DELETE FROM cart_items ci USING carts c WHERE ci.cart_id = c.id AND c.user_id = ? AND ci.id = ?", user, id);
        return carts.quote(user, false, false);
    }

    @PutMapping("/coupon")
    @Transactional
    public CartService.Quote coupon(@Valid @RequestBody Coupon input, HttpSession session) {
        long user = StoreAccess.customer(jdbc, session);
        StoreAccess.lockCustomer(jdbc, user);
        carts.ensure(user);
        String code = input.code().trim().toUpperCase(java.util.Locale.ROOT);
        jdbc.update("UPDATE carts SET coupon_code = ? WHERE user_id = ?", code.isEmpty() ? null : code, user);
        var quote = carts.quote(user, false, false);
        if (quote.invalidCoupon()) throw StoreAccess.error(HttpStatus.BAD_REQUEST, "This code is unavailable for the current bag.");
        return quote;
    }

    private CartService.Quote checked(long user, long product) {
        var quote = carts.quote(user, false, false);
        var lines = quote.lines().stream().filter(line -> line.productId() == product).toList();
        long quantity = lines.stream().mapToLong(CartService.Line::quantity).sum();
        if (lines.stream().anyMatch(line -> quantity > line.available() || !line.purchasable()
                || !line.currency().equals(quote.currency()))) {
            throw StoreAccess.error(HttpStatus.CONFLICT, "This item or quantity is unavailable.");
        }
        return quote;
    }

    record Add(@NotNull @Min(1) Long productId, @Min(1) Long variantId, @NotNull @Min(1) @Max(999) Integer quantity) {}
    record Quantity(@NotNull @Min(1) @Max(999) Integer quantity) {}
    record Coupon(@NotNull @Size(max = 50) String code) {}
}
