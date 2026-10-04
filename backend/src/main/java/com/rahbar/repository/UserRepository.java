package com.rahbar.repository;

import com.rahbar.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEmail(String email);
    Optional<User> findByPhone(String phone);
    List<User> findByRoleId(Integer roleId);
    List<User> findByRoleIdIn(List<Integer> roleIds);
    List<User> findAllByOrderByUserIdAsc();
    @Query("select u from User u where u.roleId = :roleId and u.region = :region")
    List<User> findByRoleIdAndRegion(@Param("roleId") Integer roleId, @Param("region") String region);
    long countByRoleId(Integer roleId);
    long countByRoleIdIn(List<Integer> roleIds);
    long countByRoleIdInAndStatus(List<Integer> roleIds, String status);
    boolean existsByUserIdAndRoleIdIn(String userId, List<Integer> roleIds);

    // Sponsor bulk-upload merge lookups
    Optional<User> findFirstByEmailAndRoleIdIn(String email, List<Integer> roleIds);
    Optional<User> findFirstByPhoneAndRoleIdIn(String phone, List<Integer> roleIds);
    Optional<User> findFirstByNameAndRegionAndRoleIdIn(String name, String region, List<Integer> roleIds);

    /** Students (grantees) mapped to the given sponsor / convenor in grantor_grantees. */
    @Query("select u from User u where u.userId in (select gg.granteeId from GrantorGrantee gg where gg.grantorId = :grantorId)")
    List<User> findGranteesOf(@Param("grantorId") String grantorId);

    /** Users whose user_id is a grantor in grantor_grantees for a grantee that has an application, in a region. */
    @Query("""
        select u from User u
        join GrantorGrantee gg on gg.grantorId = u.userId
        join GranteeDetails gd on gd.userId = gg.granteeId
        where u.region = :region
        """)
    List<User> findSponsorsWithApplicantsInRegion(@Param("region") String region);

    // ---------------------------------------------------------------- dashboard year filters

    @Query("select distinct extract(year from u.createdAt) from User u where u.createdAt is not null")
    List<Integer> findCreatedYears();

    @Query("select distinct extract(year from u.createdAt) from User u where u.roleId in :roleIds and u.createdAt is not null")
    List<Integer> findCreatedYearsByRoleIdIn(@Param("roleIds") List<Integer> roleIds);

    @Query("select count(u) from User u where extract(year from u.createdAt) = :year")
    long countCreatedInYear(@Param("year") Integer year);

    @Query("select count(u) from User u where u.roleId = :roleId and extract(year from u.createdAt) = :year")
    long countByRoleIdCreatedInYear(@Param("roleId") Integer roleId, @Param("year") Integer year);

    @Query("select count(u) from User u where u.roleId in :roleIds and u.status = :status and extract(year from u.createdAt) = :year")
    long countByRoleIdInAndStatusCreatedInYear(@Param("roleIds") List<Integer> roleIds, @Param("status") String status,
                                               @Param("year") Integer year);

    @Query("select u from User u where u.roleId = :roleId and extract(year from u.createdAt) = :year")
    List<User> findByRoleIdCreatedInYear(@Param("roleId") Integer roleId, @Param("year") Integer year);

    // ---------------------------------------------------------------- admin screens

    /** Admin "Manage Students": every student with their sponsor, institution and course. */
    @Query("""
        select new map(u.userId as user_id, u.name as student_name, u.email as student_email, u.phone as student_phone,
                       u.region as region, s.name as sponsor_name, i.institutionName as institution_name,
                       c.courseName as course_name, sic.institutionId as institution_id, sic.courseId as course_id)
        from User u
        left join GrantorGrantee gg on gg.granteeId = u.userId
        left join User s on s.userId = gg.grantorId
        left join StudentInstitutionCourse sic on sic.userId = u.userId
        left join Institution i on i.institutionId = sic.institutionId
        left join Course c on c.courseId = sic.courseId
        where u.roleId = 6
        """)
    List<Map<String, Object>> findStudentOverview();

    /** Admin "Sponsors": active coordinators / convenors / sponsors with the number of students mapped to them. */
    @Query("""
        select new map(u.userId as user_id, u.name as name, u.email as email, u.phone as phone, u.status as status,
                       u.region as region, r.roleName as role_name, count(distinct gg.granteeId) as student_count)
        from User u
        join Role r on r.roleId = u.roleId
        left join GrantorGrantee gg on gg.grantorId = u.userId
        where u.roleId in (3, 4, 5) and u.status = 'active'
        group by u.userId, u.name, u.email, u.phone, u.status, u.region, r.roleName
        order by u.name asc
        """)
    List<Map<String, Object>> findActiveSponsorships();

    /** Active students not mapped to the given sponsor, with their current sponsor (if any). */
    @Query("""
        select new map(u.userId as user_id, u.name as name, u.email as email, u.phone as phone, u.region as region,
                       cs.name as current_sponsor_name, gg.grantorId as current_sponsor_id)
        from User u
        left join GrantorGrantee gg on gg.granteeId = u.userId
        left join User cs on cs.userId = gg.grantorId
        where u.roleId = 6 and u.status = 'active' and (gg.grantorId is null or gg.grantorId <> :sponsorId)
        order by u.name asc
        """)
    List<Map<String, Object>> findStudentsAvailableFor(@Param("sponsorId") String sponsorId);

    /** Admin student directory: filters are optional (null = no filter); search is a LIKE pattern. */
    @Query("""
        select new map(u.userId as user_id, u.name as name, u.email as email, u.phone as phone, u.region as region,
                       u.status as status, max(s.userId) as sponsor_id, max(s.name) as sponsor_name,
                       max(i.institutionName) as institution_name, max(c.courseName) as course_name)
        from User u
        left join GrantorGrantee gg on gg.granteeId = u.userId
        left join User s on s.userId = gg.grantorId
        left join StudentInstitutionCourse sic on sic.userId = u.userId
        left join Institution i on i.institutionId = sic.institutionId
        left join Course c on c.courseId = sic.courseId
        where u.roleId = 6
          and (:institutionId is null or sic.institutionId = :institutionId)
          and (:courseId is null or sic.courseId = :courseId)
          and (:search is null or u.name like :search or u.email like :search or u.userId like :search
               or s.name like :search or s.userId like :search)
        group by u.userId, u.name, u.email, u.phone, u.region, u.status
        order by u.userId desc
        """)
    List<Map<String, Object>> searchStudentDirectory(@Param("institutionId") String institutionId,
                                                     @Param("courseId") Long courseId,
                                                     @Param("search") String search);
}
