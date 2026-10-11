package com.rahbar.repository;

import com.rahbar.entity.OrgDocumentRevision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrgDocumentRevisionRepository extends JpaRepository<OrgDocumentRevision, Long> {

    List<OrgDocumentRevision> findByDocumentIdOrderByIdDesc(Long documentId);
}
