package com.rahbar.repository;

import com.rahbar.entity.RebExamPaper;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RebExamPaperRepository extends JpaRepository<RebExamPaper, Long> {

    List<RebExamPaper> findByExamId(Long examId);

    List<RebExamPaper> findByExamIdAndClassId(Long examId, Long classId);
}
