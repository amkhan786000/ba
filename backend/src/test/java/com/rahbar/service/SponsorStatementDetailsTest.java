package com.rahbar.service;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.rahbar.entity.GranteeDetails;
import com.rahbar.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The sponsor's statement also has a few details about each student: contact, father and profession, address, status. */
class SponsorStatementDetailsTest {

    private static String text(byte[] pdf) throws Exception {
        PdfReader r = new PdfReader(pdf);
        StringBuilder out = new StringBuilder();
        for (int p = 1; p <= r.getNumberOfPages(); p++) out.append(new PdfTextExtractor(r).getTextFromPage(p)).append('\n');
        return out.toString();
    }

    @Test
    void studentDetailsAreOnTheStatement() throws Exception {
        User sponsor = new User();
        sponsor.setName("Asha Khan");
        sponsor.setUserId("1042");
        User student = new User();
        student.setId(7L);
        student.setName("Ayesha Parveen");
        student.setUserId("PVT001-2026");
        student.setPhone("9000000003");
        student.setEmail("ayesha@example.com");
        student.setStudyStatus("STUDYING");
        GranteeDetails family = new GranteeDetails();
        family.setFatherName("Abdul Karim");
        family.setFatherProfession("Farmer");
        family.setAddress("Village Arwal, Bihar");
        User other = new User(); // no application details: dashes
        other.setId(8L);
        other.setName("Bilal Ahmad");
        other.setUserId("PVT002-2026");

        byte[] pdf = SponsorStatementService.render(sponsor, 2026, LocalDate.of(2026, 10, 1), List.of(
                new SponsorStatementService.Line(student, "D.Pharm - Patna College", 4, 2, 1, null, family),
                new SponsorStatementService.Line(other, "", 0, 0, 0, null, null)), List.of(), Map.of(), Map.of());
        String text = text(pdf);
        for (String expected : List.of("Student details", "9000000003", "ayesha@example.com", "Abdul Karim", "(Farmer)", "Village Arwal, Bihar", "Studying", "Bilal Ahmad")) {
            assertTrue(text.contains(expected), "missing: " + expected);
        }
    }
}
