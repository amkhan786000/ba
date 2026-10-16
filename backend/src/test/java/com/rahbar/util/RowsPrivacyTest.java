package com.rahbar.util;

import com.rahbar.entity.User;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Generic rows never carry the password hash, a person's PAN or sponsorship amounts. */
class RowsPrivacyTest {

    @Test
    void passwordHashAndPanAreLeftOut() {
        User u = new User();
        u.setId(5L);
        u.setName("Zara");
        u.setPasswordHash("$2a$10$secret");
        u.setPan("ABCDE1234F");
        Map<String, Object> row = Rows.of(u);
        assertEquals("Zara", row.get("name"));
        assertFalse(row.containsKey("password_hash"));
        assertFalse(row.containsKey("pan"));
        assertFalse(row.containsValue("ABCDE1234F"));
    }

    @Test
    void sponsorshipAmountsAreKeptButNeverSent() throws Exception {
        com.rahbar.entity.Payment p = new com.rahbar.entity.Payment();
        p.setPaymentId(9L);
        p.setAmount(new java.math.BigDecimal("6000"));
        com.rahbar.entity.PaymentInstallment i = new com.rahbar.entity.PaymentInstallment();
        i.setInstallmentNo(2);
        i.setAmount(new java.math.BigDecimal("6000"));
        assertEquals(9L, Rows.of(p).get("payment_id"));
        assertFalse(Rows.of(p).containsKey("amount"));
        assertEquals(2, Rows.of(i).get("installment_no"));
        assertFalse(Rows.of(i).containsKey("amount"));
        // Nor when an entity is turned into JSON directly.
        var json = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        assertFalse(json.writeValueAsString(p).contains("6000"));
        assertFalse(json.writeValueAsString(i).contains("6000"));
    }
}
