package com.rahbar.repository;

import com.rahbar.entity.StudentInstitutionCourse;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface StudentInstitutionCourseRepository extends JpaRepository<StudentInstitutionCourse, String> {
    List<StudentInstitutionCourse> findByUserIdIn(List<String> userIds);
}
