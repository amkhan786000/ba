package com.rahbar.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** Razorpay signatures and amounts: a payment only counts when the signature made with our secret matches. */
class RazorpayGatewayTest {

    private final RazorpayGateway gateway = new RazorpayGateway("rzp_test_abc", "secret123", "hook456");

    @Test
    void aPaymentSignatureIsHmacOfOrderAndPaymentWithTheKeySecret() {
        String good = RazorpayGateway.hmac("order_1|pay_1", "secret123");
        assertTrue(gateway.paymentSignatureValid("order_1", "pay_1", good));
        assertTrue(gateway.paymentSignatureValid("order_1", "pay_1", good.toUpperCase()));
        assertFalse(gateway.paymentSignatureValid("order_1", "pay_2", good)); // another payment
        assertFalse(gateway.paymentSignatureValid("order_1", "pay_1", RazorpayGateway.hmac("order_1|pay_1", "someone-elses-secret")));
        assertFalse(gateway.paymentSignatureValid("order_1", "pay_1", null));
    }

    @Test
    void aWebhookSignatureIsHmacOfTheRawBodyWithTheWebhookSecret() {
        String body = "{\"event\":\"payment.captured\"}";
        assertTrue(gateway.webhookSignatureValid(body, RazorpayGateway.hmac(body, "hook456")));
        assertFalse(gateway.webhookSignatureValid(body + " ", RazorpayGateway.hmac(body, "hook456")));
        assertFalse(new RazorpayGateway("rzp_test_abc", "secret123", "").webhookSignatureValid(body, "anything"));
    }

    @Test
    void amountsGoInPaiseAndTestKeysAreKnown() {
        assertEquals(1400000L, RazorpayGateway.paise(new BigDecimal("14000")));
        assertEquals(150L, RazorpayGateway.paise(new BigDecimal("1.50")));
        assertTrue(gateway.configured());
        assertTrue(gateway.testMode());
        assertFalse(new RazorpayGateway("", "", "").configured());
    }
}
