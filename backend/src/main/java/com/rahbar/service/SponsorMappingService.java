package com.rahbar.service;

import com.rahbar.entity.GrantorGrantee;
import com.rahbar.entity.User;
import com.rahbar.repository.GrantorGranteeRepository;
import com.rahbar.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Objects;

/** Maps a student to a sponsor (grantor_grantees has one row per student) and tells both of them. */
@Service
public class SponsorMappingService {

    private final GrantorGranteeRepository grantorGranteeRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public SponsorMappingService(GrantorGranteeRepository grantorGranteeRepository, UserRepository userRepository,
                                 NotificationService notificationService) {
        this.grantorGranteeRepository = grantorGranteeRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    /**
     * Points the student's mapping at the sponsor, creating it when missing.
     *
     * @param status        status of a newly created mapping
     * @param replaceStatus whether an existing mapping's status is replaced too
     */
    public void map(Long granteeId, Long grantorId, String status, boolean replaceStatus) {
        GrantorGrantee gg = grantorGranteeRepository.findFirstByGranteeId(granteeId).orElse(null);
        Long previous = gg == null ? null : gg.getGrantorId();
        if (gg == null) {
            gg = new GrantorGrantee();
            gg.setGranteeId(granteeId);
            gg.setStatus(status);
        } else if (replaceStatus) {
            gg.setStatus(status);
        }
        gg.setGrantorId(grantorId);
        grantorGranteeRepository.save(gg);
        if (!Objects.equals(previous, grantorId)) notifyMapped(granteeId, grantorId);
    }

    /** Tells the sponsor and the student about a new mapping (nothing for the "unassigned" grantor, code 12). */
    public void notifyMapped(Long granteeId, Long grantorId) {
        if (grantorId == null || granteeId == null) return;
        User student = userRepository.findById(granteeId).orElse(null);
        User sponsor = userRepository.findById(grantorId).orElse(null);
        if (student == null || sponsor == null || ServiceSupport.UNASSIGNED_GRANTOR_CODE.equals(sponsor.getUserId())) return;
        notificationService.notify(grantorId, "New student mapped to you",
                student.getName() + " (" + student.getUserId() + ") is now one of your sponsored students.",
                NotificationService.MAPPING, Integer.valueOf(5).equals(sponsor.getRoleId()) ? "/sponsor/dashboard" : null, true);
        notificationService.notify(granteeId, "You have a sponsor",
                sponsor.getName() + " is now your sponsor.", NotificationService.MAPPING, "/student/dashboard", false);
    }
}
