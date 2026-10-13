package com.rahbar.repository;

import com.rahbar.entity.ScholarshipApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ScholarshipApplicationRepository extends JpaRepository<ScholarshipApplication, Long> {

    /** Admin > Applications: by name / RCC (like patterns, lower case) and status, newest first. */
    @Query("""
        select a from ScholarshipApplication a
        where (:name is null or lower(a.name) like :name)
          and (:rcc is null or lower(a.rccName) like :rcc)
          and (:status is null or a.status = :status)
        """)
    Page<ScholarshipApplication> search(@Param("name") String name, @Param("rcc") String rcc, @Param("status") String status, Pageable pageable);

    List<ScholarshipApplication> findByStudentMobileOrFatherMobileOrMotherMobileOrderByIdDesc(String student, String father, String mother);

    List<ScholarshipApplication> findAllByOrderByIdDesc();

    List<ScholarshipApplication> findByStudentUserIdIn(java.util.Collection<Long> studentIds);

    @Query("select distinct extract(year from a.createdAt) from ScholarshipApplication a where a.createdAt is not null")
    List<Integer> findCreatedYears();
}
