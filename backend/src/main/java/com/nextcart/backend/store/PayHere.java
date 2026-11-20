package com.nextcart.backend.store;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class PayHere {
    private static final Set<String> CURRENCIES = Set.of("LKR", "USD");
    private final String merchant;
    private final String secret;
    private final String frontend;
    private final String notify;
    private final boolean sandbox;

    PayHere(@Value("${PAYHERE_MERCHANT_ID:}") String merchant, @Value("${PAYHERE_MERCHANT_SECRET:}") String secret,
            @Value("${PAYHERE_RETURN_BASE_URL:}") String frontend, @Value("${PAYHERE_NOTIFY_URL:}") String notify,
            @Value("${PAYHERE_SANDBOX:true}") boolean sandbox) {
        this.merchant = merchant.trim();
        this.secret = secret.trim();
        this.frontend = frontend.replaceAll("/+$", "");
        this.notify = notify;
        this.sandbox = sandbox;
    }

    boolean enabled(String currency) {
        return credentialsReady() && CURRENCIES.contains(currency);
    }

    private boolean credentialsReady() {
        return !merchant.isBlank() && !secret.isBlank() && validUrl(frontend) && validUrl(notify);
    }

    private boolean validUrl(String value) {
        try {
            URI uri = URI.create(value);
            return uri.getHost() != null && uri.getUserInfo() == null && uri.getFragment() == null
                    && ("https".equals(uri.getScheme()) || (sandbox && "http".equals(uri.getScheme())));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    Checkout create(String order, BigDecimal total, String currency, AccountController.Profile profile,
            AccountController.AddressInput address) {
        String amount = total.setScale(2).toPlainString();
        String confirmation = frontend + "/order-success/" + order;
        return new Checkout(sandbox ? "https://sandbox.payhere.lk/pay/checkout" : "https://www.payhere.lk/pay/checkout",
                Map.ofEntries(Map.entry("merchant_id", merchant), Map.entry("order_id", order),
                    Map.entry("amount", amount), Map.entry("currency", currency), Map.entry("items", order),
                    Map.entry("hash", md5(merchant + order + amount + currency + md5(secret))),
                    Map.entry("return_url", confirmation), Map.entry("cancel_url", confirmation),
                    Map.entry("notify_url", notify), Map.entry("first_name", profile.firstName()),
                    Map.entry("last_name", profile.lastName()), Map.entry("email", profile.email()), Map.entry("phone", address.phone()),
                    Map.entry("address", address.line1() + (address.line2() == null || address.line2().isBlank() ? "" : ", " + address.line2())),
                    Map.entry("city", address.city()), Map.entry("country", address.country())));
    }

    boolean validNotification(Map<String, String> fields) {
        if (!credentialsReady() || !merchant.equals(fields.get("merchant_id"))) return false;
        String order = fields.getOrDefault("order_id", "");
        String amount = fields.getOrDefault("payhere_amount", "");
        String currency = fields.getOrDefault("payhere_currency", "");
        String status = fields.getOrDefault("status_code", "");
        String signature = fields.getOrDefault("md5sig", "");
        if (!order.matches("[A-Z0-9-]{1,50}") || !amount.matches("[0-9]{1,10}(\\.[0-9]{1,2})?")
                || !CURRENCIES.contains(currency) || !Set.of("2", "0", "-1", "-2", "-3").contains(status)
                || !signature.matches("[0-9A-Fa-f]{32}")) return false;
        byte[] expected = md5(merchant + order + amount + currency + status + md5(secret)).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, signature.toUpperCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }

    private String md5(String value) {
        try {
            return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("MD5")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    record Checkout(String action, Map<String, String> fields) {}
}
