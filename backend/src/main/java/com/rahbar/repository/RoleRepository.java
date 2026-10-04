package com.rahbar.repository;

import com.rahbar.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RoleRepository extends JpaRepository<Role, Integer> {
    List<Role> findAllByOrderByRoleIdAsc();
    boolean existsByRoleNameIgnoreCase(String roleName);
    boolean existsByRoleNameIgnoreCaseAndRoleIdNot(String roleName, Integer roleId);

    /** role_id is assigned as MAX + 1. */
    @Query("select coalesce(max(r.roleId), 0) + 1 from Role r")
    Integer nextId();
}
