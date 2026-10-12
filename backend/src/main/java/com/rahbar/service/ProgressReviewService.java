package com.rahbar.service;

import com.rahbar.util.UserRoles;
import com.rahbar.entity.StudentProgress;
import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.GrantorGranteeRepository;
import com.rahbar.repository.StudentProgressRepository;
import com.rahbar.repository.UserRepository;
import com.rahbar.util.Rows;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Review of the progress reports (marks + marksheet) students upload.
 * Who may review: admins and office coordinators (any student), the student's own sponsor,
 * and the convenor of the student's chapter.
 */
@Service
public class ProgressReviewService {

    public static final Set<String> STATUSES = Set.of("Pending", "Approved", "Rejected");

    private final StudentProgressRepository studentProgressRepository;
    private final GrantorGranteeRepository grantorGranteeRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public ProgressReviewService(StudentProgressRepository studentProgressRepository,
                                 GrantorGranteeRepository grantorGranteeRepository, UserRepository userRepository,
                                 NotificationService notificationService) {
        this.studentProgressRepository = studentProgressRepository;
        this.grantorGranteeRepository = grantorGranteeRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    public Map<String, Object> review(User reviewer, Long progressId, String status, String comment) {
        if (!STATUSES.contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Status must be Pending, Approved or Rejected.");
        }
        if ("Rejected".equals(status) && (comment == null || comment.isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please add a comment telling the student what to fix.");
        }
        StudentProgress progress = studentProgressRepository.findById(progressId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Progress report not found."));
        if (!canReview(reviewer, progress.getGranteeId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only review progress of your own students.");
        }

        progress.setReviewStatus(status);
        progress.setReviewComment(comment == null || comment.isBlank() ? null : comment.trim());
        progress.setReviewedBy(reviewer.getId());
        progress.setReviewedAt(LocalDateTime.now());
        studentProgressRepository.save(progress);

        if (!"Pending".equals(status)) {
            String what = "your progress report for " + progress.getSession() + " (" + progress.getYear() + ")";
            notificationService.notify(progress.getGranteeId(),
                    "Approved".equals(status) ? "Progress report approved" : "Progress report needs changes",
                    ("Approved".equals(status) ? reviewer.getName() + " approved " + what + "."
                            : reviewer.getName() + " sent back " + what + ": " + progress.getReviewComment()),
                    NotificationService.PROGRESS, "/student/progress",
                    "Approved".equals(status) ? EmailType.PROGRESS_APPROVED : EmailType.PROGRESS_RETURNED,
                    ServiceSupport.vars("reviewer", reviewer.getName(), "session", progress.getSession(),
                            "year", progress.getYear(), "comment", progress.getReviewComment()));
        }
        return Rows.of(progress);
    }

    /** Any of the reviewer's roles (main or additional) that may review this student's report is enough. */
    private boolean canReview(User reviewer, Long granteeId) {
        if (UserRoles.hasAny(reviewer, List.of(1, 2, 8), userRepository)) return true;
        if (UserRoles.has(reviewer, 5, userRepository) && grantorGranteeRepository.existsByGranteeIdAndGrantorId(granteeId, reviewer.getId())) return true;
        if (UserRoles.has(reviewer, 4, userRepository)) {
            Long chapterId = reviewer.getChapterId();
            return chapterId != null && userRepository.findById(granteeId)
                    .map(s -> chapterId.equals(s.getChapterId())).orElse(false);
        }
        return false;
    }
}
