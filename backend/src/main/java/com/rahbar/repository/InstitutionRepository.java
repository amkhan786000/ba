package com.rahbar.repository;

import com.rahbar.entity.Institution;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface InstitutionRepository extends JpaRepository<Institution, String> {
    Optional<Institution> findByInstitutionName(String name);
}
