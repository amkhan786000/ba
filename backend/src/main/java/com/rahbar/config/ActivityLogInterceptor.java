package com.rahbar.config;

import com.rahbar.entity.User;
import com.rahbar.security.RahbarUserPrincipal;
import com.rahbar.service.ActivityLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Writes an activity-log entry for every change made through the API (POST / PUT / PATCH / DELETE).
 * Reads (GET) are not logged. Sign-ins are logged by AuthService, which knows the user before a token exists.
 */
@Component
public class ActivityLogInterceptor implements HandlerInterceptor {

    private static final Set<String> CHANGES = Set.of("POST", "PUT", "PATCH", "DELETE");

    /** Paths that are logged elsewhere (sign-in); marking notifications read is skipped as noise. */
    private static final Set<String> SKIP = Set.of("/api/auth/login", "/api/auth/verify-otp",
            "/api/auth/forgot-password", "/api/auth/reset-password", // logged by AuthService
            "/api/admin/broadcasts/preview"); // a recipient count, not a change

    /** Friendly descriptions, first match wins: "METHOD regex" -> label. */
    private static final Map<Pattern, String> LABELS = new LinkedHashMap<>();

    static {
        label("POST /api/auth/register", "Registered a new account");
        // the Bihar Anjuman website: its forms, and Admin > Website
        label("POST /api/public/website/contact", "Sent a message through the website");
        label("POST /api/public/website/directory", "Asked to join the website's directory");
        label("POST /api/public/website/blog/submissions", "Sent a Knowledge Hub post through the website");
        label("POST /api/public/website/volunteer", "Signed up as a volunteer on the website");
        label("PUT /api/admin/website/content", "Edited website page text");
        label("PUT /api/admin/website/translations", "Edited a website translation");
        label("POST /api/admin/website/photo", "Uploaded a website photo");
        for (String[] r : new String[][]{{"projects", "website project"}, {"news", "website news item"},
                {"directory", "directory member"}, {"messages", "website message"}, {"pages", "website page"},
                {"blog", "Knowledge Hub post"}, {"stories", "success story"}, {"gallery", "gallery photo"}}) {
            String path = "/api/admin/website/" + r[0];
            label("POST " + path, r[0].equals("gallery") ? "Uploaded a gallery photo" : "Created a " + r[1]);
            label("PUT " + path + "/[0-9]+", "Updated a " + r[1]);
            label("DELETE " + path + "/[0-9]+", "Deleted a " + r[1]);
        }
        label("POST /api/admin/sponsor-followup/notes", "Recorded a sponsor follow-up call or note");
        label("POST /api/admin/sponsor-followup/emails", "Sent sponsor follow-up emails");
        label("POST /api/admin/mentoring", "Paired a mentor with a student");
        label("PUT /api/admin/mentoring/[0-9]+", "Changed a mentoring pair");
        label("POST /api/admin/finance/categories", "Added an income / expense category");
        label("PUT /api/admin/finance/categories/[0-9]+", "Changed an income / expense category");
        label("DELETE /api/admin/finance/categories/[0-9]+", "Deleted an income / expense category");
        label("POST /api/admin/finance/entries", "Recorded income or an expense");
        label("PUT /api/admin/finance/entries/[0-9]+", "Changed an income / expense entry");
        label("DELETE /api/admin/finance/entries/[0-9]+", "Deleted an income / expense entry");
        label("POST /api/admin/finance/published", "Published a year's income & expenses on the website");
        label("DELETE /api/admin/finance/published/[0-9]+", "Took a year's income & expenses off the website");
        label("POST /api/admin/documents", "Added a document");
        label("PUT /api/admin/documents/[0-9]+", "Edited a document");
        label("POST /api/admin/documents/[0-9]+/archive", "Archived or un-archived a document");
        label("DELETE /api/admin/documents/[0-9]+", "Deleted a document");
        label("POST /api/admin/documents/[0-9]+/revisions/[0-9]+/restore", "Restored an earlier version of a document");
        label("POST /api/admin/documents/categories", "Added a document category");
        label("PUT /api/admin/documents/categories/[0-9]+", "Updated a document category");
        label("DELETE /api/admin/documents/categories/[0-9]+", "Deleted a document category");
        // Rahbar-e-Banaat
        for (String[] r : new String[][]{{"sessions", "REB session"}, {"classes", "REB class"}, {"sections", "REB section"},
                {"subjects", "REB subject"}, {"staff", "REB staff member"}}) {
            String path = "/api/admin/reb/" + r[0];
            label("POST " + path, "Added a " + r[1]);
            label("PUT " + path + "/[0-9]+", "Updated a " + r[1]);
            label("DELETE " + path + "/[0-9]+", "Deleted a " + r[1]);
        }
        label("POST /api/admin/reb/sessions/[0-9]+/current", "Changed the current REB session");
        label("PUT /api/admin/reb/class-subjects", "Changed the subjects of a REB class");
        label("POST /api/admin/reb/class-subjects/copy", "Copied REB class subjects to another session");
        label("PUT /api/admin/reb/settings", "Changed REB settings");
        label("PUT /api/admin/reb/assignments", "Changed who teaches what at REB");
        label("POST /api/admin/reb/students", "Admitted a girl at REB");
        label("PUT /api/admin/reb/students/[0-9]+", "Updated a REB girl's details");
        label("POST /api/admin/reb/students/[0-9]+/status", "Changed a REB girl's status (left / passed out / back)");
        label("PUT /api/admin/reb/students/[0-9]+/enrollment", "Moved a REB girl to another section");
        label("POST /api/admin/reb/students/[0-9]+/guardians", "Added a REB guardian");
        label("PUT /api/admin/reb/guardians/[0-9]+", "Updated a REB guardian");
        label("DELETE /api/admin/reb/guardians/[0-9]+", "Removed a REB guardian");
        label("POST /api/admin/reb/students/[0-9]+/documents", "Uploaded a REB document");
        label("DELETE /api/admin/reb/documents/[0-9]+", "Removed a REB document");
        label("POST /api/admin/reb/students/import", "Imported REB girls from a file");
        label("POST /api/admin/reb/promotion", "Promoted REB girls to the next session");
        label("POST /api/public/website/reb/enquiries", "Sent a Rahbar-e-Banaat admission enquiry through the website");
        label("POST /api/admin/reb/enquiries", "Added a REB admission enquiry");
        label("PUT /api/admin/reb/enquiries/[0-9]+", "Updated a REB admission enquiry");
        label("POST /api/admin/reb/enquiries/[0-9]+/admit", "Admitted a girl from a REB enquiry");
        label("POST /api/admin/reb/quran", "Recorded a REB Quran lesson");
        label("DELETE /api/admin/reb/quran/[0-9]+", "Removed a REB Quran lesson");
        label("POST /api/admin/reb/hostel-leaves", "Gave a REB hostel gate pass");
        label("POST /api/admin/reb/hostel-leaves/[0-9]+/return", "Marked a REB girl back from leave");
        label("PUT /api/admin/reb/attendance", "Marked REB attendance");
        label("POST /api/admin/reb/calendar", "Added a REB holiday or event");
        label("PUT /api/admin/reb/calendar/[0-9]+", "Changed a REB holiday or event");
        label("DELETE /api/admin/reb/calendar/[0-9]+", "Removed a REB holiday or event");
        label("PUT /api/admin/reb/grade-scale", "Changed the REB grading scale");
        label("POST /api/admin/reb/exams", "Added a REB exam");
        label("PUT /api/admin/reb/exams/[0-9]+", "Updated a REB exam");
        label("DELETE /api/admin/reb/exams/[0-9]+", "Deleted a REB exam");
        label("POST /api/admin/reb/exams/[0-9]+/status", "Opened marks entry, published or unpublished a REB exam");
        label("PUT /api/admin/reb/exams/[0-9]+/papers", "Changed a REB exam's date sheet");
        label("PUT /api/admin/reb/papers/[0-9]+/marks", "Entered REB exam marks");
        label("PUT /api/admin/reb/exams/[0-9]+/remarks", "Wrote REB report card remarks");
        label("POST /api/admin/reb/fee-heads", "Added a REB fee head");
        label("PUT /api/admin/reb/fee-heads/[0-9]+", "Updated a REB fee head");
        label("PUT /api/admin/reb/fee-structure", "Changed REB fee amounts");
        label("POST /api/admin/reb/concessions", "Gave a REB fee concession");
        label("PUT /api/admin/reb/concessions/[0-9]+", "Changed a REB fee concession");
        label("DELETE /api/admin/reb/concessions/[0-9]+", "Removed a REB fee concession");
        label("POST /api/admin/reb/invoices/generate", "Made the REB bills for a month");
        label("POST /api/admin/reb/invoices/[0-9]+/waive", "Waived a REB bill");
        label("POST /api/admin/reb/invoices/[0-9]+/cancel", "Cancelled a REB bill");
        label("POST /api/admin/reb/payments", "Recorded a REB fee payment");
        label("POST /api/admin/reb/payments/[0-9]+/cancel", "Cancelled a REB receipt");
        label("POST /api/admin/reb/sponsorships", "Started a REB sponsorship");
        label("PUT /api/admin/reb/sponsorships/[0-9]+", "Changed a REB sponsorship");
        label("POST /api/admin/reb/sponsorships/[0-9]+/end", "Ended a REB sponsorship");
        label("POST /api/account/change-password", "Changed own password");
        label("PUT /api/account/profile", "Updated own profile");
        label("POST /api/public/apply", "Submitted a scholarship application");
        label("POST /api/public/applications/[^/]+/documents", "Uploaded an application document");
        label("POST /api/admin/application-period/start", "Started an application period");
        label("POST /api/admin/application-period/end", "Ended the application period");
        label("POST /api/admin/users", "Created a user");
        label("PUT /api/admin/users/[^/]+", "Updated a user");
        label("POST /api/admin/users/[^/]+/reset-password", "Reset a user's password");
        label("POST /api/admin/broadcasts", "Sent or scheduled a broadcast message");
        label("POST /api/admin/broadcasts/[0-9]+/cancel", "Cancelled a scheduled broadcast");
        label("POST /api/admin/broadcasts/templates", "Saved a broadcast template");
        label("DELETE /api/admin/broadcasts/templates/[^/]+", "Deleted a broadcast template");
        label("POST /api/admin/progress-due-dates", "Saved a progress due date");
        label("DELETE /api/admin/progress-due-dates/[^/]+", "Deleted a progress due date");
        label("POST /api/admin/progress-due-dates/reminders/run", "Sent student reminders");
        label("PUT /api/admin/roles/[^/]+/access", "Changed a role's permissions");
        label("POST /api/admin/roles", "Created a role");
        label("PUT /api/admin/roles/[^/]+", "Updated a role");
        label("DELETE /api/admin/roles/[^/]+", "Deleted a role");
        label("POST /api/admin/system-configuration", "Saved the payment config (amount and frequency) for a year");
        label("POST /api/admin/rcc-centers", "Saved an RCC center");
        label("DELETE /api/admin/rcc-centers/[^/]+", "Deleted an RCC center");
        label("POST /api/admin/chapters", "Saved a chapter");
        label("DELETE /api/admin/chapters/[^/]+", "Deleted a chapter");
        label("POST /api/admin/courses", "Saved a course");
        label("DELETE /api/admin/courses/[^/]+", "Deleted a course");
        label("POST /api/admin/institutions", "Added an institution");
        label("PUT /api/admin/institutions/[^/]+", "Edited an institution");
        label("PUT /api/admin/email-templates/[^/]+", "Saved an email template");
        label("POST /api/admin/students/merge", "Merged a duplicate student into another");
        label("POST /api/admin/people/merge", "Merged a person's second account into their main one");
        label("POST /api/student/notes", "Wrote a note to their sponsor");
        label("POST /api/admin/faqs", "Added an FAQ");
        label("PUT /api/admin/faqs/[0-9]+", "Changed an FAQ");
        label("DELETE /api/admin/faqs/[0-9]+", "Deleted an FAQ");
        label("POST /api/admin/student-notes/[0-9]+/review", "Approved or returned a student's note to their sponsor");
        label("POST /api/admin/sponsors/[^/]+/fee-schedule", "Generated the fee schedules of a sponsor's students");
        label("PUT /api/admin/applications/[^/]+/interview-scores", "Scored an interview");
        label("DELETE /api/admin/applications/[^/]+/interview-scores", "Removed own interview scores");
        label("POST /api/admin/interviews/criteria", "Saved an interview criterion");
        label("DELETE /api/admin/interviews/criteria/[^/]+", "Deleted an interview criterion");
        label("PUT /api/admin/alumni/[^/]+", "Updated an alumni profile");
        label("PUT /api/student/alumni-profile", "Updated own alumni profile");
        label("POST /api/admin/sponsors/[^/]+/statement/email", "Emailed a sponsor their yearly statement");
        label("DELETE /api/admin/email-templates/[^/]+", "Deleted an email template (back to the default)");
        label("DELETE /api/admin/institutions/[^/]+", "Deleted an institution");
        label("POST /api/admin/applications/[^/]+/status", "Changed an application status");
        label("POST /api/admin/applications/[^/]+/interview", "Scheduled an interview");
        label("POST /api/admin/manage-students/assign", "Assigned a student's course");
        label("POST /api/admin/sponsorships/[^/]+/map", "Mapped students to a sponsor");
        label("PUT /api/admin/students/[^/]+", "Updated a student record");
        label("POST /api/admin/students/[^/]+/study-status", "Changed a student's study status");
        label("POST /api/admin/students/[^/]+/action", "Changed a student's account (activate / deactivate / unmap)");
        label("POST /api/admin/students/bulk-upload", "Bulk-uploaded students");
        label("POST /api/admin/sponsors/bulk-upload", "Bulk-uploaded sponsors");
        label("POST /api/admin/students/manual-add", "Added a student");
        label("PUT /api/admin/sponsors/[^/]+", "Updated a sponsor profile");
        label("POST /api/admin/payments/record", "Recorded or edited a payment");
        label("POST /api/admin/payments/reminders/run", "Sent payment reminders");
        label("POST /api/progress/[^/]+/review", "Reviewed a progress report");
        label("POST /api/coordinator/assign-sponsor", "Assigned a sponsor");
        label("POST /api/coordinator/users/[^/]+/status/.*", "Changed a user's status");
        label("POST /api/coordinator/map-students/[^/]+", "Mapped students to a sponsor");
        label("POST /api/coordinator/appoint-convenor/[^/]+", "Appointed a convenor");
        label("POST /api/coordinator/users/[^/]+/chapter", "Changed a user's chapter");
        label("POST /api/coordinator/assign-students-bulk", "Assigned students to a sponsor");
        label("POST /api/convenor/applications/[^/]+/status", "Changed an application status");
        label("POST /api/convenor/sponsors/[^/]+/status/.*", "Changed a sponsor's status");
        label("POST /api/convenor/map-students/[^/]+", "Mapped students to a sponsor");
        label("POST /api/convenor/profile", "Updated own chapter");
        label("POST /api/convenor/payments", "Recorded a payment (pending approval)");
        label("POST /api/convenor/upload-file", "Uploaded a file");
        label("POST /api/sponsor/payments", "Recorded a payment");
        label("POST /api/student/bank-details", "Saved bank details");
        label("POST /api/student/progress", "Submitted a progress report");
        label("POST /api/student/payments/[^/]+/proof", "Uploaded a payment proof");
    }

    private static void label(String methodAndPath, String label) {
        LABELS.put(Pattern.compile(methodAndPath), label);
    }

    private final ActivityLogService activityLogService;

    public ActivityLogInterceptor(ActivityLogService activityLogService) {
        this.activityLogService = activityLogService;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                @NonNull Object handler, Exception ex) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if (!CHANGES.contains(method) || path == null || !path.startsWith("/api/") || SKIP.contains(path)
                || path.startsWith("/api/notifications")
                || path.endsWith("/preview") || path.startsWith("/api/push/")) return; // previews and push subscriptions change no records

        int status = response.getStatus();
        String key = method + " " + path;
        String action = method + " " + path;
        for (Map.Entry<Pattern, String> e : LABELS.entrySet()) {
            if (e.getKey().matcher(key).matches()) { action = e.getValue(); break; }
        }
        if (status >= 400) action = "Failed: " + action;
        activityLogService.record(currentUser(), action, method, path, status, clientIp(request));
    }

    private static User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof RahbarUserPrincipal p ? p.getUser() : null;
    }

    /**
     * Client address. With server.forward-headers-strategy=native, Tomcat takes X-Forwarded-For into account only
     * when the request comes from a trusted (private-network) proxy such as the nginx container, so it can't be forged.
     */
    public static String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
