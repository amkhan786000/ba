package com.rahbar.controller;

import com.rahbar.entity.Course;
import com.rahbar.entity.RccCenter;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.CourseRepository;
import com.rahbar.repository.RccCenterRepository;
import com.rahbar.service.ApplicationService;
import com.rahbar.service.PublicService;
import com.rahbar.util.WebsiteLimiter;
import com.rahbar.util.WebsiteSupport;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * Scholarship applications on the website (Rahbar Diploma & Paramedical Sponsorship): the form ("Apply", open only
 * while an Application Period is active in Rahbar) and "Track your application" (the application number plus one of
 * the mobile numbers on the form: the steps so far, the interview, and the documents, which the family can upload
 * there). Applications land in Admin > Applications; staff look them up in Applications > Track Application.
 */
@RestController
@RequestMapping("/api/public/website/scholarship")
public class WebsiteScholarshipController {

    private final PublicService publicService;
    private final ApplicationService applicationService;
    private final RccCenterRepository centers;
    private final CourseRepository courses;
    @org.springframework.beans.factory.annotation.Autowired
    private com.rahbar.repository.ChapterRepository chapters;
    private final WebsiteLimiter applyLimit = new WebsiteLimiter(3, 300);
    /** Look-ups need the number and a mobile; the limit stops anyone trying many numbers. */
    private final WebsiteLimiter trackLimit = new WebsiteLimiter(30, 3000);
    private final WebsiteLimiter uploadLimit = new WebsiteLimiter(20, 1000);

    public WebsiteScholarshipController(PublicService publicService, ApplicationService applicationService,
                                        RccCenterRepository centers, CourseRepository courses) {
        this.publicService = publicService;
        this.applicationService = applicationService;
        this.centers = centers;
        this.courses = courses;
    }

    /** Whether applications are open, plus the names to choose from (nothing else about centres or courses). */
    @GetMapping("/options")
    public Map<String, Object> options() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("open", publicService.isApplicationPeriodOpen());
        out.put("rccCenters", names(centers.findAll().stream().map(RccCenter::getCenterName).toList()));
        out.put("courses", names(courses.findAll().stream().map(Course::getCourseName).toList()));
        out.put("chapters", chapters == null ? List.of() : names(chapters.findAll().stream().filter(c -> !Boolean.FALSE.equals(c.getActive()))
                .map(com.rahbar.entity.Chapter::getChapterName).toList()));
        return out;
    }

    /** Same checks as before (period open, name, email, gender, three different mobiles); returns the application number. */
    @PostMapping("/apply")
    public Map<String, Object> apply(@RequestBody Map<String, Object> form, HttpServletRequest request) {
        Object trap = form.get("website");
        if (trap != null && !String.valueOf(trap).isBlank()) return Map.of("message", "Application submitted successfully!");
        if (!applyLimit.allow(WebsiteSupport.clientIp(request))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "You have applied several times already. Please try again in an hour.");
        }
        Map<String, Object> body = new HashMap<>(form);
        body.remove("website");
        Long id = publicService.apply(body);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", "Application submitted successfully!");
        out.put("applicationId", id);
        return out;
    }

    /** "Track your application". Body: { applicationId, mobile } (one of the three mobiles on the form). */
    @PostMapping("/track")
    public Map<String, Object> track(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        if (!trackLimit.allow(WebsiteSupport.clientIp(request))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many look-ups. Please try again in an hour.");
        }
        return applicationService.track(applicationNumber(body.get("applicationId")), body.get("mobile") == null ? null : String.valueOf(body.get("mobile")));
    }

    /** A document for the application (the mobile number proves it is theirs). */
    @PostMapping(value = "/applications/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadDocument(@PathVariable Long id, @RequestParam String mobile,
                                              @RequestParam(required = false) String docType, @RequestParam MultipartFile file,
                                              HttpServletRequest request) {
        if (!uploadLimit.allow(WebsiteSupport.clientIp(request))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many uploads. Please try again in an hour.");
        }
        return applicationService.uploadByApplicant(id, mobile, docType, file);
    }

    private static Long applicationNumber(Object v) {
        try {
            return v == null ? null : Long.valueOf(String.valueOf(v).trim().replaceFirst("^#", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<String> names(List<String> values) {
        Set<String> out = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String v : values) if (v != null && !v.isBlank()) out.add(v.trim());
        return new ArrayList<>(out);
    }
}
