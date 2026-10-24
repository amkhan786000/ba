package com.rahbar.repository;

import com.rahbar.entity.Employer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployerRepository extends JpaRepository<Employer, Long> {
    List<Employer> findAllByOrderByNameAsc();
}
