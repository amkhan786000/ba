package com.rahbar.service;

import com.rahbar.dto.RebPdf;
import com.rahbar.entity.*;
import com.rahbar.repository.*;
import com.rahbar.security.Access;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;

import static com.rahbar.util.RebSupport.notFound;

/**
 * What a Rahbar sponsor sees of the Rahbar-e-Banaat girls they sponsor (in their own portal): her name and class,
 * the sponsorship, her attendance this session, her published results, and the payments they made for her with
 * their receipts. Nothing about other girls, her family or her documents.
 */
@Service
public class RebSponsorPortalService {

    private final RebSponsorshipRepository sponsorships;
    private final RebStudentRepository students;
    private final RebEnrollmentRepository enrollments;
    private final RebSessionRepository sessions;
    private final RebPaymentRepository payments;
    private final RebStudentService studentService;
    private final RebAttendanceService attendance;
    private final RebExamService exams;
    private final RebReceiptService receipts;

    public RebSponsorPortalService(RebSponsorshipRepository sponsorships, RebStudentRepository students, RebEnrollmentRepository enrollments,
                                   RebSessionRepository sessions, RebPaymentRepository payments, RebStudentService studentService,
                                   RebAttendanceService attendance, RebExamService exams, RebReceiptService receipts) {
        this.sponsorships = sponsorships;
        this.students = students;
        this.enrollments = enrollments;
        this.sessions = sessions;
        this.payments = payments;
        this.studentService = studentService;
        this.attendance = attendance;
        this.exams = exams;
        this.receipts = receipts;
    }

    private static Long me() {
        return Access.current().getUser().getId();
    }

    /** The girls the signed-in sponsor sponsors or sponsored. */
    public List<Map<String, Object>> mine() {
        Long me = me();
        RebSession current = sessions.findFirstByCurrentTrue().orElse(null);
        Map<Long, String> labels = studentService.sectionLabels();
        String now = YearMonth.now().toString();
        List<Map<String, Object>> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (RebSponsorship s : sponsorships.findAllByOrderByIdDesc()) {
            if (!me.equals(s.getSponsorUserId()) || !seen.add(s.getStudentId())) continue;
            RebStudent g = students.findById(s.getStudentId()).orElse(null);
            if (g == null) continue;
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("student_id", g.getId());
            r.put("name", g.getName());
            r.put("status", g.getStatus());
            r.put("class", current == null ? null : enrollments.findByStudentIdAndSessionId(g.getId(), current.getId())
                    .map(e -> labels.get(e.getSectionId())).orElse(null));
            r.put("monthly_amount", s.getMonthlyAmount());
            r.put("since", s.getStartPeriod());
            r.put("until", s.getEndPeriod());
            r.put("current", RebSponsorshipService.covers(s, now));
            r.put("attendance_percent", current == null ? null
                    : attendance.summary(g.getId(), current.getStartDate(), current.getEndDate()).get("percent"));
            List<Map<String, Object>> results = new ArrayList<>();
            for (Map<String, Object> x : exams.ofStudent(g.getId())) {
                results.add(Map.of("exam", String.valueOf(x.get("exam_name")), "percent", x.get("percent") == null ? "" : x.get("percent"),
                        "grade", x.get("grade") == null ? "" : x.get("grade"), "result", x.get("result") == null ? "" : x.get("result")));
            }
            r.put("results", results);
            List<Map<String, Object>> paid = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO;
            for (RebPayment p : payments.findByStudentIdOrderByPaidOnAscIdAsc(g.getId())) {
                if (!"SPONSOR".equals(p.getPayer()) || !me.equals(p.getSponsorUserId()) || !"VALID".equals(p.getStatus())) continue;
                paid.add(Map.of("id", p.getId(), "receipt_no", p.getReceiptNo(), "paid_on", p.getPaidOn(), "amount", p.getAmount()));
                total = total.add(p.getAmount());
            }
            Collections.reverse(paid);
            r.put("payments", paid);
            r.put("total_paid", total);
            out.add(r);
        }
        return out;
    }

    /** A receipt of a payment the signed-in sponsor made. */
    public RebPdf receipt(Long paymentId) {
        RebPayment p = payments.findById(paymentId).orElseThrow(() -> notFound("Receipt"));
        if (!"SPONSOR".equals(p.getPayer()) || !me().equals(p.getSponsorUserId())) throw notFound("Receipt");
        return receipts.receipt(paymentId);
    }
}
