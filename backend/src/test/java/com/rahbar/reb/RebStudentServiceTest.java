package com.rahbar.reb;

import com.rahbar.entity.Role;
import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.UserRepository;
import com.rahbar.security.RahbarUserPrincipal;
import com.rahbar.service.FileStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** REB girls: admission numbers, promotion order, private details and guardians. */
class RebStudentServiceTest {

    private final RebStudentRepository students = mock(RebStudentRepository.class);
    private final RebGuardianRepository guardians = mock(RebGuardianRepository.class);
    private final RebEnrollmentRepository enrollments = mock(RebEnrollmentRepository.class);
    private final RebSectionRepository sections = mock(RebSectionRepository.class);
    private final RebClassRepository classes = mock(RebClassRepository.class);
    private final RebSessionRepository sessions = mock(RebSessionRepository.class);
    private final RebSetupService setup = mock(RebSetupService.class);
    private final RebStudentService service = new RebStudentService(students, guardians, enrollments,
            mock(RebStudentDocumentRepository.class), sections, classes, sessions, setup, mock(FileStorageService.class), mock(UserRepository.class));

    private static void signIn(String... permissions) {
        User u = new User();
        u.setId(9L);
        u.setRoleId(2);
        var principal = new RahbarUserPrincipal(u, Set.of(permissions), Role.SCOPE_ALL);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private static RebClass cls(long id, String name, int order) {
        RebClass c = new RebClass();
        c.setId(id);
        c.setName(name);
        c.setLevelOrder(order);
        return c;
    }

    @Test
    void admissionNumbersCountUpWithinTheYear() {
        when(setup.setting("admission_prefix", "REB")).thenReturn("REB");
        when(students.findAdmissionNosStartingWith("REB/2026/")).thenReturn(List.of("REB/2026/0007", "REB/2026/0012", "REB/2026/old"));
        assertEquals("REB/2026/0013", service.nextAdmissionNo(LocalDate.of(2026, 5, 1)));
        when(students.findAdmissionNosStartingWith("REB/2027/")).thenReturn(List.of());
        assertEquals("REB/2027/0001", service.nextAdmissionNo(LocalDate.of(2027, 1, 3)));
    }

    @Test
    void theNextClassIsTheChosenOneElseTheNextActiveByOrder() {
        RebClass six = cls(1, "Class 6", 6), seven = cls(2, "Class 7", 7), hidden = cls(3, "Old 8", 8), eight = cls(4, "Class 8", 9);
        hidden.setActive(false);
        List<RebClass> ordered = List.of(six, seven, hidden, eight);
        Map<Long, RebClass> byId = Map.of(1L, six, 2L, seven, 3L, hidden, 4L, eight);
        assertEquals(seven, RebStudentService.nextClass(six, ordered, byId));
        assertEquals(eight, RebStudentService.nextClass(seven, ordered, byId)); // skips the inactive class
        assertNull(RebStudentService.nextClass(eight, ordered, byId));          // last class: she passes out
        six.setNextClassId(4L);
        assertEquals(eight, RebStudentService.nextClass(six, ordered, byId));
    }

    @Test
    void onlyTheLastFourAadhaarDigitsAreKeptAndOnlyWithThePrivatePermission() {
        signIn("REB_STUDENTS:EDIT", "REB_STUDENT_PRIVATE:EDIT");
        RebStudent s = new RebStudent();
        service.readStudent(s, new HashMap<>(Map.of("name", "Sana", "aadhaarLast4", "1234 5678 9012")), true);
        assertEquals("9012", s.getAadhaarLast4());
        assertThrows(ApiException.class, () -> service.readStudent(s, new HashMap<>(Map.of("aadhaarLast4", "12345")), false));

        signIn("REB_STUDENTS:EDIT"); // no private permission: the value sent is ignored
        service.readStudent(s, new HashMap<>(Map.of("aadhaarLast4", "1111")), false);
        assertEquals("9012", s.getAadhaarLast4());
    }

    @Test
    void privateDetailsAreLeftOutWithoutThePermission() {
        signIn("REB_STUDENTS:VIEW");
        RebStudent s = new RebStudent();
        s.setId(5L);
        s.setName("Sana");
        s.setAdmissionNo("REB/2026/0001");
        s.setAadhaarLast4("9012");
        s.setPhotoPath("reb_5_photo.jpg");
        when(students.findById(5L)).thenReturn(Optional.of(s));
        RebGuardian g = new RebGuardian();
        g.setStudentId(5L);
        g.setName("Imran");
        g.setMonthlyIncome(new java.math.BigDecimal("8000"));
        when(guardians.findByStudentIdOrderByPrimaryDescIdAsc(5L)).thenReturn(List.of(g));
        when(enrollments.findByStudentId(5L)).thenReturn(List.of());

        Map<String, Object> p = service.get(5L);
        @SuppressWarnings("unchecked") Map<String, Object> student = (Map<String, Object>) p.get("student");
        assertFalse(student.containsKey("aadhaar_last4"));
        assertFalse(student.containsKey("photo_path"));
        @SuppressWarnings("unchecked") List<Map<String, Object>> gs = (List<Map<String, Object>>) p.get("guardians");
        assertFalse(gs.get(0).containsKey("monthly_income"));
        assertNull(p.get("documents"));
    }

    @Test
    void aGirlKeepsAtLeastOneGuardian() {
        RebGuardian only = new RebGuardian();
        only.setId(1L);
        only.setStudentId(5L);
        when(guardians.findById(1L)).thenReturn(Optional.of(only));
        when(guardians.findByStudentIdOrderByPrimaryDescIdAsc(5L)).thenReturn(List.of(only));
        assertThrows(ApiException.class, () -> service.deleteGuardian(1L));
        verify(guardians, never()).delete(any());
    }

    @Test
    void promotionLeavesGirlsAlreadyMovedAlone() {
        RebSession from = new RebSession(), to = new RebSession();
        from.setId(1L);
        to.setId(2L);
        from.setEndDate(LocalDate.of(2026, 3, 31));
        when(setup.session(1L)).thenReturn(from);
        when(setup.session(2L)).thenReturn(to);
        RebEnrollment moved = new RebEnrollment(), pending = new RebEnrollment();
        moved.setId(10L); moved.setStudentId(100L); moved.setSessionId(1L); moved.setSectionId(5L);
        pending.setId(11L); pending.setStudentId(101L); pending.setSessionId(1L); pending.setSectionId(5L);
        when(enrollments.findById(10L)).thenReturn(Optional.of(moved));
        when(enrollments.findById(11L)).thenReturn(Optional.of(pending));
        when(enrollments.findByStudentIdAndSessionId(100L, 2L)).thenReturn(Optional.of(new RebEnrollment()));
        when(enrollments.findByStudentIdAndSessionId(101L, 2L)).thenReturn(Optional.empty());
        RebStudent girl = new RebStudent();
        girl.setId(101L);
        when(students.findById(101L)).thenReturn(Optional.of(girl));
        RebSection target = new RebSection();
        target.setId(20L);
        target.setSessionId(2L);
        when(sections.findById(20L)).thenReturn(Optional.of(target));

        Map<String, Object> r = service.promote(Map.of("fromSessionId", 1, "toSessionId", 2, "rows", List.of(
                Map.of("enrollmentId", 10, "action", "PROMOTE", "targetSectionId", 20),
                Map.of("enrollmentId", 11, "action", "PROMOTE", "targetSectionId", 20))));
        assertEquals(1, r.get("promoted"));
        ArgumentCaptor<RebEnrollment> saved = ArgumentCaptor.forClass(RebEnrollment.class);
        verify(enrollments, times(2)).save(saved.capture());
        assertEquals("PROMOTED", saved.getAllValues().get(0).getStatus());
        assertEquals(101L, saved.getAllValues().get(1).getStudentId());
        assertEquals(2L, saved.getAllValues().get(1).getSessionId());
    }
}
