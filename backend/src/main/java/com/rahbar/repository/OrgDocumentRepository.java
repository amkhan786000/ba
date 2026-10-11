package com.rahbar.repository;

import com.rahbar.entity.OrgDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrgDocumentRepository extends JpaRepository<OrgDocument, Long> {

    List<OrgDocument> findAllByOrderByTitleAsc();

    boolean existsByCategoryId(Long categoryId);
}
