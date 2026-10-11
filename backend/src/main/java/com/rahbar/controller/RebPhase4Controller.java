package com.rahbar.controller;

import com.rahbar.dto.RebPdf;
import com.rahbar.entity.RebSponsorship;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.RebSponsorshipRepository;
import com.rahbar.repository.RebStudentRepository;
import com.rahbar.service.*;
import com.rahbar.util.Rows;
import com.rahbar.util.WebsiteLimiter;
import com.rahbar.util.WebsiteSupport;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * Rahbar-e-Banaat phase 4: admission enquiries (also the website's "Apply for admission" form and the public REB
 * numbers), Quran / Hifz progress, hostel leave and gate passes, and the sponsor's view of their REB girls.
 */
@RestController
public class RebPhase4Controller {

    private final RebEnquiryService enquiries;
    private final RebQuranService quran;
    private final RebHostelService hostel;
    private final RebSponsorPortalService sponsorPortal;
    private final RebStudentRepository students;
    private final RebSponsorshipRepository sponsorships;
    private final WebsiteLimiter enquiryLimit = new WebsiteLimiter(3, 100);

    public RebPhase4Controller(RebEnquiryService enquiries, RebQuranService quran, RebHostelService hostel, RebSponsorPortalService sponsorPortal,
                               RebStudentRepository students, RebSponsorshipRepository sponsorships) {
        this.enquiries = enquiries;
        this.quran = quran;
        this.hostel = hostel;
        this.sponsorPortal = sponsorPortal;
        this.students = students;
        this.sponsorships = sponsorships;
    }

    private static ResponseEntity<ByteArrayResource> pdf(RebPdf pdf) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + pdf.filename() + "\"")
                .body(new ByteArrayResource(pdf.bytes()));
    }

    // ---------------------------------------------------------------- public (biharanjuman.com)

    /** The website's "Apply for admission" form. A filled-in "website" field (bot trap) is accepted but not saved. */
    @PostMapping("/api/public/website/reb/enquiries")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> publicEnquiry(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String thanks = "Thank you. Our office will call you about admission soon.";
        Object trap = body.get("website");
        if (trap != null && !String.valueOf(trap).isBlank()) return Map.of("message", thanks);
        if (!enquiryLimit.allow(WebsiteSupport.clientIp(request))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "You have sent several enquiries already. Please try again in an hour.");
        }
        enquiries.submitFromWebsite(body);
        return Map.of("message", thanks);
    }

    /** Totals for the website (never names): girls studying, in the hostel, sponsored now. */
    @GetMapping("/api/public/website/reb/stats")
    public Map<String, Object> stats() {
        var girls = students.findAll().stream().filter(s -> "ACTIVE".equals(s.getStatus())).toList();
        String now = YearMonth.now().toString();
        long sponsored = sponsorships.findByStatus("ACTIVE").stream().filter(s -> RebSponsorshipService.covers(s, now))
                .map(RebSponsorship::getStudentId).distinct().count();
        return Map.of("girls", girls.size(), "residential", girls.stream().filter(s -> s.isResidential()).count(), "sponsored", sponsored);
    }

    // ---------------------------------------------------------------- enquiries

    @GetMapping("/api/admin/reb/enquiries")
    @PreAuthorize("hasAuthority('REB_ENQUIRIES:VIEW')")
    public List<Map<String, Object>> enquiries(@RequestParam(required = false) String status) {
        return enquiries.list(status);
    }

    /** An enquiry taken by phone or at a visit. */
    @PostMapping("/api/admin/reb/enquiries")
    @PreAuthorize("hasAuthority('REB_ENQUIRIES:EDIT')")
    public Map<String, Object> addEnquiry(@RequestBody Map<String, Object> body) {
        return Map.of("message", "Enquiry saved.", "data", Rows.of(enquiries.create(body)));
    }

    @PutMapping("/api/admin/reb/enquiries/{id}")
    @PreAuthorize("hasAuthority('REB_ENQUIRIES:EDIT')")
    public Map<String, Object> updateEnquiry(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Map.of("message", "Enquiry saved.", "data", Rows.of(enquiries.update(id, body)));
    }

    /** Body: { sectionId, rollNo, feePlan, admittedOn }. Needs admitting girls too. */
    @PostMapping("/api/admin/reb/enquiries/{id}/admit")
    @PreAuthorize("hasAuthority('REB_ENQUIRIES:EDIT') and hasAuthority('REB_STUDENTS:EDIT')")
    public Map<String, Object> admit(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Map.of("message", "Admitted.", "id", enquiries.admit(id, body));
    }

    // ---------------------------------------------------------------- Quran progress

    @GetMapping("/api/admin/reb/students/{id}/quran")
    @PreAuthorize("hasAuthority('REB_QURAN:VIEW')")
    public Map<String, Object> quranOfStudent(@PathVariable Long id) {
        return quran.ofStudent(id);
    }

    @GetMapping("/api/admin/reb/quran")
    @PreAuthorize("hasAuthority('REB_QURAN:VIEW')")
    public List<Map<String, Object>> quranSummary(@RequestParam Long sectionId) {
        return quran.sectionSummary(sectionId);
    }

    /** Body: { studentId, date, kind, para, surah, fromAyah, toAyah, quality, completedPara, remark }. */
    @PostMapping("/api/admin/reb/quran")
    @PreAuthorize("hasAuthority('REB_QURAN:EDIT')")
    public Map<String, Object> addLesson(@RequestBody Map<String, Object> body) {
        return Map.of("message", "Lesson saved.", "data", Rows.of(quran.add(body)));
    }

    @DeleteMapping("/api/admin/reb/quran/{id}")
    @PreAuthorize("hasAuthority('REB_QURAN:EDIT')")
    public Map<String, String> deleteLesson(@PathVariable Long id) {
        quran.delete(id);
        return Map.of("message", "Entry removed.");
    }

    // ---------------------------------------------------------------- hostel leave

    @GetMapping("/api/admin/reb/hostel-leaves")
    @PreAuthorize("hasAuthority('REB_HOSTEL:VIEW')")
    public List<Map<String, Object>> leaves(@RequestParam(required = false) String status) {
        return hostel.list(status);
    }

    @GetMapping("/api/admin/reb/students/{id}/hostel-leaves")
    @PreAuthorize("hasAuthority('REB_HOSTEL:VIEW')")
    public List<Map<String, Object>> leavesOfStudent(@PathVariable Long id) {
        return hostel.ofStudent(id);
    }

    /** Body: { studentId, guardianId, leaveTo, reason, note }. */
    @PostMapping("/api/admin/reb/hostel-leaves")
    @PreAuthorize("hasAuthority('REB_HOSTEL:EDIT')")
    public Map<String, Object> giveLeave(@RequestBody Map<String, Object> body) {
        var l = hostel.give(body);
        return Map.of("message", "Gate pass " + l.getPassNo() + " given.", "data", Rows.of(l));
    }

    /** Body: { returnedWith, note }. */
    @PostMapping("/api/admin/reb/hostel-leaves/{id}/return")
    @PreAuthorize("hasAuthority('REB_HOSTEL:EDIT')")
    public Map<String, Object> returned(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Map.of("message", "Marked as back in the hostel.", "data", Rows.of(hostel.markReturned(id, body)));
    }

    // ---------------------------------------------------------------- sponsor portal

    @GetMapping("/api/sponsor/reb-girls")
    @PreAuthorize("hasRole('5')")
    public List<Map<String, Object>> sponsorGirls() {
        return sponsorPortal.mine();
    }

    @GetMapping("/api/sponsor/reb-receipts/{id}")
    @PreAuthorize("hasRole('5')")
    public ResponseEntity<ByteArrayResource> sponsorReceipt(@PathVariable Long id) {
        return pdf(sponsorPortal.receipt(id));
    }
}
