package com.rahbar.repository;

import com.rahbar.entity.RebAdmissionEnquiry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RebAdmissionEnquiryRepository extends JpaRepository<RebAdmissionEnquiry, Long> {

    List<RebAdmissionEnquiry> findAllByOrderByIdDesc();
}
