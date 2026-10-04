package com.rahbar.repository;

import com.rahbar.entity.StudentProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StudentProgressRepository extends JpaRepository<StudentProgress, Long> {
    List<StudentProgress> findByGranteeIdOrderByCreatedAtDesc(String granteeId);
    List<StudentProgress> findByGranteeIdInOrderByCreatedAtDesc(List<String> granteeIds);

    /** progress_id is assigned as MAX + 1 (the legacy table isn't auto-increment everywhere). */
    @Query("select coalesce(max(p.progressId), 0) + 1 from StudentProgress p")
    Long nextId();

    /** Progress rows of students living in a region, each with the student's name: [StudentProgress, String]. */
    @Query("""
        select sp, u.name from StudentProgress sp
        join User u on u.userId = sp.granteeId
        where u.region = :region
        order by sp.createdAt desc
        """)
    List<Object[]> findWithGranteeNameByRegion(@Param("region") String region);
}
