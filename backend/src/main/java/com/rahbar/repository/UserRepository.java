package com.rahbar.repository;

import com.rahbar.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    /** By the user's code (users.user_id), e.g. bulk uploads that reference 'STU-1001'. */
    Optional<User> findByUserId(String userId);
    boolean existsByUserId(String userId);
    Optional<User> findByEmail(String email);
    Optional<User> findByPhone(String phone);
    List<User> findByRoleId(Integer roleId);
    List<User> findByRoleIdIn(List<Integer> roleIds);
    /** Recipient picker: users whose name, email or user code contains the text (lower-case LIKE pattern). */
    @Query("""
        select u from User u
        where lower(u.name) like :q or lower(u.email) like :q or lower(u.userId) like :q
        order by u.name asc
        """)
    List<User> searchByNameEmailOrCode(@Param("q") String q, org.springframework.data.domain.Pageable pageable);
    List<User> findAllByOrderByUserIdAsc();
    List<User> findByRoleIdAndChapterId(Integer roleId, Long chapterId);
    long countByRoleId(Integer roleId);
    long countByChapterId(Long chapterId);

    /** users.id of every sponsor (role 5): their details are only shown with the SPONSOR_DETAILS permission. */
    @Query("select u.id from User u where u.roleId = 5")
    List<Long> findSponsorIds();

    @Query("select u.userId from User u")
    List<String> findAllUserIds();

    /** Manage Users page: every filter optional (null = any); name / email are lower-case LIKE patterns. */
    @Query(value = """
        select u from User u
        where (:name is null or lower(u.name) like :name)
          and (:email is null or lower(u.email) like :email)
          and (:roleId is null or u.roleId = :roleId)
          and (:status is null or lower(u.status) = :status)
        """,
        countQuery = """
        select count(u) from User u
        where (:name is null or lower(u.name) like :name)
          and (:email is null or lower(u.email) like :email)
          and (:roleId is null or u.roleId = :roleId)
          and (:status is null or lower(u.status) = :status)
        """)
    org.springframework.data.domain.Page<User> searchUsers(@Param("name") String name, @Param("email") String email,
                                                           @Param("roleId") Integer roleId, @Param("status") String status,
                                                           org.springframework.data.domain.Pageable pageable);
    boolean existsByEmailAndIdNot(String email, Long id);
    boolean existsByPhoneAndIdNot(String phone, Long id);

    /** Number of users per role: [Integer roleId, Long count]. */
    @Query("select u.roleId, count(u) from User u group by u.roleId")
    List<Object[]> countPerRole();
    long countByRoleIdIn(List<Integer> roleIds);
    long countByRoleIdInAndStatus(List<Integer> roleIds, String status);

    // Sponsor bulk-upload merge lookups
    Optional<User> findFirstByEmailAndRoleIdIn(String email, List<Integer> roleIds);
    Optional<User> findFirstByPhoneAndRoleIdIn(String phone, List<Integer> roleIds);
    Optional<User> findFirstByNameAndChapterIdAndRoleIdIn(String name, Long chapterId, List<Integer> roleIds);

    /** Students (grantees) mapped to the given sponsor / convenor in grantor_grantees. */
    @Query("select u from User u where u.id in (select gg.granteeId from GrantorGrantee gg where gg.grantorId = :grantorId)")
    List<User> findGranteesOf(@Param("grantorId") Long grantorId);

    /** Users of a chapter who are a grantor in grantor_grantees for a grantee that has an application. */
    @Query("""
        select u from User u
        join GrantorGrantee gg on gg.grantorId = u.id
        join GranteeDetails gd on gd.userId = gg.granteeId
        where u.chapterId = :chapterId
        """)
    List<User> findSponsorsWithApplicantsInChapter(@Param("chapterId") Long chapterId);

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
        select new map(u.id as id, u.userId as user_id, u.name as student_name, u.email as student_email, u.phone as student_phone,
                       u.chapterId as chapter_id, ch.chapterName as chapter_name, s.name as sponsor_name, i.institutionName as institution_name,
                       c.courseName as course_name, sic.institutionId as institution_id, sic.courseId as course_id)
        from User u
        left join Chapter ch on ch.chapterId = u.chapterId
        left join GrantorGrantee gg on gg.granteeId = u.id
        left join User s on s.id = gg.grantorId
        left join StudentInstitutionCourse sic on sic.userId = u.id
        left join Institution i on i.institutionId = sic.institutionId
        left join Course c on c.courseId = sic.courseId
        where u.roleId = 6
        """)
    List<Map<String, Object>> findStudentOverview();

    /** Admin "Sponsors": active coordinators / convenors / sponsors with the number of students mapped to them. */
    @Query("""
        select new map(u.id as id, u.userId as user_id, u.name as name, u.email as email, u.phone as phone, u.status as status,
                       u.chapterId as chapter_id, ch.chapterName as chapter_name, r.roleName as role_name,
                       count(distinct gg.granteeId) as student_count)
        from User u
        left join Chapter ch on ch.chapterId = u.chapterId
        join Role r on r.roleId = u.roleId
        left join GrantorGrantee gg on gg.grantorId = u.id
        where u.roleId in (3, 4, 5) and u.status = 'active'
        group by u.id, u.userId, u.name, u.email, u.phone, u.status, u.chapterId, ch.chapterName, r.roleName
        order by u.name asc
        """)
    List<Map<String, Object>> findActiveSponsorships();

    /** Active students not mapped to the given sponsor, with their current sponsor (if any). */
    @Query("""
        select new map(u.id as id, u.userId as user_id, u.name as name, u.email as email, u.phone as phone,
                       u.chapterId as chapter_id, ch.chapterName as chapter_name, cs.name as current_sponsor_name, gg.grantorId as current_sponsor_id,
                       cs.userId as current_sponsor_code)
        from User u
        left join Chapter ch on ch.chapterId = u.chapterId
        left join GrantorGrantee gg on gg.granteeId = u.id
        left join User cs on cs.id = gg.grantorId
        where u.roleId = 6 and u.status = 'active' and (gg.grantorId is null or gg.grantorId <> :sponsorId)
        order by u.name asc
        """)
    List<Map<String, Object>> findStudentsAvailableFor(@Param("sponsorId") Long sponsorId);

    /** Admin student directory: filters are optional (null = no filter); search is a LIKE pattern. */
    @Query("""
        select new map(u.id as id, u.userId as user_id, u.name as name, u.email as email, u.phone as phone,
                       u.chapterId as chapter_id, ch.chapterName as chapter_name, u.status as status, max(s.id) as sponsor_id, max(s.userId) as sponsor_code, max(s.name) as sponsor_name,
                       max(i.institutionName) as institution_name, max(c.courseName) as course_name)
        from User u
        left join Chapter ch on ch.chapterId = u.chapterId
        left join GrantorGrantee gg on gg.granteeId = u.id
        left join User s on s.id = gg.grantorId
        left join StudentInstitutionCourse sic on sic.userId = u.id
        left join Institution i on i.institutionId = sic.institutionId
        left join Course c on c.courseId = sic.courseId
        where u.roleId = 6
          and (:institutionId is null or sic.institutionId = :institutionId)
          and (:courseId is null or sic.courseId = :courseId)
          and (:search is null or u.name like :search or u.email like :search or u.userId like :search
               or s.name like :search or s.userId like :search)
        group by u.id, u.userId, u.name, u.email, u.phone, u.chapterId, ch.chapterName, u.status
        order by u.id desc
        """)
    List<Map<String, Object>> searchStudentDirectory(@Param("institutionId") String institutionId,
                                                     @Param("courseId") Long courseId,
                                                     @Param("search") String search);
}
