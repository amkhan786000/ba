package com.rahbar.repository;

import com.rahbar.entity.UserAdditionalRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserAdditionalRoleRepository extends JpaRepository<UserAdditionalRole, Long> {
    List<UserAdditionalRole> findByUserId(Long userId);

    List<UserAdditionalRole> findByRoleId(Integer roleId);
}
