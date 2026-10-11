package com.rahbar.repository;

import com.rahbar.entity.FinPublishedYear;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FinPublishedYearRepository extends JpaRepository<FinPublishedYear, Integer> {

    List<FinPublishedYear> findAllByOrderByYearDesc();
}
