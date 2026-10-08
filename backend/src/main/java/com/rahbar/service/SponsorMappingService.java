package com.rahbar.service;

import com.rahbar.entity.GrantorGrantee;
import com.rahbar.entity.User;
import com.rahbar.repository.GrantorGranteeRepository;
import com.rahbar.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Every change to which sponsor a student is mapped to goes through here (grantor_grantees has one row per student).
 * On a change the new sponsor and the student get an email (and a notification), the previous sponsor is told the
 * student moved on, and the student's payment installments are rebuilt (unpaid ones move to the new sponsor).
 */
@Service
public class SponsorMappingService {

    private final GrantorGranteeRepository grantorGranteeRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final PaymentInstallmentService installmentService;

    public SponsorMappingService(GrantorGranteeRepository grantorGranteeRepository, UserRepository userRepository,
                                 NotificationService notificationService, PaymentInstallmentService installmentService) {
        this.grantorGranteeRepository = grantorGranteeRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.installmentService = installmentService;
    }

    /**
     * Points the student's mapping at the sponsor, creating it when missing.
     *
     * @param status        status of a newly created mapping
     * @param replaceStatus whether an existing mapping's status is replaced too
     */
    @Transactional
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
        changed(granteeId, previous, grantorId);
    }

    /** Moves the student's existing mapping to the sponsor (nothing happens when the student has no mapping). */
    @Transactional
    public void remap(Long granteeId, Long grantorId) {
        grantorGranteeRepository.findFirstByGranteeId(granteeId).ifPresent(gg -> {
            Long previous = gg.getGrantorId();
            gg.setGrantorId(grantorId);
            grantorGranteeRepository.save(gg);
            changed(granteeId, previous, grantorId);
        });
    }

    /** Removes the student's mapping. */
    @Transactional
    public void unmap(Long granteeId) {
        Long previous = grantorGranteeRepository.findFirstByGranteeId(granteeId).map(GrantorGrantee::getGrantorId).orElse(null);
        grantorGranteeRepository.deleteByGranteeId(granteeId);
        changed(granteeId, previous, null);
    }

    /** A deactivated sponsor's students go to the "unassigned" grantor (code 12); newStatus null keeps their status. */
    @Transactional
    public void moveAllTo(Long fromSponsorId, Long toGrantorId, String newStatus) {
        List<GrantorGrantee> mappings = grantorGranteeRepository.findByGrantorId(fromSponsorId);
        for (GrantorGrantee gg : mappings) {
            gg.setGrantorId(toGrantorId);
            if (newStatus != null) gg.setStatus(newStatus);
        }
        grantorGranteeRepository.saveAll(mappings);
        for (GrantorGrantee gg : mappings) changed(gg.getGranteeId(), fromSponsorId, toGrantorId);
    }

    private void changed(Long granteeId, Long previous, Long current) {
        if (!Objects.equals(previous, current)) {
            notifyMapped(granteeId, current);
            notifyUnmapped(granteeId, previous, current);
        }
        installmentService.sync(granteeId);
    }

    private boolean realSponsor(User sponsor) {
        return sponsor != null && !ServiceSupport.UNASSIGNED_GRANTOR_CODE.equals(sponsor.getUserId());
    }

    /** Emails the sponsor and the student about a new mapping (nothing for the "unassigned" grantor, code 12). */
    public void notifyMapped(Long granteeId, Long grantorId) {
        if (grantorId == null || granteeId == null) return;
        User student = userRepository.findById(granteeId).orElse(null);
        User sponsor = userRepository.findById(grantorId).orElse(null);
        if (student == null || !realSponsor(sponsor)) return;
        notificationService.notify(grantorId, "New student mapped to you",
                student.getName() + " (" + student.getUserId() + ") is now one of your sponsored students."
                        + " You can see their payment schedule on your Payments page.",
                NotificationService.MAPPING, Integer.valueOf(5).equals(sponsor.getRoleId()) ? "/sponsor/dashboard" : null, true);
        notificationService.notify(granteeId, "You have a sponsor",
                sponsor.getName() + " is now your sponsor. You can see your payment schedule on your Payments page.",
                NotificationService.MAPPING, "/student/dashboard", true);
    }

    /** Emails the previous sponsor that the student is no longer mapped to them. */
    private void notifyUnmapped(Long granteeId, Long previousId, Long currentId) {
        if (previousId == null) return;
        User student = userRepository.findById(granteeId).orElse(null);
        User previous = userRepository.findById(previousId).orElse(null);
        if (student == null || !realSponsor(previous)) return;
        boolean moved = currentId != null && userRepository.findById(currentId).filter(this::realSponsor).isPresent();
        notificationService.notify(previousId, "Student no longer mapped to you",
                student.getName() + " (" + student.getUserId() + ") is no longer one of your sponsored students"
                        + (moved ? "; another sponsor has taken over." : ".")
                        + " Installments you already paid stay in your payment history.",
                NotificationService.MAPPING, Integer.valueOf(5).equals(previous.getRoleId()) ? "/sponsor/dashboard" : null, true);
        if (!moved) {
            notificationService.notify(granteeId, "Sponsor changed",
                    "You are currently not mapped to a sponsor. The office will let you know when a new sponsor is assigned.",
                    NotificationService.MAPPING, "/student/dashboard", true);
        }
    }
}
