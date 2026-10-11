package com.rahbar.repository;

import com.rahbar.entity.RebHostelLeave;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RebHostelLeaveRepository extends JpaRepository<RebHostelLeave, Long> {

    List<RebHostelLeave> findAllByOrderByLeaveFromDesc();

    List<RebHostelLeave> findByStatus(String status);

    List<RebHostelLeave> findByStudentIdOrderByLeaveFromDesc(Long studentId);

    @Query("select l.passNo from RebHostelLeave l where l.passNo like :prefix%")
    List<String> findNumbersStartingWith(@Param("prefix") String prefix);
}
