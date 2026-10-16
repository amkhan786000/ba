package com.rahbar.service;

import com.rahbar.entity.PaymentSchedule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Which Payment Config a student gets from their session year and college type (BA Details). */
class PaymentConfigChoiceTest {

    private static PaymentSchedule config(String type, String amount) {
        PaymentSchedule c = new PaymentSchedule();
        c.setYear(2026);
        c.setCollegeType(type);
        c.setAmount(new BigDecimal(amount));
        return c;
    }

    private static PaymentSchedule pick(List<PaymentSchedule> configs, String studentType) {
        return PaymentInstallmentService.choose(configs, 2026, studentType).config();
    }

    @Test
    void govtAndPrivateStudentsGetTheirOwnConfigElseTheAnyOne() {
        PaymentSchedule govt = config("GOVT", "10000"), priv = config("PRIVATE", "20000"), any = config("ANY", "15000");
        assertSame(govt, pick(List.of(govt, priv, any), "GOVT"));
        assertSame(priv, pick(List.of(govt, priv, any), "private"));
        assertSame(any, pick(List.of(govt, any), "PRIVATE")); // no Private config: the year's Any config
        assertSame(any, pick(List.of(any), "GOVT"));
        assertNull(pick(List.of(govt), "PRIVATE")); // only a Govt config: a Private student gets none
        assertTrue(PaymentInstallmentService.choose(List.of(govt), 2026, "PRIVATE").problem().contains("private colleges"));
    }

    @Test
    void doesntMatterOrNotSetUsesTheAnyConfigOrTheYearsOnlyOne() {
        PaymentSchedule govt = config("GOVT", "10000"), priv = config("PRIVATE", "20000"), any = config("ANY", "15000");
        assertSame(any, pick(List.of(govt, priv, any), "ANY"));
        assertSame(any, pick(List.of(govt, priv, any), null));
        assertSame(govt, pick(List.of(govt), null)); // the year has only one config
        // separate Govt and Private configs and no Any one: the college type must be chosen
        PaymentInstallmentService.ConfigChoice c = PaymentInstallmentService.choose(List.of(govt, priv), 2026, null);
        assertNull(c.config());
        assertTrue(c.problem().contains("Set the student's college type"));
        assertEquals("There is no Payment Config for 2026.", PaymentInstallmentService.choose(List.of(), 2026, "GOVT").problem());
    }
}
