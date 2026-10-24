package com.rahbar.service;

import com.rahbar.entity.CoachingStudent;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Coaching students and teachers: the admission form's checks and classes 7 to 10 only. */
class CoachingServiceTest {

    private final CoachingStudentRepository students = mock(CoachingStudentRepository.class);
    private final CoachingExamMarkRepository marks = mock(CoachingExamMarkRepository.class);
    private final CoachingAccess access = mock(CoachingAccess.class);
    private final CoachingService service = new CoachingService(students, mock(CoachingTeacherRepository.class), mock(CoachingSubjectRepository.class),
            mock(CoachingExamSubjectRepository.class), marks, mock(UserRepository.class), mock(RoleRepository.class), access);

    private static Map<String, Object> form(Object... kv) {
        Map<String, Object> m = new HashMap<>(Map.of("rccCenterId", 10, "classLevel", 8, "name", "Sana"));
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    @Test
    void onlyClassesSevenToTen() {
        assertEquals(7, CoachingService.classLevel("7"));
        assertThrows(ApiException.class, () -> CoachingService.classLevel(6));
        assertThrows(ApiException.class, () -> CoachingService.classLevel(11));
        assertEquals("7,9", CoachingService.classes(List.of("9", 7, "7")));
        assertThrows(ApiException.class, () -> CoachingService.classes("8,12"));
    }

    @Test
    void savesAStudentFromTheAdmissionForm() {
        when(access.requireCenter(10)).thenReturn(10L);
        when(students.save(any())).thenAnswer(i -> i.getArgument(0));
        Map<String, Object> row = service.saveStudent(null, form("guardianMobile", "+91 98765 43210", "aadhaarLast4", "1234",
                "gender", "f", "previousClassPercent", "86.5"));
        assertEquals("9876543210", row.get("guardian_mobile"));
        assertEquals("F", row.get("gender"));
        assertEquals("APPLIED", row.get("status")); // waits for the entrance test / selection
    }

    @Test
    void checksTheForm() {
        when(access.requireCenter(10)).thenReturn(10L);
        assertThrows(ApiException.class, () -> service.saveStudent(null, form("name", " ")));
        assertThrows(ApiException.class, () -> service.saveStudent(null, form("aadhaarLast4", "123456789012")));
        assertThrows(ApiException.class, () -> service.saveStudent(null, form("guardianMobile", "12345")));
        assertThrows(ApiException.class, () -> service.saveStudent(null, form("previousClassPercent", "120")));
        assertThrows(ApiException.class, () -> service.saveStudent(null, form("classLevel", 11)));
    }

    @Test
    void aStudentWithMarksIsMarkedLeftNotDeleted() {
        CoachingStudent s = new CoachingStudent();
        s.setId(5L);
        s.setRccCenterId(10L);
        when(students.findById(5L)).thenReturn(Optional.of(s));
        when(marks.existsByStudentId(5L)).thenReturn(true);
        assertThrows(ApiException.class, () -> service.deleteStudent(5L));
        verify(students, never()).delete(any());
    }

    @Test
    void aCenterScopedUserCantChangeTheSharedSubjects() {
        when(access.scoped()).thenReturn(true);
        assertThrows(ApiException.class, () -> service.saveSubject(null, Map.of("name", "Physics")));
    }

    @Test
    void theAdmissionFormsNewFields() {
        when(access.requireCenter(10)).thenReturn(10L);
        when(students.save(any())).thenAnswer(i -> i.getArgument(0));
        Map<String, Object> row = service.saveStudent(null, form("sessionFrom", "2025", "sessionTo", "2028", "fatherMobile", "8210617802",
                "schoolClass", "VII", "guardianName", "Md. Naushad Alam", "declarationAccepted", true, "testRollNo", "8001", "testAttendance", "present"));
        assertEquals("2025-28", row.get("session_label"));
        assertEquals("8210617802", row.get("contact_mobile"));
        assertEquals("PRESENT", row.get("test_attendance"));
        assertEquals(true, row.get("declaration_accepted"));
        assertThrows(ApiException.class, () -> service.saveStudent(null, form("sessionFrom", "2025", "sessionTo", "2024")));
        assertThrows(ApiException.class, () -> service.saveStudent(null, form("testAttendance", "late")));
    }

    @Test
    void selectingAdmitsTheStudent() {
        CoachingStudent s = new CoachingStudent();
        s.setId(5L);
        s.setName("Mahim");
        s.setRccCenterId(10L);
        s.setStatus("APPLIED");
        when(students.findById(5L)).thenReturn(Optional.of(s));
        when(access.centerIds()).thenReturn(Set.of(10L));
        doCallRealMethod().when(access).requireVisible(any(), any());
        service.admission(5L, new HashMap<>(Map.of("testAttendance", "PRESENT")));
        assertEquals("PRESENT", s.getTestAttendance());
        service.admission(5L, new HashMap<>(Map.of("decision", "SELECTED")));
        assertEquals("ACTIVE", s.getStatus());
        assertNotNull(s.getAdmissionDate());
        service.admission(5L, new HashMap<>(Map.of("decision", "NOT_SELECTED")));
        assertEquals("NOT_SELECTED", s.getStatus());
    }
}
