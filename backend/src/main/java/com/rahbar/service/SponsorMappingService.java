package com.rahbar.service;

import com.rahbar.entity.GrantorGrantee;
import com.rahbar.repository.GrantorGranteeRepository;
import org.springframework.stereotype.Service;

/** Maps a student to a sponsor (grantor_grantees has one row per student). */
@Service
public class SponsorMappingService {

    private final GrantorGranteeRepository grantorGranteeRepository;

    public SponsorMappingService(GrantorGranteeRepository grantorGranteeRepository) {
        this.grantorGranteeRepository = grantorGranteeRepository;
    }

    /**
     * Points the student's mapping at the sponsor, creating it when missing.
     *
     * @param status        status of a newly created mapping
     * @param replaceStatus whether an existing mapping's status is replaced too
     */
    public void map(String granteeId, String grantorId, String status, boolean replaceStatus) {
        GrantorGrantee gg = grantorGranteeRepository.findFirstByGranteeId(granteeId).orElse(null);
        if (gg == null) {
            gg = new GrantorGrantee();
            gg.setGranteeId(granteeId);
            gg.setStatus(status);
        } else if (replaceStatus) {
            gg.setStatus(status);
        }
        gg.setGrantorId(grantorId);
        grantorGranteeRepository.save(gg);
    }
}
