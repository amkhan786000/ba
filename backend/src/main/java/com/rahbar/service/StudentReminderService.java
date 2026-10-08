package com.rahbar.service;

import com.rahbar.entity.BankDetails;
import com.rahbar.entity.ProgressDueDate;
import com.rahbar.entity.StudentProgress;
import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.BankDetailsRepository;
import com.rahbar.repository.ProgressDueDateRepository;
import com.rahbar.repository.StudentProgressRepository;
import com.rahbar.repository.UserRepository;
import com.rahbar.util.Rows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Progress-report due dates (set by the office) and the reminders students get by email and in the app:
 * 14 days before a due date if they haven't uploaded a report for it yet, once when it is overdue (for up to
 * 60 days), and once a month while their bank details are missing. Only students who are studying get them.
 *
 * A report counts for a due date when it was uploaded after the previous due date (for the first due date:
 * within the 6 months before it).
 */
@Service
public class StudentReminderService {

    private static final Logger log = LoggerFactory.getLogger(StudentReminderService.class);
    static final int REMIND_DAYS_BEFORE = 14;
    static final int OVERDUE_REMINDER_DAYS = 60;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy");

    private final ProgressDueDateRepository dueDateRepository;
    private final UserRepository userRepository;
    private final StudentProgressRepository progressRepository;
    private final BankDetailsRepository bankDetailsRepository;
    private final NotificationService notificationService;

    public StudentReminderService(ProgressDueDateRepository dueDateRepository, UserRepository userRepository,
                                  StudentProgressRepository progressRepository, BankDetailsRepository bankDetailsRepository,
                                  NotificationService notificationService) {
        this.dueDateRepository = dueDateRepository;
        this.userRepository = userRepository;
        this.progressRepository = progressRepository;
        this.bankDetailsRepository = bankDetailsRepository;
        this.notificationService = notificationService;
    }

    // ------------------------------------------------------------------ due dates

    /** Every due date, oldest first, with how many studying students have / haven't uploaded a report for it. */
    public List<Map<String, Object>> dueDates() {
        List<ProgressDueDate> dates = dueDateRepository.findAllByOrderByDueDateAsc();
        List<User> students = studyingStudents();
        Map<Long, List<LocalDateTime>> uploads = uploadsByStudent();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < dates.size(); i++) {
            ProgressDueDate d = dates.get(i);
            LocalDate from = windowStart(dates, i);
            long submitted = students.stream().filter(u -> uploadedSince(uploads.get(u.getId()), from)).count();
            Map<String, Object> row = Rows.of(d);
            row.put("submitted", submitted);
            row.put("pending", students.size() - submitted);
            row.put("window_start", from);
            rows.add(row);
        }
        return rows;
    }

    public Map<String, Object> saveDueDate(Long id, String title, String date, String note) {
        String t = title == null ? "" : title.trim();
        if (t.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Please enter a title.");
        if (t.length() > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "The title can be at most 100 characters.");
        if (date == null || date.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "Please choose the due date.");
        LocalDate due = LocalDate.parse(date.trim());
        dueDateRepository.findByDueDate(due).filter(other -> !other.getDueId().equals(id)).ifPresent(other -> {
            throw new ApiException(HttpStatus.CONFLICT, "There is already a due date on " + due.format(DAY) + ".");
        });
        ProgressDueDate d = id == null ? new ProgressDueDate()
                : dueDateRepository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Due date not found."));
        d.setTitle(t);
        d.setDueDate(due);
        d.setNote(note == null || note.isBlank() ? null : note.trim());
        return Rows.of(dueDateRepository.save(d));
    }

    public void deleteDueDate(Long id) {
        dueDateRepository.deleteById(id);
    }

    /** For the student's own pages: the next due date and whether they have already uploaded for it. */
    public Map<String, Object> forStudent(Long userId) {
        Map<String, Object> none = new LinkedHashMap<>();
        none.put("next", null);
        none.put("overdue", List.of());
        // Only current students (not on hold, graduated or dropped out) are expected to send reports.
        String status = userRepository.findById(userId).map(User::getStudyStatus).orElse(null);
        if (!StudyStatus.getsReminders(status)) return none;
        List<ProgressDueDate> dates = dueDateRepository.findAllByOrderByDueDateAsc();
        List<LocalDateTime> mine = uploadsByStudent().getOrDefault(userId, List.of());
        LocalDate today = LocalDate.now();
        Map<String, Object> body = new LinkedHashMap<>();
        List<Map<String, Object>> overdue = new ArrayList<>();
        for (int i = 0; i < dates.size(); i++) {
            ProgressDueDate d = dates.get(i);
            boolean done = uploadedSince(mine, windowStart(dates, i));
            if (d.getDueDate().isBefore(today)) {
                if (!done && !d.getDueDate().isBefore(today.minusDays(OVERDUE_REMINDER_DAYS))) {
                    overdue.add(Map.of("title", d.getTitle(), "due_date", d.getDueDate()));
                }
            } else if (!body.containsKey("next")) {
                Map<String, Object> next = new LinkedHashMap<>();
                next.put("title", d.getTitle());
                next.put("due_date", d.getDueDate());
                next.put("note", d.getNote());
                next.put("submitted", done);
                body.put("next", next);
            }
        }
        body.putIfAbsent("next", null);
        body.put("overdue", overdue);
        return body;
    }

    // ------------------------------------------------------------------ reminders

    /** Every day at 8:00 (server time) unless app.reminders.cron says otherwise. */
    @Scheduled(cron = "${app.reminders.cron:0 0 8 * * *}")
    public void scheduledRun() {
        log.info("Student reminders sent: {}", sendReminders());
    }

    /** Sends the reminders due today that haven't been sent yet; returns how many of each kind went out. */
    public Map<String, Integer> sendReminders() {
        LocalDate today = LocalDate.now();
        List<ProgressDueDate> dates = dueDateRepository.findAllByOrderByDueDateAsc();
        List<User> students = studyingStudents().stream().filter(u -> StudyStatus.getsReminders(u.getStudyStatus())).toList();
        Map<Long, List<LocalDateTime>> uploads = uploadsByStudent();
        int upcoming = 0, overdue = 0, bank = 0;

        for (int i = 0; i < dates.size(); i++) {
            ProgressDueDate d = dates.get(i);
            LocalDate due = d.getDueDate();
            boolean soon = !today.isBefore(due.minusDays(REMIND_DAYS_BEFORE)) && today.isBefore(due);
            boolean late = !today.isBefore(due) && !today.isAfter(due.plusDays(OVERDUE_REMINDER_DAYS));
            if (!soon && !late) continue;
            LocalDate from = windowStart(dates, i);
            for (User u : students) {
                if (uploadedSince(uploads.get(u.getId()), from)) continue;
                if (soon && notificationService.notifyOnce("progress:upcoming:" + u.getUserId() + ":" + due, u.getId(),
                        "Progress report due " + due.format(DAY),
                        "Please upload your progress report (" + d.getTitle() + ") by " + due.format(DAY) + "."
                                + (d.getNote() == null ? "" : " " + d.getNote()),
                        NotificationService.REMINDER, "/student/progress", EmailType.PROGRESS_DUE_SOON,
                        ServiceSupport.vars("title", d.getTitle(), "due_date", due.format(DAY), "note", d.getNote()))) upcoming++;
                if (late && notificationService.notifyOnce("progress:overdue:" + u.getUserId() + ":" + due, u.getId(),
                        "Progress report overdue",
                        "Your progress report (" + d.getTitle() + ") was due on " + due.format(DAY)
                                + ". Please upload it as soon as possible.",
                        NotificationService.REMINDER, "/student/progress", EmailType.PROGRESS_OVERDUE,
                        ServiceSupport.vars("title", d.getTitle(), "due_date", due.format(DAY)))) overdue++;
            }
        }

        // Missing bank details: at most one reminder per student per month.
        Set<Long> withBank = new HashSet<>();
        for (BankDetails b : bankDetailsRepository.findAll()) withBank.add(b.getUserId());
        String month = today.toString().substring(0, 7);
        for (User u : students) {
            if (withBank.contains(u.getId())) continue;
            if (notificationService.notifyOnce("bank:missing:" + u.getUserId() + ":" + month, u.getId(),
                    "Please add your bank details",
                    "We don't have your bank details yet, so payments can't be sent to you. "
                            + "Please add them on your Payments page.",
                    NotificationService.REMINDER, "/student/payments", EmailType.BANK_DETAILS_MISSING, ServiceSupport.vars())) bank++;
        }
        return Map.of("progressUpcoming", upcoming, "progressOverdue", overdue, "bankDetailsMissing", bank);
    }

    // ------------------------------------------------------------------ helpers

    /** Active students who are studying or on hold (graduated / dropped out are left out). */
    private List<User> studyingStudents() {
        return userRepository.findByRoleId(6).stream()
                .filter(u -> !"Inactive".equalsIgnoreCase(u.getStatus()))
                .filter(u -> StudyStatus.inProgramme(u.getStudyStatus()))
                .toList();
    }

    private Map<Long, List<LocalDateTime>> uploadsByStudent() {
        Map<Long, List<LocalDateTime>> map = new HashMap<>();
        for (StudentProgress p : progressRepository.findAll()) {
            if (p.getCreatedAt() != null) map.computeIfAbsent(p.getGranteeId(), k -> new ArrayList<>()).add(p.getCreatedAt());
        }
        return map;
    }

    /** Uploads after this day count (the day after the previous due date; 6 months back for the first one). */
    private static LocalDate windowStart(List<ProgressDueDate> dates, int index) {
        return index > 0 ? dates.get(index - 1).getDueDate().plusDays(1) : dates.get(index).getDueDate().minusMonths(6);
    }

    private static boolean uploadedSince(List<LocalDateTime> uploads, LocalDate from) {
        return uploads != null && uploads.stream().anyMatch(t -> !t.toLocalDate().isBefore(from));
    }
}
