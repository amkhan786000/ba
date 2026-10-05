package com.rahbar.repository;

import com.rahbar.entity.ApplicationDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplicationDocumentRepository extends JpaRepository<ApplicationDocument, Long> {
    List<ApplicationDocument> findByGranteeDetailIdOrderByDocumentIdDesc(Long granteeDetailId);
    long countByGranteeDetailId(Long granteeDetailId);
}
