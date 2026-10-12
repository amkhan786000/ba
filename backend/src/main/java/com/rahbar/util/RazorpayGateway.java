package com.rahbar.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rahbar.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Razorpay (https://razorpay.com/docs/api): creating an order before the payment window opens, and checking the
 * signatures Razorpay puts on a finished payment and on its webhooks, so Rahbar never takes a browser's word that a
 * payment succeeded. Keys come from the server settings (RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET,
 * RAZORPAY_WEBHOOK_SECRET); test keys (rzp_test_...) take test payments only.
 */
@Component
public class RazorpayGateway {

    private static final String ORDERS_URL = "https://api.razorpay.com/v1/orders";

    private final String keyId;
    private final String keySecret;
    private final String webhookSecret;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public RazorpayGateway(@Value("${app.razorpay.key-id:}") String keyId,
                           @Value("${app.razorpay.key-secret:}") String keySecret,
                           @Value("${app.razorpay.webhook-secret:}") String webhookSecret) {
        this.keyId = keyId == null ? "" : keyId.trim();
        this.keySecret = keySecret == null ? "" : keySecret.trim();
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
    }

    /** True when the key id and secret are set. */
    public boolean configured() {
        return !keyId.isEmpty() && !keySecret.isEmpty();
    }

    /** True for test keys (no real money moves). */
    public boolean testMode() {
        return keyId.startsWith("rzp_test_");
    }

    public boolean webhookConfigured() {
        return !webhookSecret.isEmpty();
    }

    /** The public key id for the payment window (safe to send to the browser; the secret never is). */
    public String keyId() {
        return keyId;
    }

    /** Creates an order for the amount (rupees); returns Razorpay's order id. receipt: our reference (max 40 chars). */
    public String createOrder(BigDecimal rupees, String receipt, Map<String, String> notes) {
        if (!configured()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Online payments are not set up yet.");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", paise(rupees));
        body.put("currency", "INR");
        body.put("receipt", receipt.length() > 40 ? receipt.substring(0, 40) : receipt);
        body.put("notes", notes);
        try {
            String auth = Base64.getEncoder().encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
            HttpRequest req = HttpRequest.newBuilder(URI.create(ORDERS_URL)).timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Basic " + auth).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "The payment service refused the order (" + res.statusCode() + "). Please try again later.");
            }
            JsonNode node = json.readTree(res.body());
            return node.path("id").asText();
        } catch (ApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not reach the payment service. Please try again.");
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not reach the payment service. Please try again.");
        }
    }

    /** The signature Razorpay returns to the payment window: HMAC-SHA256 of "order_id|payment_id" with the key secret. */
    public boolean paymentSignatureValid(String orderId, String paymentId, String signature) {
        if (!configured() || orderId == null || paymentId == null || signature == null) return false;
        return same(hmac(orderId + "|" + paymentId, keySecret), signature);
    }

    /** The X-Razorpay-Signature of a webhook: HMAC-SHA256 of the raw body with the webhook secret. */
    public boolean webhookSignatureValid(String rawBody, String signature) {
        if (!webhookConfigured() || rawBody == null || signature == null) return false;
        return same(hmac(rawBody, webhookSecret), signature);
    }

    static long paise(BigDecimal rupees) {
        return rupees.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
    }

    static String hmac(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC failed", e);
        }
    }

    private static boolean same(String expected, String given) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), given.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }
}
