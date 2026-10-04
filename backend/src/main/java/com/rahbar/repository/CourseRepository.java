package com.rahbar.repository;

import com.rahbar.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findByInstitutionId(String institutionId);
    Optional<Course> findByCourseNameAndInstitutionId(String courseName, String institutionId);
    boolean existsByCourseIdAndInstitutionId(Long courseId, String institutionId);
}
