package com.rahbar.repository;

import com.rahbar.entity.GranteeDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface GranteeDetailsRepository extends JpaRepository<GranteeDetails, Long> {
    Optional<GranteeDetails> findByUserId(String userId);
    List<GranteeDetails> findByStudentMobileOrFatherMobileOrMotherMobile(String m1, String m2, String m3);
}
