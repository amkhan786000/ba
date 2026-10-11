package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
import com.rahbar.security.RebClassScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** REB results: Madrasa and School blocks, pass / fail, grades, ranks, missing marks and marks entry rules. */
class RebExamServiceTest {

    private final RebExamRepository exams = mock(RebExamRepository.class);
    private final RebExamPaperRepository papers = mock(RebExamPaperRepository.class);
    private final RebMarkRepository marks = mock(RebMarkRepository.class);
    private final RebExamResultRepository results = mock(RebExamResultRepository.class);
    private final RebGradeBandRepository bands = mock(RebGradeBandRepository.class);
    private final RebSubjectRepository subjects = mock(RebSubjectRepository.class);
    private final RebSectionRepository sections = mock(RebSectionRepository.class);
    private final RebEnrollmentRepository enrollments = mock(RebEnrollmentRepository.class);
    private final RebStudentRepository students = mock(RebStudentRepository.class);
    private final RebSetupService setup = mock(RebSetupService.class);
    private final RebAttendanceService attendance = mock(RebAttendanceService.class);
    private final RebClassScope scope = mock(RebClassScope.class);
    private final RebExamService service = new RebExamService(exams, papers, marks, results, bands, mock(RebClassSubjectRepository.class),
            subjects, mock(RebClassRepository.class), sections, enrollments, students, setup, mock(RebStudentService.class), attendance, scope);

    private final RebExam exam = new RebExam();

    private static RebSubject subject(long id, String name, String track) {
        RebSubject s = new RebSubject();
        s.setId(id);
        s.setName(name);
        s.setTrack(track);
        return s;
    }

    private static RebExamPaper paper(long id, long subjectId) {
        RebExamPaper p = new RebExamPaper();
        p.setId(id);
        p.setExamId(1L);
        p.setClassId(6L);
        p.setSubjectId(subjectId);
        p.setMaxMarks(100);
        p.setPassMarks(33);
        return p;
    }

    private static RebMark mark(long paperId, long studentId, String marks) {
        RebMark m = new RebMark();
        m.setPaperId(paperId);
        m.setStudentId(studentId);
        if (marks == null) m.setAbsent(true); else m.setMarks(new BigDecimal(marks));
        return m;
    }

    private static RebEnrollment seat(long studentId, String roll) {
        RebEnrollment e = new RebEnrollment();
        e.setStudentId(studentId);
        e.setSectionId(60L);
        e.setRollNo(roll);
        return e;
    }

    @BeforeEach
    void setUp() {
        exam.setId(1L);
        exam.setSessionId(1L);
        exam.setStatus("MARKS_ENTRY");
        RebSession session = new RebSession();
        session.setId(1L);
        session.setStartDate(LocalDate.of(2026, 1, 1));
        session.setEndDate(LocalDate.of(2026, 12, 31));
        when(setup.session(1L)).thenReturn(session);
        RebSection six = new RebSection();
        six.setId(60L);
        six.setClassId(6L);
        six.setSessionId(1L);
        when(sections.findById(60L)).thenReturn(Optional.of(six));
        when(sections.findBySessionId(1L)).thenReturn(List.of(six));
        when(sections.findBySessionIdAndClassId(1L, 6L)).thenReturn(List.of(six));
        when(subjects.findAll()).thenReturn(List.of(subject(1, "Quran", "MADRASA"), subject(2, "Maths", "SCHOOL")));
        when(papers.findByExamIdAndClassId(1L, 6L)).thenReturn(List.of(paper(11, 1), paper(12, 2)));
        when(papers.findByExamId(1L)).thenReturn(List.of(paper(11, 1), paper(12, 2)));
        when(bands.findBySessionIdOrderByMinPercentDesc(1L)).thenReturn(List.of());
        when(enrollments.findBySectionId(60L)).thenReturn(List.of(seat(1, "1"), seat(2, "2"), seat(3, "3"), seat(4, "4")));
        for (long i = 1; i <= 4; i++) {
            RebStudent s = new RebStudent();
            s.setId(i);
            s.setName("Girl " + i);
            when(students.findById(i)).thenReturn(Optional.of(s));
        }
        when(attendance.percents(any(), any(), any())).thenReturn(Map.of());
        when(scope.current()).thenReturn(new RebClassScope.Scope(true, Set.of(), Set.of()));
    }

    private Map<String, Object> row(List<Map<String, Object>> rows, long id) {
        return rows.stream().filter(r -> r.get("student_id").equals(id)).findFirst().orElseThrow();
    }

    @Test
    void blocksPercentGradeResultAndRank() {
        when(marks.findByPaperIdIn(any())).thenReturn(List.of(
                mark(11, 1, "90"), mark(12, 1, "80"),   // 85% A, pass
                mark(11, 2, "90"), mark(12, 2, "80"),   // 85% too: same rank
                mark(11, 3, "60"), mark(12, 3, "20"),   // 40%, but Maths below 33: fail
                mark(11, 4, "70")));                    // Maths missing
        var rows = service.tabulate(exam, 60L);
        var a = row(rows, 1);
        assertEquals(new BigDecimal("90"), a.get("madrasa_total"));
        assertEquals(new BigDecimal("80"), a.get("school_total"));
        assertEquals(new BigDecimal("85.00"), a.get("percent"));
        assertEquals("A", a.get("grade"));
        assertEquals("PASS", a.get("result"));
        assertEquals(1, a.get("rank"));
        assertEquals(1, row(rows, 2).get("rank"));          // a tie shares the rank
        assertEquals("FAIL", row(rows, 3).get("result"));
        assertEquals(3, row(rows, 3).get("rank"));
        assertEquals(1, row(rows, 4).get("missing"));
        assertNull(row(rows, 4).get("result"));             // not decided while marks are missing
    }

    @Test
    void absentInEveryPaperIsAbsentAndPublishingWaitsForMissingMarks() {
        when(marks.findByPaperIdIn(any())).thenReturn(List.of(mark(11, 1, null), mark(12, 1, null)));
        assertEquals("ABSENT", row(service.tabulate(exam, 60L), 1).get("result"));
        when(exams.findById(1L)).thenReturn(Optional.of(exam));
        ApiException e = assertThrows(ApiException.class, () -> service.setStatus(1L, Map.of("status", "PUBLISHED")));
        assertTrue(e.getMessage().contains("missing"));
    }

    @Test
    void marksAreCheckedAndOnlyEnteredWhileOpen() {
        when(papers.findById(12L)).thenReturn(Optional.of(paper(12, 2)));
        when(exams.findById(1L)).thenReturn(Optional.of(exam));
        assertThrows(ApiException.class, () -> service.saveMarks(12L, Map.of("marks", List.of(Map.of("studentId", 1, "marks", "101")))));
        assertThrows(ApiException.class, () -> service.saveMarks(12L, Map.of("marks", List.of(Map.of("studentId", 99, "marks", "50")))));
        assertEquals("1 marks saved.", service.saveMarks(12L, Map.of("marks", List.of(Map.of("studentId", 1, "marks", "55")))));
        exam.setStatus("PUBLISHED");
        assertThrows(ApiException.class, () -> service.saveMarks(12L, Map.of("marks", List.of(Map.of("studentId", 1, "marks", "56")))));
    }

    @Test
    void aTeacherOnlyReachesTheSubjectsSheTeaches() {
        when(scope.current()).thenReturn(new RebClassScope.Scope(false, Set.of(60L), Set.of("60:1")));
        when(exams.findById(1L)).thenReturn(Optional.of(exam));
        when(marks.findByPaperId(anyLong())).thenReturn(List.of());
        var mine = service.myPapers(1L);
        assertEquals(1, mine.size());
        assertEquals(11L, mine.get(0).get("paper_id"));
        when(papers.findById(12L)).thenReturn(Optional.of(paper(12, 2)));
        assertThrows(ApiException.class, () -> service.marksSheet(12L));
    }

    @Test
    void defaultGradesAndAttendancePercent() {
        assertEquals("A+", RebExamService.grade(RebExamService.DEFAULT_BANDS, new BigDecimal("90")).grade());
        assertEquals("D", RebExamService.grade(RebExamService.DEFAULT_BANDS, new BigDecimal("33")).grade());
        assertFalse(RebExamService.grade(RebExamService.DEFAULT_BANDS, new BigDecimal("32.99")).pass());
        assertEquals(new BigDecimal("75.0"), RebAttendanceService.percent(3, 4));
        assertNull(RebAttendanceService.percent(0, 0));
    }
}
