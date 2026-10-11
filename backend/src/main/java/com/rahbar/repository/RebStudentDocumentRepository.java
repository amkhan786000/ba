package com.rahbar.repository;

import com.rahbar.entity.RebStudentDocument;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RebStudentDocumentRepository extends JpaRepository<RebStudentDocument, Long> {

    java.util.List<RebStudentDocument> findByStudentIdOrderByIdDesc(Long studentId);
    java.util.List<RebStudentDocument> findAllByStudentIdIn(java.util.Collection<Long> studentIds);
}
