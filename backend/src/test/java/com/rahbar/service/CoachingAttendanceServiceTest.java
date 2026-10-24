package com.rahbar.service;

import com.rahbar.entity.CoachingAttendance;
import com.rahbar.entity.CoachingStudent;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Coaching attendance: present / (present + absent), no future days, only the class's own students. */
class CoachingAttendanceServiceTest {

    private final CoachingAttendanceRepository attendance = mock(CoachingAttendanceRepository.class);
    private final CoachingStudentRepository students = mock(CoachingStudentRepository.class);
    private final CoachingAccess access = mock(CoachingAccess.class);
    private final CoachingService coaching = new CoachingService(students, mock(CoachingTeacherRepository.class), mock(CoachingSubjectRepository.class),
            mock(CoachingExamSubjectRepository.class), mock(CoachingExamMarkRepository.class), mock(UserRepository.class), mock(RoleRepository.class), access);
    private final CoachingAttendanceService service = new CoachingAttendanceService(attendance, students, access, coaching);

    private CoachingStudent student(long id, int cls) {
        CoachingStudent s = new CoachingStudent();
        s.setId(id);
        s.setName("S" + id);
        s.setRccCenterId(10L);
        s.setClassLevel(cls);
        when(students.findById(id)).thenReturn(Optional.of(s));
        return s;
    }

    private static Map<String, Object> body(String date, Object... entries) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < entries.length; i += 2) {
            Map<String, Object> e = new HashMap<>();
            e.put("studentId", entries[i]);
            e.put("status", entries[i + 1]);
            list.add(e);
        }
        return new HashMap<>(Map.of("centerId", 10, "classLevel", 9, "date", date, "entries", list));
    }

    @Test
    void leaveDoesNotCountAgainstAStudent() {
        assertEquals(new BigDecimal("75.0"), CoachingAttendanceService.percent(3, 1));
        assertNull(CoachingAttendanceService.percent(0, 0));
    }

    @Test
    void marksTheRegister() {
        when(access.requireCenter(10)).thenReturn(10L);
        when(access.centerIds()).thenReturn(Set.of(10L));
        doCallRealMethod().when(access).requireVisible(any(), any());
        student(1, 9);
        student(2, 9);
        Map<String, Object> out = service.save(body(LocalDate.now().toString(), 1, "p", 2, "A"));
        assertEquals(2, out.get("saved"));
        verify(attendance, times(2)).save(any(CoachingAttendance.class));
    }

    @Test
    void opensOnTheFirstClassThatHasStudents() {
        com.rahbar.entity.RccCenter c = new com.rahbar.entity.RccCenter();
        c.setRccCenterId(10L);
        c.setCenterName("Patna");
        when(access.centers()).thenReturn(List.of(c));
        CoachingStudent eight = student(1, 8);
        eight.setStatus("ACTIVE");
        when(students.findAll()).thenReturn(List.of(eight));
        when(students.findByRccCenterIdAndClassLevelAndStatusOrderByNameAsc(10L, 8, "ACTIVE")).thenReturn(List.of(eight));
        Map<String, Object> reg = service.register(null, null, null);
        assertEquals(8, reg.get("class_level"));
        assertEquals(1L, ((Map<?, ?>) reg.get("students_per_class")).get(8));
        assertEquals(1, ((List<?>) reg.get("rows")).size());
    }

    @Test
    void refusesFutureDaysOtherClassesAndUnknownMarks() {
        when(access.requireCenter(10)).thenReturn(10L);
        when(access.centerIds()).thenReturn(Set.of(10L));
        doCallRealMethod().when(access).requireVisible(any(), any());
        student(1, 9);
        student(3, 8);
        assertThrows(ApiException.class, () -> service.save(body(LocalDate.now().plusDays(1).toString(), 1, "P")));
        assertThrows(ApiException.class, () -> service.save(body(LocalDate.now().toString(), 3, "P")));
        assertThrows(ApiException.class, () -> service.save(body(LocalDate.now().toString(), 1, "X")));
    }
}
