package com.rahbar.repository;

import com.rahbar.entity.GrantorGrantee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface GrantorGranteeRepository extends JpaRepository<GrantorGrantee, Long> {
    /** grantee_id is unique in grantor_grantees: a student has at most one sponsor. */
    Optional<GrantorGrantee> findFirstByGranteeId(String granteeId);
    List<GrantorGrantee> findByGrantorId(String grantorId);
    List<GrantorGrantee> findByGrantorIdIn(List<String> grantorIds);
    boolean existsByGranteeIdAndGrantorId(String granteeId, String grantorId);

    @Transactional
    @Modifying
    @Query("delete from GrantorGrantee gg where gg.granteeId = :granteeId")
    int deleteByGranteeId(@Param("granteeId") String granteeId);
}
