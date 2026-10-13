package com.rahbar.controller;

import com.rahbar.entity.Course;
import com.rahbar.entity.RccCenter;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.CourseRepository;
import com.rahbar.repository.RccCenterRepository;
import com.rahbar.service.PublicService;
import com.rahbar.util.WebsiteLimiter;
import com.rahbar.util.WebsiteSupport;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * The scholarship application form on the website (Rahbar Diploma & Paramedical Sponsorship > Apply). It is open
 * only while an Application Period is active in Rahbar (Admin > Application Periods); otherwise the website shows
 * it as closed and a submission is refused. Applications land in Admin > Applications as before.
 */
@RestController
@RequestMapping("/api/public/website/scholarship")
public class WebsiteScholarshipController {

    private final PublicService publicService;
    private final RccCenterRepository centers;
    private final CourseRepository courses;
    private final WebsiteLimiter applyLimit = new WebsiteLimiter(3, 300);

    /** Rahbar's own address (the first of PUBLIC_URL): the website links to its "Track an application" page. */
    @Value("${app.cors.allowed-origins:}")
    private String rahbarUrls;

    public WebsiteScholarshipController(PublicService publicService, RccCenterRepository centers, CourseRepository courses) {
        this.publicService = publicService;
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
        out.put("trackUrl", trackUrl());
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
        out.put("trackUrl", trackUrl());
        return out;
    }

    private String trackUrl() {
        String first = rahbarUrls == null ? "" : rahbarUrls.split(",")[0].trim();
        return first.isEmpty() ? null : first.replaceAll("/+$", "") + "/track-application";
    }

    private static List<String> names(List<String> values) {
        Set<String> out = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String v : values) if (v != null && !v.isBlank()) out.add(v.trim());
        return new ArrayList<>(out);
    }
}
