package com.rahbar.service;

import com.rahbar.entity.GrantorGrantee;
import com.rahbar.entity.User;
import com.rahbar.repository.GrantorGranteeRepository;
import com.rahbar.repository.PaymentRepository;
import com.rahbar.repository.StudentInstitutionCourseRepository;
import com.rahbar.repository.UserRepository;
import com.rahbar.util.Rows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Payment dues and reminders. Each sponsored student pays in quarterly installments from the day their course was
 * assigned: (semesters / 2) * 4 installments, one every 3 months (the same schedule the sponsor pages show).
 * Every morning sponsors get one reminder per overdue installment and one a week before the next installment is due.
 */
@Service
public class PaymentReminderService {

    private static final Logger log = LoggerFactory.getLogger(PaymentReminderService.class);
    private static final int DUE_SOON_DAYS = 7;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy");

    private final GrantorGranteeRepository grantorGranteeRepository;
    private final UserRepository userRepository;
    private final StudentInstitutionCourseRepository studentCourseRepository;
    private final PaymentRepository paymentRepository;
    private final NotificationService notificationService;

    public PaymentReminderService(GrantorGranteeRepository grantorGranteeRepository, UserRepository userRepository,
                                  StudentInstitutionCourseRepository studentCourseRepository,
                                  PaymentRepository paymentRepository, NotificationService notificationService) {
        this.grantorGranteeRepository = grantorGranteeRepository;
        this.userRepository = userRepository;
        this.studentCourseRepository = studentCourseRepository;
        this.paymentRepository = paymentRepository;
        this.notificationService = notificationService;
    }

    /** One row per sponsored student: installments due vs paid, next due date and a status. */
    public List<Map<String, Object>> dues() {
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, User> users = new HashMap<>();
        userRepository.findAll().forEach(u -> users.put(u.getUserId(), u));

        for (GrantorGrantee gg : grantorGranteeRepository.findAll()) {
            if (gg.getGrantorId() == null || ServiceSupport.UNASSIGNED_GRANTOR.equals(gg.getGrantorId())) continue;
            User student = users.get(gg.getGranteeId());
            if (student == null || "Inactive".equalsIgnoreCase(student.getStatus())) continue;
            User sponsor = users.get(gg.getGrantorId());

            Map<String, Object> course = Rows.first(studentCourseRepository.findCourseInfo(student.getUserId()));
            LocalDate start = course != null && course.get("assigned_at") instanceof LocalDateTime t ? t.toLocalDate() : null;
            int semesters = course != null && course.get("number_of_semesters") instanceof Number n ? n.intValue() : 0;
            int total = (int) Math.floor(semesters / 2.0 * 4);
            long paid = paymentRepository.countByGranteeIdAndStatus(student.getUserId(), "Paid");

            int dueSoFar = 0;
            LocalDate nextDue = null;
            String status;
            if (start == null || total == 0) {
                status = "No course assigned";
            } else {
                for (int i = 1; i <= total; i++) {
                    if (!start.plusMonths(3L * i).isAfter(today)) dueSoFar++;
                }
                if (paid < total) nextDue = start.plusMonths(3L * (paid + 1));
                if (paid >= total) status = "Completed";
                else if (paid < dueSoFar) status = "Overdue";
                else if (nextDue != null && !nextDue.isAfter(today.plusDays(DUE_SOON_DAYS))) status = "Due soon";
                else status = "On schedule";
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("student_id", student.getUserId());
            row.put("student_name", student.getName());
            row.put("student_phone", student.getPhone());
            row.put("sponsor_id", gg.getGrantorId());
            row.put("sponsor_name", sponsor == null ? null : sponsor.getName());
            row.put("course_start", start);
            row.put("installments_total", total);
            row.put("installments_due", dueSoFar);
            row.put("installments_paid", paid);
            row.put("overdue", Math.max(0, dueSoFar - paid));
            row.put("next_due_date", nextDue);
            row.put("status", status);
            rows.add(row);
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> statusRank((String) r.get("status")))
                .thenComparing(r -> String.valueOf(r.get("student_name")), String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private static int statusRank(String status) {
        return switch (status) {
            case "Overdue" -> 0;
            case "Due soon" -> 1;
            case "On schedule" -> 2;
            case "No course assigned" -> 3;
            default -> 4;
        };
    }

    /** Every day at 8:00 (server time) unless app.reminders.cron says otherwise. */
    @Scheduled(cron = "${app.reminders.cron:0 0 8 * * *}")
    public void scheduledRun() {
        Map<String, Integer> sent = sendReminders();
        log.info("Payment reminders sent: {}", sent);
    }

    /** Sends the reminders that haven't been sent yet; returns how many of each kind went out. */
    public Map<String, Integer> sendReminders() {
        int overdue = 0;
        int upcoming = 0;
        for (Map<String, Object> d : dues()) {
            String sponsorId = (String) d.get("sponsor_id");
            String studentId = (String) d.get("student_id");
            String student = d.get("student_name") + " (" + studentId + ")";
            if ("Overdue".equals(d.get("status"))) {
                long late = ((Number) d.get("overdue")).longValue();
                String key = "reminder:overdue:" + studentId + ":" + d.get("installments_due");
                if (notificationService.notifyOnce(key, sponsorId, "Payment overdue",
                        late + " installment(s) for " + student + " are overdue. Please record the payment once it is made.",
                        NotificationService.REMINDER, "/sponsor/payments", true)) overdue++;
            } else if ("Due soon".equals(d.get("status"))) {
                LocalDate next = (LocalDate) d.get("next_due_date");
                String key = "reminder:upcoming:" + studentId + ":" + next;
                if (notificationService.notifyOnce(key, sponsorId, "Payment due soon",
                        "The next installment for " + student + " is due on " + next.format(DAY) + ".",
                        NotificationService.REMINDER, "/sponsor/payments", true)) upcoming++;
            }
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("overdueReminders", overdue);
        result.put("upcomingReminders", upcoming);
        return result;
    }
}
