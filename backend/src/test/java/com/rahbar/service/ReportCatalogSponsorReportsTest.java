package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.repository.*;
import com.rahbar.security.SponsorPrivacy;
import com.rahbar.security.StudentScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Reports > Sponsors: students by sponsor (with subtotals and the unassigned at the end) and sponsors with overdue installments. */
class ReportCatalogSponsorReportsTest {

    private final UserRepository users = mock(UserRepository.class);
    private final GrantorGranteeRepository mappings = mock(GrantorGranteeRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentInstallmentRepository installments = mock(PaymentInstallmentRepository.class);
    private final SponsorMappingHistoryRepository history = mock(SponsorMappingHistoryRepository.class);
    private final StudentScope scope = mock(StudentScope.class);
    private final SponsorPrivacy privacy = mock(SponsorPrivacy.class);
    private final StudentInstitutionCourseRepository enrolments = mock(StudentInstitutionCourseRepository.class);
    private final InstitutionRepository institutions = mock(InstitutionRepository.class);
    private final CourseRepository courses = mock(CourseRepository.class);
    private ReportCatalogService service;

    private final User zara = user(1L, "SP1", "Zara Sponsor", 5);
    private final User adil = user(2L, "SP2", "Adil Sponsor", 5);
    private final User s1 = user(11L, "ST11", "Bina", 6);
    private final User s2 = user(12L, "ST12", "Aman", 6);
    private final User s3 = user(13L, "ST13", "Chand", 6);
    private final User s4 = user(14L, "ST14", "Dev", 6);

    private static User user(Long id, String code, String name, int role) {
        User u = new User();
        u.setId(id);
        u.setUserId(code);
        u.setName(name);
        u.setRoleId(role);
        u.setStatus("Active");
        return u;
    }

    private static GrantorGrantee map(Long sponsor, Long student) {
        GrantorGrantee g = new GrantorGrantee();
        g.setGrantorId(sponsor);
        g.setGranteeId(student);
        return g;
    }

    private static PaymentInstallment due(Long sponsor, Long student, LocalDate on, String amount, Long paymentId) {
        PaymentInstallment i = new PaymentInstallment();
        i.setGrantorId(sponsor);
        i.setGranteeId(student);
        i.setDueDate(on);
        i.setAmount(new BigDecimal(amount));
        i.setPaymentId(paymentId);
        return i;
    }

    @BeforeEach
    void setUp() {
        service = new ReportCatalogService(privacy, users, mock(RoleRepository.class), mock(GranteeDetailsRepository.class),
                mock(ApplicationStatusRepository.class), mappings, enrolments,
                institutions, courses, mock(BankDetailsRepository.class), payments,
                mock(PaymentScheduleRepository.class), mock(StudentProgressRepository.class), mock(RccCenterRepository.class),
                mock(PaymentReminderService.class), mock(ActivityLogService.class), mock(ReportService.class), scope,
                installments, history, mock(VolunteerApplicationRepository.class), mock(ChapterRepository.class),
                mock(AlumniService.class), mock(MentoringService.class));
        when(scope.current()).thenReturn(StudentScope.Scope.ALL);
        when(privacy.hidesDetails()).thenReturn(false);
        when(users.findAll()).thenReturn(List.of(zara, adil, s1, s2, s3, s4));
        when(users.findByRoleId(ServiceSupport.STUDENT_ROLE)).thenReturn(List.of(s1, s2, s3, s4));
        when(mappings.findAll()).thenReturn(List.of(map(1L, 11L), map(1L, 12L), map(2L, 13L)));
        SponsorMappingHistory h = new SponsorMappingHistory();
        h.setGrantorId(1L);
        h.setGranteeId(11L);
        h.setStartedAt(LocalDateTime.of(2025, 7, 1, 10, 0));
        when(history.findAll()).thenReturn(List.of(h));
        Payment p = new Payment();
        p.setGrantorId(1L);
        p.setGranteeId(11L);
        p.setAmount(new BigDecimal("6000"));
        p.setStatus("Paid");
        when(payments.findAll()).thenReturn(List.of(p));
        LocalDate today = LocalDate.now();
        when(installments.findAllByOrderByGranteeIdAscInstallmentNoAsc()).thenReturn(List.of(
                due(1L, 12L, today.minusDays(40), "3000", null),
                due(1L, 12L, today.minusDays(10), "3000", null),
                due(1L, 11L, today.minusDays(60), "3000", 99L),     // paid
                due(2L, 13L, today.plusDays(20), "3000", null)));   // not due yet
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(String key) {
        return (List<Map<String, Object>>) service.data(key, null, null).get("rows");
    }

    @Test
    void studentsBySponsorGroupsWithSubtotalsAndUnassignedLast() {
        List<Map<String, Object>> rows = rows("students_by_sponsor");
        assertEquals(List.of("Adil Sponsor", "Adil Sponsor", "Zara Sponsor", "Zara Sponsor", "Zara Sponsor", "Unassigned", "Unassigned"),
                rows.stream().map(r -> r.get("sponsor_name")).toList());
        assertEquals(Arrays.asList("ST13", null, "ST12", "ST11", null, "ST14", null), rows.stream().map(r -> r.get("student_id")).toList());

        Map<String, Object> bina = rows.get(3);
        assertEquals(LocalDate.of(2025, 7, 1), bina.get("sponsored_since"));
        assertEquals(1L, bina.get("installments_paid"));
        Map<String, Object> aman = rows.get(2);
        assertEquals(2L, aman.get("overdue_installments"));
        Map<String, Object> zaraTotal = rows.get(4);
        assertEquals("Subtotal: 2 students", zaraTotal.get("student_name"));
        assertEquals(1L, zaraTotal.get("installments_paid"));
        assertEquals(2L, zaraTotal.get("overdue_installments"));
        assertFalse(zaraTotal.containsKey("overdue_amount")); // amounts are never shown
        assertEquals("Subtotal: 1 student", rows.get(6).get("student_name"));
    }

    @Test
    void sponsorsOverdueCountsOnlyUnpaidPastDueInstallments() {
        List<Map<String, Object>> rows = rows("sponsors_overdue");
        assertEquals(1, rows.size());
        Map<String, Object> r = rows.get(0);
        assertEquals("SP1", r.get("sponsor_id"));
        assertEquals(1, r.get("students"));
        assertEquals(2L, r.get("overdue_installments"));
        assertEquals(40L, r.get("days_overdue"));
    }

    @Test
    void studentsByCourseAndInstitutionPerSessionYear() {
        s1.setYear(2024); s2.setYear(2025); s3.setYear(2025); s4.setYear(null);
        var patna = new com.rahbar.entity.Institution(); patna.setInstitutionId("I1"); patna.setInstitutionName("Patna College");
        var bsc = new com.rahbar.entity.Course(); bsc.setCourseId(5L); bsc.setInstitutionId("I1"); bsc.setCourseName("B.Sc");
        when(institutions.findAll()).thenReturn(List.of(patna));
        when(courses.findAll()).thenReturn(List.of(bsc));
        List<com.rahbar.entity.StudentInstitutionCourse> sic = new java.util.ArrayList<>();
        for (long id : new long[]{11, 12, 14}) {
            var e = new com.rahbar.entity.StudentInstitutionCourse(); e.setUserId(id); e.setInstitutionId("I1"); e.setCourseId(5L); sic.add(e);
        }
        when(enrolments.findAll()).thenReturn(sic); // s3 has no course

        List<Map<String, Object>> byCourse = rows("students_by_course_and_year");
        assertEquals(2, byCourse.size());
        Map<String, Object> b = byCourse.get(0);
        assertEquals("B.Sc", b.get("course"));
        assertEquals("Patna College", b.get("institution"));
        assertEquals(1L, b.get("year_2024"));
        assertEquals(1L, b.get("year_2025"));
        assertEquals(1L, b.get("year_not_set"));
        assertEquals(3L, b.get("total_students"));
        assertEquals("No course assigned yet", byCourse.get(1).get("institution")); // last
        assertEquals(1L, byCourse.get(1).get("year_2025"));
        assertEquals(0L, byCourse.get(1).get("year_2024")); // every row has every year column

        List<Map<String, Object>> byInstitution = rows("students_by_institution_and_year");
        assertEquals("Patna College", byInstitution.get(0).get("institution"));
        assertFalse(byInstitution.get(0).containsKey("course"));
        assertEquals(3L, byInstitution.get(0).get("total_students"));
    }
}
