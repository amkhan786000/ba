package com.rahbar.service;

import com.rahbar.entity.GranteeDetails;
import com.rahbar.entity.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Does a student fit a sponsor's wish? Unset preferences are no condition; unknown facts don't fit. */
class SponsorMatchTest {

    private static User student(String collegeType) {
        User u = new User();
        u.setCollegeType(collegeType);
        return u;
    }

    private static GranteeDetails family(String profession, String income) {
        GranteeDetails g = new GranteeDetails();
        g.setFatherProfession(profession);
        g.setAverageAnnualSalary(income == null ? null : new BigDecimal(income));
        return g;
    }

    @Test
    void everyGivenPreferenceMustFit() {
        var w = new SponsorMatch.Wish("GOVT", List.of("Farmer", "Driver"), new BigDecimal("50000"), new BigDecimal("200000"));
        assertTrue(w.any());
        var fit = SponsorMatch.fit(w, student("GOVT"), family("farmer ", "120000"));
        assertTrue(fit.fits());
        assertEquals(3, fit.why().size());
        assertFalse(SponsorMatch.fit(w, student("PRIVATE"), family("Farmer", "120000")).fits());
        assertFalse(SponsorMatch.fit(w, student("GOVT"), family("Teacher", "120000")).fits());
        assertFalse(SponsorMatch.fit(w, student("GOVT"), family("Farmer", "300000")).fits());
        assertEquals(List.of("family income not known"), SponsorMatch.fit(w, student("GOVT"), family("Farmer", null)).missing());
    }

    @Test
    void aStudentWhoseCollegeTypeDoesntMatterFitsEither() {
        var w = new SponsorMatch.Wish("PRIVATE", List.of(), null, null);
        assertTrue(SponsorMatch.fit(w, student("ANY"), null).fits());
        assertTrue(SponsorMatch.fit(w, student(null), null).fits());
    }

    @Test
    void noPreferencesMeansNoBadge() {
        assertFalse(new SponsorMatch.Wish("ANY", List.of(), null, null).any());
        assertFalse(new SponsorMatch.Wish(null, null, null, null).any());
    }
}
