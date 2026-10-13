package com.rahbar.controller;

import com.rahbar.service.ApplicationService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/** Staff view of one application: details + history + documents, interview scheduling, document upload. */
@RestController
@RequestMapping("/api/admin/applications/{id}")
@PreAuthorize("denyAll()") // every endpoint below names the permission it needs
public class ApplicationController {

    private final ApplicationService applicationService;
    private final com.rahbar.service.InterviewService interviewService;
    private final com.rahbar.service.ApplicationAcceptService acceptService;

    public ApplicationController(ApplicationService applicationService, com.rahbar.service.InterviewService interviewService,
                                 com.rahbar.service.ApplicationAcceptService acceptService) {
        this.applicationService = applicationService;
        this.interviewService = interviewService;
        this.acceptService = acceptService;
    }

    /**
     * Accepts the application: makes the student account (temporary password emailed) and copies the family details.
     * Body: { note } (optional staff note). Sponsor, college / course and payments are completed on the student's profile.
     */
    @PostMapping("/accept")
    @PreAuthorize("hasAuthority('APPLICATIONS:EDIT') or hasRole('3')")
    public Map<String, Object> accept(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return acceptService.accept(id, body == null ? null : body.get("note"));
    }

    /** Every interviewer's scores for this application, the averages and the signed-in user's own review. */
    @GetMapping("/interview-scores")
    @PreAuthorize("hasAuthority('APPLICATIONS:VIEW') or hasAnyRole('3','4')")
    public Map<String, Object> interviewScores(@PathVariable Long id) {
        return interviewService.forApplication(id);
    }

    /** Body: { scores: { "<criterionId>": 1-10, ... }, recommendation: APPROVE | WAITLIST | REJECT, comment }. */
    @PutMapping("/interview-scores")
    @PreAuthorize("hasAuthority('APPLICATIONS:EDIT') or hasAnyRole('3','4')")
    @SuppressWarnings("unchecked")
    public Map<String, Object> saveInterviewScores(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object scores = body.get("scores");
        return interviewService.saveMyReview(id, scores instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of(),
                (String) body.get("recommendation"), (String) body.get("comment"));
    }

    @DeleteMapping("/interview-scores")
    @PreAuthorize("hasAuthority('APPLICATIONS:EDIT') or hasAnyRole('3','4')")
    public Map<String, String> deleteInterviewScores(@PathVariable Long id) {
        interviewService.deleteMyReview(id);
        return Map.of("message", "Your scores for this application were removed.");
    }

    @GetMapping("/details")
    @PreAuthorize("hasAuthority('APPLICATIONS:VIEW') or hasAnyRole('3','4')")
    public Map<String, Object> details(@PathVariable Long id) {
        return applicationService.details(id);
    }

    /** Body: { interviewAt: "2026-10-12T10:30", venue } */
    @PostMapping("/interview")
    @PreAuthorize("hasAuthority('APPLICATIONS:EDIT') or hasAnyRole('3','4')")
    public Map<String, String> scheduleInterview(@PathVariable Long id, @RequestBody Map<String, String> body) {
        applicationService.scheduleInterview(id, body.get("interviewAt"), body.get("venue"));
        return Map.of("message", "Interview scheduled.");
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('APPLICATIONS:EDIT') or hasAnyRole('3','4')")
    public Map<String, Object> uploadDocument(@PathVariable Long id, @RequestParam(required = false) String docType,
                                              @RequestParam MultipartFile file) {
        return applicationService.uploadByStaff(id, docType, file);
    }
}
