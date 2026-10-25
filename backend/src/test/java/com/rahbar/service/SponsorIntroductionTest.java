package com.rahbar.service;

import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Edit Sponsor > Introduction: how the sponsor was introduced and when it was communicated (all optional). */
class SponsorIntroductionTest {

    private static Map<String, Object> data(Object via, Object other, Object on) {
        Map<String, Object> d = new HashMap<>();
        d.put("introducedVia", via);
        d.put("introducedViaOther", other);
        d.put("introducedOn", on);
        return d;
    }

    @Test
    void theMediumAndTheDateAreKept() {
        User u = new User();
        AdminService.setIntroduction(u, data("whatsapp", "ignored unless other", "2026-10-01"));
        assertEquals("WHATSAPP", u.getIntroducedVia());
        assertNull(u.getIntroducedViaOther());
        assertEquals(LocalDate.of(2026, 10, 1), u.getIntroducedOn());

        AdminService.setIntroduction(u, data("OTHER", "  At the annual dinner ", null));
        assertEquals("OTHER", u.getIntroducedVia());
        assertEquals("At the annual dinner", u.getIntroducedViaOther());
        assertNull(u.getIntroducedOn());

        AdminService.setIntroduction(u, data("", null, ""));
        assertNull(u.getIntroducedVia());
        assertNull(u.getIntroducedViaOther());
    }

    @Test
    void onlyKnownMediumsAndNoFutureDates() {
        User u = new User();
        assertThrows(ApiException.class, () -> AdminService.setIntroduction(u, data("FAX", null, null)));
        assertThrows(ApiException.class, () -> AdminService.setIntroduction(u, data("EMAIL", null, LocalDate.now().plusDays(1).toString())));
        assertThrows(ApiException.class, () -> AdminService.setIntroduction(u, data("EMAIL", null, "yesterday")));
        assertThrows(ApiException.class, () -> AdminService.setIntroduction(u, data("OTHER", "x".repeat(101), null)));
    }
}
