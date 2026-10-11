package com.rahbar.repository;

import com.rahbar.entity.WebMember;
import com.rahbar.repository.WebMemberRepository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;
import org.springframework.data.jpa.repository.Query;

public interface WebMemberRepository extends JpaRepository<WebMember, Long>, JpaSpecificationExecutor<WebMember> {

    boolean existsByEmailIgnoreCase(String email);

    long countByApprovedTrue();

    @Query("select distinct m.currentCountry from WebMember m where m.approved = true order by m.currentCountry")
    List<String> approvedCountries();
}
