package com.rahbar.repository;

import com.rahbar.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEmail(String email);
    Optional<User> findByPhone(String phone);
    List<User> findByRoleId(Integer roleId);
    List<User> findByRoleIdIn(List<Integer> roleIds);

    @Query("select u from User u where u.roleId = :roleId and u.region = :region")
    List<User> findByRoleIdAndRegion(@Param("roleId") Integer roleId, @Param("region") String region);

    long countByRoleIdIn(List<Integer> roleIds);
}
