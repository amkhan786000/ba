package com.rahbar.repository;

import com.rahbar.entity.Chapter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChapterRepository extends JpaRepository<Chapter, Long> {
    List<Chapter> findAllByOrderByChapterNameAsc();
    Optional<Chapter> findByChapterNameIgnoreCase(String chapterName);
}
