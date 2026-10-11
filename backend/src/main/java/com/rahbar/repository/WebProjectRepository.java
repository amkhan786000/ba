package com.rahbar.repository;

import com.rahbar.entity.WebProject;
import com.rahbar.repository.WebProjectRepository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;
import java.util.Optional;

public interface WebProjectRepository extends JpaRepository<WebProject, Long>, JpaSpecificationExecutor<WebProject> {

    List<WebProject> findByActiveTrueOrderByDisplayOrderAscNameAsc();

    Optional<WebProject> findBySlugAndActiveTrue(String slug);

    long countByActiveTrue();
}
