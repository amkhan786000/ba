package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.repository.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Sponsor dashboard overview: study picture, latest news, past students now, impact. */
class SponsorOverviewTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final StudentInstitutionCourseRepository courses = mock(StudentInstitutionCourseRepository.class);
    private final StudentProgressRepository progress = mock(StudentProgressRepository.class);
    private final SponsorMappingHistoryRepository history = mock(SponsorMappingHistoryRepository.class);
    private final AlumniProfileRepository alumni = mock(AlumniProfileRepository.class);
    private final GrantorGranteeRepository mappings = mock(GrantorGranteeRepository.class);
    private final PaymentInstallmentService installments = mock(PaymentInstallmentService.class);
    private final SponsorService service = new SponsorService(users, mappings, payments, mock(PaymentScheduleRepository.class),
            mock(BankDetailsRepository.class), courses, progress, mock(FileStorageService.class), mock(NotificationService.class),
            installments, history, alumni);

    private static User student(long id, String name, String status) {
        User u = new User();
        u.setId(id);
        u.setUserId("ST" + id);
        u.setName(name);
        u.setRoleId(6);
        u.setStudyStatus(status);
        return u;
    }

    private static Payment paid(long student, String amount, LocalDateTime on, String proof) {
        Payment p = new Payment();
        p.setGrantorId(1L);
        p.setGranteeId(student);
        p.setAmount(new BigDecimal(amount));
        p.setPaymentDate(on);
        p.setStatus("Paid");
        p.setStudentProofUrl(proof);
        return p;
    }

    @Test
    @SuppressWarnings("unchecked")
    void showsTheStudyPictureNewsPastStudentsAndImpact() {
        LocalDate today = LocalDate.now();
        User asha = student(11, "Asha", null), quiet = student(12, "Bilal", null), grad = student(13, "Chand", StudyStatus.GRADUATED);
        asha.setPaymentStartDate(today.minusMonths(14)); // third semester of eight
        when(users.findGranteesOf(1L)).thenReturn(List.of(asha, quiet));
        when(users.findById(11L)).thenReturn(Optional.of(asha));
        when(users.findById(12L)).thenReturn(Optional.of(quiet));
        when(users.findById(13L)).thenReturn(Optional.of(grad));
        when(mappings.findByGrantorId(1L)).thenReturn(List.of());
        Map<String, Object> course = new HashMap<>();
        course.put("course_name", "B.Tech");
        course.put("institution_name", "NIT Patna");
        course.put("number_of_semesters", 8);
        course.put("assigned_at", today.minusMonths(14).atStartOfDay());
        when(courses.findCourseDetails(11L)).thenReturn(List.of(course));
        StudentProgress sp = new StudentProgress();
        sp.setMarks("82%");
        sp.setYear(2026);
        sp.setCreatedAt(LocalDateTime.now().minusDays(20));
        when(progress.findByGranteeIdOrderByCreatedAtDesc(11L)).thenReturn(List.of(sp));
        when(payments.findByGrantorIdOrderByPaymentDateDesc(1L)).thenReturn(List.of(
                paid(11, "6000", LocalDateTime.now().minusDays(30), "proof.pdf"),
                paid(12, "6000", LocalDateTime.now().minusDays(300), null),
                paid(13, "9000", LocalDateTime.of(2023, 7, 1, 10, 0), null)));
        SponsorMappingHistory h = new SponsorMappingHistory();
        h.setGrantorId(1L);
        h.setGranteeId(13L);
        h.setStartedAt(LocalDateTime.of(2022, 7, 1, 10, 0));
        h.setEndedAt(LocalDateTime.of(2024, 6, 30, 10, 0));
        when(history.findByGrantorIdOrderByStartedAtDesc(1L)).thenReturn(List.of(h));
        AlumniProfile a = new AlumniProfile();
        a.setUserId(13L);
        a.setCurrentStatus("EMPLOYED");
        a.setOrganisation("TCS");
        a.setConsentToContact(true);
        when(alumni.findById(13L)).thenReturn(Optional.of(a));

        when(installments.label(org.mockito.ArgumentMatchers.any())).thenReturn("installment 2");
        Map<String, Object> o = service.overview(1L);

        List<Map<String, Object>> students = (List<Map<String, Object>>) o.get("students");
        Map<String, Object> first = students.get(0);
        assertEquals("B.Tech", first.get("course"));
        assertEquals(3, first.get("semester_now"));
        assertEquals(today.minusMonths(14).plusMonths(48), first.get("expected_completion"));
        assertEquals(true, ((Map<String, Object>) first.get("last_payment")).get("spent_proof"));
        assertEquals(false, first.get("quiet"));
        assertEquals(true, students.get(1).get("quiet")); // nothing for 300 days

        Map<String, Object> past = ((List<Map<String, Object>>) o.get("past")).get(0);
        assertEquals("TCS", ((Map<String, Object>) past.get("now")).get("organisation"));
        a.setConsentToContact(false);
        assertNull(((List<Map<String, Object>>) service.overview(1L).get("past")).get(0).get("now"));

        Map<String, Object> impact = (Map<String, Object>) o.get("impact");
        assertNull(impact.get("total_given")); // amounts are never shown: installments paid instead
        assertEquals(3, impact.get("payments"));
        assertEquals("installment 2", ((Map<String, Object>) first.get("last_payment")).get("installment"));
        assertNull(((Map<String, Object>) first.get("last_payment")).get("amount"));
        assertEquals(3, impact.get("students_supported"));
        assertEquals(2, impact.get("studying_now"));
        assertEquals(1L, impact.get("graduated"));
        assertEquals(2022, impact.get("since_year"));
    }
}
