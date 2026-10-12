package com.rahbar.repository;

import com.rahbar.entity.StudentNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudentNoteRepository extends JpaRepository<StudentNote, Long> {
    List<StudentNote> findByStudentUserIdOrderByIdDesc(Long studentUserId);

    List<StudentNote> findBySponsorUserIdAndStatusOrderByReviewedAtDesc(Long sponsorUserId, String status);

    List<StudentNote> findAllByOrderByIdDesc();

    long countByStudentUserIdAndStatus(Long studentUserId, String status);
}
