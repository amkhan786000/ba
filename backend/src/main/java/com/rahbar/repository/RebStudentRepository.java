package com.rahbar.repository;

import com.rahbar.entity.RebStudent;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RebStudentRepository extends JpaRepository<RebStudent, Long> {

    java.util.Optional<RebStudent> findByAdmissionNo(String admissionNo);
    @org.springframework.data.jpa.repository.Query("select s.admissionNo from RebStudent s where s.admissionNo like :prefix%")
    java.util.List<String> findAdmissionNosStartingWith(@org.springframework.data.repository.query.Param("prefix") String prefix);
}
