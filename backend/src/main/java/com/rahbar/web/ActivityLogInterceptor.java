package com.rahbar.web;

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
