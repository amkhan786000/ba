package com.rahbar.service;

import com.rahbar.entity.GrantorGrantee;
import com.rahbar.entity.PaymentInstallment;
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
 * Payment dues and reminders, from the installments stored for each sponsored student (PaymentInstallmentService:
 * built from the course, the Payment Config of the student's session year and the payment start date).
 * Every morning sponsors get one reminder per overdue installment and one a week before the next installment is due.
 */
@Service
public class PaymentReminderService {

    private static final Logger log = LoggerFactory.getLogger(PaymentReminderService.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy");

    private final GrantorGranteeRepository grantorGranteeRepository;
    private final UserRepository userRepository;
    private final StudentInstitutionCourseRepository studentCourseRepository;
    private final PaymentRepository paymentRepository;
    private final NotificationService notificationService;
    private final PaymentInstallmentService installmentService;

    public PaymentReminderService(GrantorGranteeRepository grantorGranteeRepository, UserRepository userRepository,
                                  StudentInstitutionCourseRepository studentCourseRepository,
                                  PaymentRepository paymentRepository, NotificationService notificationService,
                                  PaymentInstallmentService installmentService) {
        this.installmentService = installmentService;
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
        Map<Long, User> users = new HashMap<>();
        userRepository.findAll().forEach(u -> users.put(u.getId(), u));
        Long unassigned = ServiceSupport.unassignedGrantorId(userRepository);
        Map<Long, List<PaymentInstallment>> byStudent = installmentService.byStudent();
        // "Due soon" window per student: the "show as due" days of their session year's Payment Config.
        Map<Long, Integer> notice = installmentService.noticeDays(byStudent.keySet());

        for (GrantorGrantee gg : grantorGranteeRepository.findAll()) {
            if (gg.getGrantorId() == null || gg.getGrantorId().equals(unassigned)) continue;
            User student = users.get(gg.getGranteeId());
            if (student == null || "Inactive".equalsIgnoreCase(student.getStatus())) continue;
            if (!StudyStatus.inProgramme(student.getStudyStatus())) continue; // graduated / dropped out: no dues
            User sponsor = users.get(gg.getGrantorId());

            // From the stored installments (see PaymentInstallmentService): due = its date has come.
            List<PaymentInstallment> installments = byStudent.getOrDefault(student.getId(), List.of());
            int total = installments.size();
            long paid = installments.stream().filter(i -> i.getPaymentId() != null).count();
            int dueSoFar = (int) installments.stream().filter(i -> !i.getDueDate().isAfter(today)).count();
            long overdueCount = installments.stream().filter(i -> i.getPaymentId() == null && i.getDueDate().isBefore(today)).count();
            int noticeDays = notice.getOrDefault(student.getId(), PaymentInstallmentService.DEFAULT_NOTICE_DAYS);
            LocalDate nextDue = installments.stream().filter(i -> i.getPaymentId() == null)
                    .map(PaymentInstallment::getDueDate).min(Comparator.naturalOrder()).orElse(null);
            LocalDate start = student.getPaymentStartDate();
            String status;
            if (total == 0) {
                status = "No schedule";
            } else if (paid >= total) {
                status = "Completed";
            } else if (overdueCount > 0) {
                status = "Overdue";
            } else if (nextDue != null && !nextDue.isAfter(today.plusDays(noticeDays))) {
                status = "Due soon";
            } else {
                status = "On schedule";
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("student_id", student.getId());
            row.put("student_code", student.getUserId());
            row.put("student_name", student.getName());
            row.put("student_phone", student.getPhone());
            row.put("sponsor_id", gg.getGrantorId());
            row.put("sponsor_code", sponsor == null ? null : sponsor.getUserId());
            row.put("sponsor_name", sponsor == null ? null : sponsor.getName());
            row.put("course_start", start);
            row.put("installments_total", total);
            row.put("installments_due", dueSoFar);
            row.put("installments_paid", paid);
            row.put("overdue", overdueCount);
            row.put("next_due_date", nextDue);
            // On hold: dues are still listed, but with this status no reminder goes out.
            if (StudyStatus.ON_HOLD.equals(student.getStudyStatus())) status = "On hold";
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
            case "On hold" -> 3;
            case "No schedule" -> 4;
            default -> 5;
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
            Long sponsorId = (Long) d.get("sponsor_id");
            // The student's code (not users.id) keeps reminder keys the same as before the numeric-id change.
            String studentId = (String) d.get("student_code");
            String student = d.get("student_name") + " (" + studentId + ")";
            if ("Overdue".equals(d.get("status"))) {
                long late = ((Number) d.get("overdue")).longValue();
                String key = "reminder:overdue:" + studentId + ":" + d.get("installments_due");
                if (notificationService.notifyOnce(key, sponsorId, "Payment overdue",
                        late + " installment(s) for " + student + " are overdue. Please record the payment once it is made.",
                        NotificationService.REMINDER, "/sponsor/payments", EmailType.PAYMENT_OVERDUE,
                        ServiceSupport.vars("count", late, "student_name", d.get("student_name"), "student_code", studentId))) overdue++;
            } else if ("Due soon".equals(d.get("status"))) {
                LocalDate next = (LocalDate) d.get("next_due_date");
                String key = "reminder:upcoming:" + studentId + ":" + next;
                if (notificationService.notifyOnce(key, sponsorId, "Payment due soon",
                        "The next installment for " + student + " is due on " + next.format(DAY) + ".",
                        NotificationService.REMINDER, "/sponsor/payments", EmailType.PAYMENT_DUE_SOON,
                        ServiceSupport.vars("student_name", d.get("student_name"), "student_code", studentId, "due_date", next.format(DAY)))) upcoming++;
            }
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("overdueReminders", overdue);
        result.put("upcomingReminders", upcoming);
        return result;
    }
}
