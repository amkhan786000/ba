package com.rahbar.repository;

import com.rahbar.entity.OrgDocumentCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrgDocumentCategoryRepository extends JpaRepository<OrgDocumentCategory, Long> {

    List<OrgDocumentCategory> findAllByOrderByDisplayOrderAscNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
