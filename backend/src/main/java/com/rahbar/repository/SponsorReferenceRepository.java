package com.rahbar.repository;

import com.rahbar.entity.SponsorReference;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SponsorReferenceRepository extends JpaRepository<SponsorReference, String> {
    List<SponsorReference> findByUserId(String userId);
}
