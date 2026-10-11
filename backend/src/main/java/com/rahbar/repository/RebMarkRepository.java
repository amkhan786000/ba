package com.rahbar.repository;

import com.rahbar.entity.RebMark;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RebMarkRepository extends JpaRepository<RebMark, Long> {

    List<RebMark> findByPaperId(Long paperId);

    List<RebMark> findByPaperIdIn(Collection<Long> paperIds);
}
