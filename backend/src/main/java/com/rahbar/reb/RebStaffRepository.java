package com.rahbar.reb;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RebStaffRepository extends JpaRepository<RebStaff, Long> {

    java.util.List<RebStaff> findAllByOrderByNameAsc();
    java.util.Optional<RebStaff> findByUserId(Long userId);
}
