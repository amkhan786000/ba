package com.rahbar.repository;

import com.rahbar.entity.StudentInstitutionCourse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;

public interface StudentInstitutionCourseRepository extends JpaRepository<StudentInstitutionCourse, Long> {
    List<StudentInstitutionCourse> findByUserIdIn(List<Long> userIds);

    List<StudentInstitutionCourse> findByCourseId(Long courseId);

    long countByCourseId(Long courseId);

    long countByInstitutionId(String institutionId);

    /** Course start date and length/fees for a student (assigned_at, number_of_semesters, fees_per_semester). */
    @Query("""
        select new map(s.assignedAt as assigned_at, c.numberOfSemesters as number_of_semesters,
                       c.feesPerSemester as fees_per_semester)
        from StudentInstitutionCourse s join Course c on c.courseId = s.courseId
        where s.userId = :userId
        """)
    List<Map<String, Object>> findCourseInfo(@Param("userId") Long userId);

    /** Full course assignment of a student, with course and institution names. */
    @Query("""
        select new map(s.assignedAt as assigned_at, s.institutionId as institution_id, s.courseId as course_id,
                       c.courseName as course_name, c.numberOfSemesters as number_of_semesters,
                       i.institutionName as institution_name)
        from StudentInstitutionCourse s
        join Course c on c.courseId = s.courseId
        join Institution i on i.institutionId = s.institutionId
        where s.userId = :userId
        """)
    List<Map<String, Object>> findCourseDetails(@Param("userId") Long userId);
}
