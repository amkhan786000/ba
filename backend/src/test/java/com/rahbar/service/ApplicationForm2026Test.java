package com.rahbar.service;

import com.rahbar.entity.ScholarshipApplication;
import com.rahbar.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The 2026 form's sections: what's required, and how values are cleaned. */
class ApplicationForm2026Test {

    private static Map<String, Object> form() {
        Map<String, Object> f = new HashMap<>();
        f.put("recommendedBy", "chapter");
        f.put("recommendedChapter", "Jeddah Chapter");
        f.put("rccName", "Should be dropped");
        f.put("inchargeMobile", "+91 98765 43210");
        f.put("govtSeat", "y");
        f.put("govtSeatDetails", "Diploma CE, Govt Polytechnic Patna, SBTE");
        f.put("aadhaarLast4", "1234");
        f.put("dateOfBirth", LocalDate.now().minusYears(17).toString());
        f.put("edu10Year", "2025");
        f.put("edu10Percent", "78.456%");
        f.put("bankAccountHolder", "Ayesha Khan");
        f.put("bankAccountNumber", "1234 5678 9012");
        f.put("bankIfsc", "sbin0001234");
        f.put("bankName", "SBI");
        f.put("bankBranch", "Patna Main");
        f.put("termsAccepted", true);
        f.put("selfDeclaration", true);
        return f;
    }

    @Test
    void aCompleteFormIsSavedAndCleaned() {
        ScholarshipApplication a = new ScholarshipApplication();
        a.setRccName("Should be dropped");
        PublicService.applyForm2026(a, form());
        assertEquals("CHAPTER", a.getRecommendedBy());
        assertEquals("Jeddah Chapter", a.getRecommendedChapter());
        assertNull(a.getRccName());
        assertEquals("9876543210", a.getInchargeMobile());
        assertEquals("Y", a.getGovtSeat());
        assertEquals("Indian", a.getNationality());
        assertEquals(new BigDecimal("78.46"), a.getEdu10Percent());
        assertEquals("123456789012", a.getBankAccountNumber());
        assertEquals("SBIN0001234", a.getBankIfsc());
        assertNotNull(a.getDeclarationsAcceptedAt());
    }

    @Test
    void requiredAndInvalidAnswersAreRefused() {
        for (String key : new String[]{"dateOfBirth", "edu10Year", "edu10Percent", "bankAccountHolder", "bankIfsc", "termsAccepted", "selfDeclaration"}) {
            Map<String, Object> f = form();
            f.remove(key);
            assertThrows(ApiException.class, () -> PublicService.applyForm2026(new ScholarshipApplication(), f), key);
        }
        Map<String, Object> f = form();
        f.put("aadhaarLast4", "123456789012"); // the full number is not taken
        assertThrows(ApiException.class, () -> PublicService.applyForm2026(new ScholarshipApplication(), f));
        Map<String, Object> g = form();
        g.put("bankIfsc", "SBI12345");
        assertThrows(ApiException.class, () -> PublicService.applyForm2026(new ScholarshipApplication(), g));
        Map<String, Object> h = form();
        h.put("edu10Percent", "120");
        assertThrows(ApiException.class, () -> PublicService.applyForm2026(new ScholarshipApplication(), h));
    }
}
