package com.rahbar.repository;

import com.rahbar.entity.GranteeDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface GranteeDetailsRepository extends JpaRepository<GranteeDetails, Long> {
    /** user_id isn't unique in the legacy table, so take the oldest application of a student. */
    Optional<GranteeDetails> findFirstByUserIdOrderByGranteeDetailIdAsc(String userId);
    List<GranteeDetails> findByUserId(String userId);
    List<GranteeDetails> findByStudentMobileOrFatherMobileOrMotherMobile(String m1, String m2, String m3);

    @Query("select distinct extract(year from g.createdAt) from GranteeDetails g where g.createdAt is not null")
    List<Integer> findCreatedYears();

    @Query("select count(g) from GranteeDetails g where extract(year from g.createdAt) = :year")
    long countCreatedInYear(@Param("year") Integer year);

    @Query("select g from GranteeDetails g where extract(year from g.createdAt) = :year")
    List<GranteeDetails> findCreatedInYear(@Param("year") Integer year);

    /** Applications with the applicant's account name (LEFT JOIN users): [GranteeDetails, String]. */
    @Query("select g, u.name from GranteeDetails g left join User u on u.userId = g.userId")
    List<Object[]> findAllWithApplicantName();

    /** Applications of students living in a region: [GranteeDetails, String applicantName]. */
    @Query("select g, u.name from GranteeDetails g join User u on u.userId = g.userId where u.region = :region")
    List<Object[]> findWithApplicantNameByRegion(@Param("region") String region);

    /** Every application joined with every status row it has had: [GranteeDetails, ApplicationStatus]. */
    @Query("select g, a from GranteeDetails g join ApplicationStatus a on a.granteeDetailId = g.granteeDetailId")
    List<Object[]> findAllWithStatusHistory();

    /** Applications report: each application with the sponsor its student is mapped to. */
    @Query("""
        select new map(g.granteeDetailId as grantee_detail_id, g.name as name, g.fatherName as father_name,
                       g.rccName as rcc_name, g.courseApplied as course_applied,
                       s.name as assigned_sponsor_name, s.userId as assigned_sponsor_id)
        from GranteeDetails g
        left join GrantorGrantee gg on gg.granteeId = g.userId
        left join User s on s.userId = gg.grantorId
        """)
    List<Map<String, Object>> findApplicationsReport();

    @Transactional
    @Modifying
    @Query("delete from GranteeDetails g where g.userId = :userId")
    int deleteByUserId(@Param("userId") String userId);
}
