package com.rahbar.service;

import com.rahbar.util.UserRoles;
import com.rahbar.entity.GrantorGrantee;
import com.rahbar.entity.User;
import com.rahbar.repository.GrantorGranteeRepository;
import com.rahbar.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Every change to which sponsor a student is mapped to goes through here (grantor_grantees has one row per student).
 * On a change the new sponsor and the student get an email (and a notification), the previous sponsor is told the
 * student moved on, the student takes the new sponsor's chapter (when the sponsor has one), and the student's payment
 * installments are rebuilt (unpaid ones move to the new sponsor).
 */
@Service
public class SponsorMappingService {

    private final GrantorGranteeRepository grantorGranteeRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final PaymentInstallmentService installmentService;
    private final com.rahbar.repository.SponsorMappingHistoryRepository historyRepository;

    public SponsorMappingService(GrantorGranteeRepository grantorGranteeRepository, UserRepository userRepository,
                                 NotificationService notificationService, PaymentInstallmentService installmentService,
                                 com.rahbar.repository.SponsorMappingHistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
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
        map(granteeId, grantorId, status, replaceStatus, null);
    }

    /** Like {@link #map(Long, Long, String, boolean)}; endReason: why the previous sponsorship ended (kept in the history). */
    @Transactional
    public void map(Long granteeId, Long grantorId, String status, boolean replaceStatus, String endReason) {
        map(granteeId, grantorId, status, replaceStatus, endReason, null, false);
    }

    /**
     * Like {@link #map(Long, Long, String, boolean, String)}, also saying whether the sponsorship is on behalf of an
     * anonymous donor ({@code onBehalf}; null = the sponsor's own) when {@code setOnBehalf}. Without it a mapping to
     * the same sponsor keeps what it was, and a new sponsor starts as their own sponsorship.
     */
    @Transactional
    public void map(Long granteeId, Long grantorId, String status, boolean replaceStatus, String endReason,
                    com.rahbar.dto.OnBehalf onBehalf, boolean setOnBehalf) {
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
        if (setOnBehalf) setOnBehalf(gg, onBehalf);
        else if (!Objects.equals(previous, grantorId)) setOnBehalf(gg, null);
        grantorGranteeRepository.save(gg);
        changed(granteeId, previous, grantorId, endReason);
        if (Objects.equals(previous, grantorId) && setOnBehalf) {
            // same sponsor, only on-behalf changed: the open period says so too
            for (com.rahbar.entity.SponsorMappingHistory h : historyRepository.findByGranteeIdAndEndedAtIsNull(granteeId)) {
                copyOnBehalf(gg, h);
                historyRepository.save(h);
            }
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.rahbar.repository.ChapterRepository chapterRepository;

    /** chapter_id -> chapter name, for showing donors' chapters. */
    public Map<Long, String> chapterNames() {
        Map<Long, String> names = new HashMap<>();
        if (chapterRepository == null) return names; // not wired (unit tests)
        chapterRepository.findAll().forEach(c -> names.put(c.getChapterId(), c.getChapterName()));
        return names;
    }

    /**
     * What a screen shows about a sponsorship on behalf of an anonymous sponsor: on_behalf, donor_chapter_id,
     * donor_chapter, donor_label and on_behalf_text ("on behalf of Donor A, an anonymous sponsor from Jeddah Chapter").
     * Who the anonymous sponsor is never shows, except to the Super Admin and the Application Administrator.
     */
    public static Map<String, Object> onBehalfFields(GrantorGrantee gg, Map<Long, String> chapterNames) {
        return onBehalfFields(gg, chapterNames, null);
    }

    /**
     * As {@link #onBehalfFields(GrantorGrantee, Map)}; users looks up the anonymous sponsor's account, so the Super
     * Admin and the Application Administrator also get donor_user_id, donor_name and donor_code (and see the name in
     * on_behalf_text).
     */
    public static Map<String, Object> onBehalfFields(GrantorGrantee gg, Map<Long, String> chapterNames, java.util.function.Function<Long, User> users) {
        Map<String, Object> m = new LinkedHashMap<>();
        boolean on = gg != null && Boolean.TRUE.equals(gg.getOnBehalf());
        m.put("on_behalf", on);
        m.put("donor_chapter_id", on ? gg.getDonorChapterId() : null);
        m.put("donor_chapter", on ? chapterNames.get(gg.getDonorChapterId()) : null);
        m.put("donor_label", on ? gg.getDonorLabel() : null);
        String text = on ? onBehalfText(gg.getDonorLabel(), chapterNames.get(gg.getDonorChapterId())) : null;
        User donor = on && users != null && gg.getDonorUserId() != null && seesAnonymousSponsors() ? users.apply(gg.getDonorUserId()) : null;
        if (donor != null) {
            m.put("donor_user_id", donor.getId());
            m.put("donor_name", donor.getName());
            m.put("donor_code", donor.getUserId());
            text = adminText(donor);
        }
        m.put("on_behalf_text", text);
        return m;
    }

    /** What the Super Admin and the Application Administrator see: "on behalf of Asha Khan (1042), anonymous". */
    public static String adminText(User donor) {
        return "on behalf of " + donor.getName() + " (" + donor.getUserId() + "), anonymous to everyone else";
    }

    /** Only the Super Admin and the Application Administrator see who an anonymous sponsor is. */
    public static boolean seesAnonymousSponsors() {
        try {
            return com.rahbar.security.Access.isAdmin();
        } catch (RuntimeException e) {
            return false; // nobody signed in (e.g. a scheduled job)
        }
    }

    /**
     * "on behalf of an anonymous sponsor from Jeddah Chapter" (with the label: "on behalf of Donor A, an anonymous
     * sponsor from Jeddah Chapter"); never a name.
     */
    public static String onBehalfText(String label, String chapter) {
        String from = "an anonymous sponsor" + (chapter == null ? "" : " from " + chapter);
        return "on behalf of " + (label == null || label.isBlank() ? from : label + ", " + from);
    }

    private static void setOnBehalf(GrantorGrantee gg, com.rahbar.dto.OnBehalf onBehalf) {
        boolean was = Boolean.TRUE.equals(gg.getOnBehalf());
        boolean on = onBehalf != null && (onBehalf.donorChapterId() != null || onBehalf.donorUserId() != null);
        gg.setOnBehalf(on);
        gg.setDonorChapterId(on ? onBehalf.donorChapterId() : null);
        String label = on && onBehalf.label() != null ? onBehalf.label().strip() : null;
        gg.setDonorLabel(label == null || label.isEmpty() ? null : label.length() > 80 ? label.substring(0, 80) : label);
        // the anonymous sponsor's account: as given (admins), else the one already linked stays
        gg.setDonorUserId(!on ? null : onBehalf.setsDonor() ? onBehalf.donorUserId() : was ? gg.getDonorUserId() : null);
    }

    private static void copyOnBehalf(GrantorGrantee gg, com.rahbar.entity.SponsorMappingHistory h) {
        h.setOnBehalf(Boolean.TRUE.equals(gg.getOnBehalf()));
        h.setDonorChapterId(gg.getDonorChapterId());
        h.setDonorLabel(gg.getDonorLabel());
        h.setDonorUserId(gg.getDonorUserId());
    }

    /**
     * Super Admin / Application Administrator: links (or, with null, unlinks) the anonymous sponsor's own account to a
     * student's sponsorship on behalf of an anonymous sponsor, e.g. one made before anonymous sponsors had accounts.
     * Their chapter becomes the donor's chapter (whose Chapter Dashboard lists the student) when they have one.
     */
    @Transactional
    public void linkAnonymousSponsor(Long granteeId, Long donorUserId) {
        if (!seesAnonymousSponsors()) throw com.rahbar.security.Access.forbidden("Only the Super Admin or the Application Administrator can do this.");
        GrantorGrantee gg = grantorGranteeRepository.findFirstByGranteeId(granteeId)
                .filter(g -> Boolean.TRUE.equals(g.getOnBehalf()))
                .orElseThrow(() -> new com.rahbar.exception.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                        "This student isn't sponsored on behalf of an anonymous sponsor."));
        if (donorUserId != null) {
            if (donorUserId.equals(gg.getGrantorId())) {
                throw new com.rahbar.exception.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                        "The anonymous sponsor can't be the sponsor who mapped the student.");
            }
            userRepository.findById(donorUserId).map(User::getChapterId).ifPresent(gg::setDonorChapterId);
        }
        gg.setDonorUserId(donorUserId);
        grantorGranteeRepository.save(gg);
        for (com.rahbar.entity.SponsorMappingHistory h : historyRepository.findByGranteeIdAndEndedAtIsNull(granteeId)) {
            copyOnBehalf(gg, h);
            historyRepository.save(h);
        }
    }

    /** Moves the student's existing mapping to the sponsor (nothing happens when the student has no mapping). */
    @Transactional
    public void remap(Long granteeId, Long grantorId) {
        grantorGranteeRepository.findFirstByGranteeId(granteeId).ifPresent(gg -> {
            Long previous = gg.getGrantorId();
            gg.setGrantorId(grantorId);
            if (!Objects.equals(previous, grantorId)) setOnBehalf(gg, null);
            grantorGranteeRepository.save(gg);
            changed(granteeId, previous, grantorId, null);
        });
    }

    /** Removes the student's mapping. */
    @Transactional
    public void unmap(Long granteeId) {
        unmap(granteeId, null);
    }

    /** Removes the student's mapping; endReason: why (kept in the history). */
    @Transactional
    public void unmap(Long granteeId, String endReason) {
        Long previous = grantorGranteeRepository.findFirstByGranteeId(granteeId).map(GrantorGrantee::getGrantorId).orElse(null);
        grantorGranteeRepository.deleteByGranteeId(granteeId);
        changed(granteeId, previous, null, endReason);
    }

    /** A deactivated sponsor's students go to the "unassigned" grantor (code 12); newStatus null keeps their status. */
    @Transactional
    public void moveAllTo(Long fromSponsorId, Long toGrantorId, String newStatus) {
        List<GrantorGrantee> mappings = grantorGranteeRepository.findByGrantorId(fromSponsorId);
        for (GrantorGrantee gg : mappings) {
            gg.setGrantorId(toGrantorId);
            setOnBehalf(gg, null);
            if (newStatus != null) gg.setStatus(newStatus);
        }
        grantorGranteeRepository.saveAll(mappings);
        for (GrantorGrantee gg : mappings) changed(gg.getGranteeId(), fromSponsorId, toGrantorId, "Sponsor deactivated");
    }

    private void changed(Long granteeId, Long previous, Long current, String endReason) {
        if (!Objects.equals(previous, current)) {
            takeSponsorsChapter(granteeId, current);
            recordHistory(granteeId, current, endReason);
            notifyMapped(granteeId, current);
            notifyUnmapped(granteeId, previous, current);
        }
        installmentService.sync(granteeId);
    }

    /**
     * A student mapped to a sponsor belongs to the sponsor's chapter. A sponsor without a chapter (or the
     * "unassigned" grantor, or no sponsor) leaves the student's chapter as it is; the sponsor's chapter never changes.
     */
    private void takeSponsorsChapter(Long granteeId, Long sponsorId) {
        if (sponsorId == null || sponsorId.equals(ServiceSupport.unassignedGrantorId(userRepository))) return;
        // on behalf of an anonymous donor: the student keeps their own chapter
        if (grantorGranteeRepository.findFirstByGranteeId(granteeId).map(g -> Boolean.TRUE.equals(g.getOnBehalf())).orElse(false)) return;
        Long chapterId = userRepository.findById(sponsorId).map(User::getChapterId).orElse(null);
        if (chapterId == null) return;
        userRepository.findById(granteeId).ifPresent(student -> {
            if (!chapterId.equals(student.getChapterId())) {
                student.setChapterId(chapterId);
                userRepository.save(student);
            }
        });
    }

    /** Closes the student's open mapping period and opens one with the new sponsor (none for "unassigned" / unmap). */
    private void recordHistory(Long granteeId, Long current, String endReason) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        String reason = endReason == null || endReason.isBlank() ? null
                : endReason.strip().length() > 300 ? endReason.strip().substring(0, 300) : endReason.strip();
        for (com.rahbar.entity.SponsorMappingHistory h : historyRepository.findByGranteeIdAndEndedAtIsNull(granteeId)) {
            h.setEndedAt(now);
            h.setEndReason(reason);
            historyRepository.save(h);
        }
        if (current != null && realSponsor(userRepository.findById(current).orElse(null))) {
            com.rahbar.entity.SponsorMappingHistory h = new com.rahbar.entity.SponsorMappingHistory();
            h.setGranteeId(granteeId);
            h.setGrantorId(current);
            h.setStartedAt(now);
            grantorGranteeRepository.findFirstByGranteeId(granteeId).ifPresent(g -> copyOnBehalf(g, h));
            historyRepository.save(h);
        }
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
                student.getName() + " (" + ServiceSupport.refOf(student) + ") is now one of your sponsored students."
                        + " You can see their payment schedule on your Payments page.",
                NotificationService.MAPPING, UserRoles.has(sponsor, 5, userRepository) ? "/sponsor/dashboard" : null,
                EmailType.SPONSOR_STUDENT_MAPPED, ServiceSupport.vars("student_name", student.getName(), "student_code", ServiceSupport.refOf(student)));
        notificationService.notify(granteeId, "You have a sponsor",
                sponsor.getName() + grantorGranteeRepository.findFirstByGranteeId(granteeId).filter(g -> Boolean.TRUE.equals(g.getOnBehalf()))
                        .map(g -> " (" + onBehalfText(g.getDonorLabel(), chapterNames().get(g.getDonorChapterId())) + ")").orElse("")
                        + " is now your sponsor. You can see your payment schedule on your Payments page.",
                NotificationService.MAPPING, "/student/dashboard",
                EmailType.STUDENT_SPONSOR_ASSIGNED, ServiceSupport.vars("sponsor_name", sponsor.getName()));
    }

    /** Emails the previous sponsor that the student is no longer mapped to them. */
    private void notifyUnmapped(Long granteeId, Long previousId, Long currentId) {
        if (previousId == null) return;
        User student = userRepository.findById(granteeId).orElse(null);
        User previous = userRepository.findById(previousId).orElse(null);
        if (student == null || !realSponsor(previous)) return;
        boolean moved = currentId != null && userRepository.findById(currentId).filter(this::realSponsor).isPresent();
        notificationService.notify(previousId, "Student no longer mapped to you",
                student.getName() + " (" + ServiceSupport.refOf(student) + ") is no longer one of your sponsored students"
                        + (moved ? "; another sponsor has taken over." : ".")
                        + " Installments you already paid stay in your payment history.",
                NotificationService.MAPPING, UserRoles.has(previous, 5, userRepository) ? "/sponsor/dashboard" : null,
                EmailType.SPONSOR_STUDENT_UNMAPPED, ServiceSupport.vars("student_name", student.getName(), "student_code", ServiceSupport.refOf(student)));
        if (!moved) {
            notificationService.notify(granteeId, "Sponsor changed",
                    "You are currently not mapped to a sponsor. The office will let you know when a new sponsor is assigned.",
                    NotificationService.MAPPING, "/student/dashboard", EmailType.STUDENT_SPONSOR_REMOVED, ServiceSupport.vars());
        }
    }
}
