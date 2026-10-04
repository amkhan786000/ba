package com.rahbar.repository;

import com.rahbar.entity.GrantorGrantee;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface GrantorGranteeRepository extends JpaRepository<GrantorGrantee, Long> {
    Optional<GrantorGrantee> findByGranteeId(String granteeId);
    List<GrantorGrantee> findByGrantorId(String grantorId);
    List<GrantorGrantee> findByGrantorIdIn(List<String> grantorIds);
}
