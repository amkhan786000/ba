package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Coaching dashboard: the office sees all centers and a row per center; a coordinator / teacher only their own. */
class CoachingDashboardServiceTest {

    private final CoachingStudentRepository students = mock(CoachingStudentRepository.class);
    private final CoachingExamRepository exams = mock(CoachingExamRepository.class);
    private final CoachingExamSubjectRepository examSubjects = mock(CoachingExamSubjectRepository.class);
    private final CoachingExamMarkRepository marks = mock(CoachingExamMarkRepository.class);
    private final CoachingAccess access = mock(CoachingAccess.class);
    private final CoachingService coaching = mock(CoachingService.class);
    private final CoachingDashboardService service = new CoachingDashboardService(students, mock(CoachingTeacherRepository.class), exams,
            examSubjects, marks, mock(CoachingExamPrizeRepository.class), access, coaching, mock(CoachingAttendanceRepository.class),
            new CoachingResults(exams, examSubjects, marks), mock(CoachingPipelineService.class));

    private static CoachingStudent student(long id, long center, int cls, String gender) {
        CoachingStudent s = new CoachingStudent();
        s.setId(id);
        s.setName("S" + id);
        s.setRccCenterId(center);
        s.setClassLevel(cls);
        s.setGender(gender);
        return s;
    }

    @BeforeEach
    void setUp() {
        when(coaching.centers()).thenReturn(Map.of());
        when(students.findAll()).thenReturn(List.of(student(1, 10, 7, "F"), student(2, 10, 8, "M"), student(3, 11, 7, "F")));
        CoachingExam e = new CoachingExam();
        e.setId(100L);
        e.setRccCenterId(10L);
        e.setClassLevel(7);
        e.setName("July");
        e.setExamDate(LocalDate.now());
        when(exams.findAllByOrderByExamDateDescIdDesc()).thenReturn(List.of(e));
        CoachingExamSubject sub = new CoachingExamSubject();
        sub.setExamId(100L);
        sub.setSubjectId(1L);
        sub.setMaxMarks(new BigDecimal("50"));
        when(examSubjects.findByExamIdIn(any())).thenReturn(List.of(sub));
        CoachingExamMark m = new CoachingExamMark();
        m.setExamId(100L);
        m.setStudentId(1L);
        m.setSubjectId(1L);
        m.setMarks(new BigDecimal("40"));
        when(marks.findByExamId(100L)).thenReturn(List.of(m));
        doCallRealMethod().when(access).requireVisible(any(), any());
    }

    @Test
    void theOfficeSeesEveryCenterAndARowEach() {
        when(access.names()).thenReturn(Map.of(10L, "Patna", 11L, "Gaya"));
        when(access.centerIds()).thenReturn(Set.of(10L, 11L));
        Map<String, Object> d = service.dashboard(null);
        @SuppressWarnings("unchecked") Map<String, Object> tiles = (Map<String, Object>) d.get("tiles");
        assertEquals(3, tiles.get("students"));
        assertEquals(2L, tiles.get("girls"));
        assertEquals(new BigDecimal("80.0"), tiles.get("average_percent_3_months"));
        assertEquals(2, ((List<?>) d.get("centers_summary")).size());
    }

    @Test
    void oneCenter() {
        when(access.names()).thenReturn(Map.of(10L, "Patna", 11L, "Gaya"));
        when(access.centerIds()).thenReturn(Set.of(10L, 11L));
        Map<String, Object> d = service.dashboard(11L);
        assertEquals(1, ((Map<?, ?>) d.get("tiles")).get("students"));
        assertNull(d.get("centers_summary"));
        assertEquals("Gaya", d.get("center_name"));
    }

    @Test
    void aCoordinatorSeesOnlyTheirCenter() {
        when(access.scoped()).thenReturn(true);
        when(access.names()).thenReturn(Map.of(10L, "Patna"));
        when(access.centerIds()).thenReturn(Set.of(10L));
        Map<String, Object> d = service.dashboard(null);
        assertEquals(2, ((Map<?, ?>) d.get("tiles")).get("students"));
        assertNull(d.get("centers_summary"));
        assertThrows(ApiException.class, () -> service.dashboard(11L));
    }
}
