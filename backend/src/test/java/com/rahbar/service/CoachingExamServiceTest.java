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

/** Coaching exams: prizes from the totals (ties share), marks within the maximum, and only the user's centers. */
class CoachingExamServiceTest {

    private final CoachingExamRepository exams = mock(CoachingExamRepository.class);
    private final CoachingExamSubjectRepository examSubjects = mock(CoachingExamSubjectRepository.class);
    private final CoachingExamMarkRepository marks = mock(CoachingExamMarkRepository.class);
    private final CoachingExamPrizeRepository prizes = mock(CoachingExamPrizeRepository.class);
    private final CoachingSubjectRepository subjects = mock(CoachingSubjectRepository.class);
    private final CoachingStudentRepository students = mock(CoachingStudentRepository.class);
    private final CoachingAccess access = mock(CoachingAccess.class);
    private final CoachingService coaching = new CoachingService(students, mock(CoachingTeacherRepository.class), subjects,
            examSubjects, marks, mock(UserRepository.class), mock(RoleRepository.class), access);
    private final CoachingExamService service = new CoachingExamService(exams, examSubjects, marks, prizes, subjects, students, access, coaching);

    private final CoachingExam exam = new CoachingExam();

    @BeforeEach
    void setUp() {
        exam.setId(1L);
        exam.setRccCenterId(10L);
        exam.setClassLevel(8);
        exam.setName("July test");
        exam.setExamDate(LocalDate.of(2026, 7, 31));
        when(exams.findById(1L)).thenReturn(Optional.of(exam));
        when(access.centerIds()).thenReturn(Set.of(10L));
        doCallRealMethod().when(access).requireVisible(any(), any());
        CoachingExamSubject maths = new CoachingExamSubject();
        maths.setExamId(1L);
        maths.setSubjectId(100L);
        maths.setMaxMarks(new BigDecimal("50"));
        when(examSubjects.findByExamIdOrderByIdAsc(1L)).thenReturn(List.of(maths));
    }

    private static Map<Long, BigDecimal> totals(Object... kv) {
        Map<Long, BigDecimal> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(((Number) kv[i]).longValue(), new BigDecimal(String.valueOf(kv[i + 1])));
        return m;
    }

    @Test
    void topThreeTotalsWinAndEqualTotalsShareAPosition() {
        assertEquals(Map.of(1L, 1, 2L, 2, 3L, 3), CoachingExamService.positions(totals(1, 95, 2, 90, 3, 88, 4, 80)));
        assertEquals(Map.of(1L, 1, 2L, 1, 3L, 2, 4L, 3), CoachingExamService.positions(totals(1, 95, 2, "95.0", 3, 90, 4, 88, 5, 80)));
        assertEquals(Map.of(1L, 1), CoachingExamService.positions(totals(1, 40)));
    }

    private CoachingStudent student(long id, long center, int cls) {
        CoachingStudent s = new CoachingStudent();
        s.setId(id);
        s.setName("Student " + id);
        s.setRccCenterId(center);
        s.setClassLevel(cls);
        when(students.findById(id)).thenReturn(Optional.of(s));
        return s;
    }

    private static Map<String, Object> mark(long student, long subject, Object marks) {
        Map<String, Object> m = new HashMap<>();
        m.put("studentId", student);
        m.put("subjectId", subject);
        m.put("marks", marks);
        return m;
    }

    @Test
    void marksStayWithinTheMaximumAndTheExamsClass() {
        student(5, 10, 8);
        student(6, 10, 9);
        assertThrows(ApiException.class, () -> service.saveMarks(1L, Map.of("marks", List.of(mark(5, 100, 51)))));
        assertThrows(ApiException.class, () -> service.saveMarks(1L, Map.of("marks", List.of(mark(6, 100, 40)))));
        assertThrows(ApiException.class, () -> service.saveMarks(1L, Map.of("marks", List.of(mark(5, 999, 40)))));

        Map<String, Object> ok = service.saveMarks(1L, Map.of("marks", List.of(mark(5, 100, "42.5"))));
        assertEquals(1, ok.get("changed"));
        verify(marks).save(argThat(m -> m.getMarks().compareTo(new BigDecimal("42.5")) == 0 && m.getStudentId() == 5L));
    }

    @Test
    void changedMarksClearThePrizes() {
        student(5, 10, 8);
        exam.setPrizesGeneratedAt(java.time.LocalDateTime.now());
        Map<String, Object> out = service.saveMarks(1L, Map.of("marks", List.of(mark(5, 100, 30))));
        verify(prizes).deleteByExamId(1L);
        assertNull(exam.getPrizesGeneratedAt());
        assertTrue(String.valueOf(out.get("message")).contains("generate them again"));
    }

    @Test
    void prizesNeedMarks() {
        when(marks.findByExamId(1L)).thenReturn(List.of());
        assertThrows(ApiException.class, () -> service.generatePrizes(1L));
    }

    @Test
    void anotherCentersExamIsNotFound() {
        when(access.centerIds()).thenReturn(Set.of(11L));
        ApiException e = assertThrows(ApiException.class, () -> service.sheet(1L));
        assertTrue(e.getMessage().contains("not found"));
    }
}
