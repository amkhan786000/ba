package com.rahbar.security;

import com.rahbar.entity.GranteeDetails;
import com.rahbar.entity.GrantorGrantee;
import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.GranteeDetailsRepository;
import com.rahbar.repository.GrantorGranteeRepository;
import com.rahbar.repository.RccCenterRepository;
import com.rahbar.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Which students (and applications, sponsors) the signed-in staff user may see, from their role's scope:
 * <ul>
 *   <li>ALL: everything;</li>
 *   <li>CHAPTER (e.g. Chapter Lead): students of their own chapter, and the sponsors of their chapter (sponsors whose
 *       chapter is theirs, or who sponsor one of their students);</li>
 *   <li>RCC (e.g. RCC / coaching center lead): students whose RCC name (on their application) is their center's name,
 *       compared ignoring case and extra spaces. The office corrects a wrong RCC name in Student Directory > BA Details.</li>
 * </ul>
 * A scoped user without a chapter / RCC center sees nothing. Use {@link #current()} once per request.
 */
@Component
public class StudentScope {

    private final UserRepository userRepository;
    private final GranteeDetailsRepository granteeDetailsRepository;
    private final GrantorGranteeRepository grantorGranteeRepository;
    private final RccCenterRepository rccCenterRepository;

    public StudentScope(UserRepository userRepository, GranteeDetailsRepository granteeDetailsRepository,
                        GrantorGranteeRepository grantorGranteeRepository, RccCenterRepository rccCenterRepository) {
        this.userRepository = userRepository;
        this.granteeDetailsRepository = granteeDetailsRepository;
        this.grantorGranteeRepository = grantorGranteeRepository;
        this.rccCenterRepository = rccCenterRepository;
    }

    /** The signed-in user's view of students. */
    public Scope current() {
        if (!Access.chapterScoped() && !Access.rccScoped()) return Scope.ALL;
        User me = Access.current().getUser();
        if (Access.chapterScoped()) {
            return me.getChapterId() == null ? Scope.NONE : new Scope(me.getChapterId(), null, null, this);
        }
        String centerName = me.getRccCenterId() == null ? null
                : rccCenterRepository.findById(me.getRccCenterId()).map(c -> c.getCenterName()).orElse(null);
        return centerName == null ? Scope.NONE : new Scope(null, me.getRccCenterId(), normalize(centerName), this);
    }

    /** 404 unless the signed-in user may see this student (so other students' records don't even appear to exist). */
    public User requireStudent(Long studentId) {
        User u = userRepository.findById(studentId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Student not found"));
        if (!current().includes(u)) throw new ApiException(HttpStatus.NOT_FOUND, "Student not found");
        return u;
    }

    /** 404 unless the signed-in user may see this application. */
    public GranteeDetails requireApplication(GranteeDetails g) {
        Scope scope = current();
        if (scope.all()) return g;
        Map<Long, User> users = new HashMap<>();
        if (g.getUserId() != null) userRepository.findById(g.getUserId()).ifPresent(u -> users.put(u.getId(), u));
        if (!scope.includesApplication(g, users)) throw new ApiException(HttpStatus.NOT_FOUND, "Application not found.");
        return g;
    }

    /** 404 unless the signed-in user may see this sponsor. */
    public User requireSponsor(Long sponsorId) {
        User u = userRepository.findById(sponsorId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Sponsor not found"));
        if (!current().includesSponsor(u)) throw new ApiException(HttpStatus.NOT_FOUND, "Sponsor not found");
        return u;
    }

    /** Every user by users.id (for checking many rows at once). */
    public Map<Long, User> usersById() {
        Map<Long, User> users = new HashMap<>();
        userRepository.findAll().forEach(u -> users.put(u.getId(), u));
        return users;
    }

    /** RCC names compare ignoring case and repeated spaces. */
    public static String normalize(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** One user's scope; caches the lookups it needs, so build it once per request. */
    public static final class Scope {
        public static final Scope ALL = new Scope(null, null, null, null);
        public static final Scope NONE = new Scope(-1L, null, null, null);

        private final Long chapterId;
        private final Long rccCenterId;
        private final String rccName;
        private final StudentScope owner;
        private Map<Long, String> rccOfStudent;
        private Set<Long> sponsorIds;

        private Scope(Long chapterId, Long rccCenterId, String rccName, StudentScope owner) {
            this.chapterId = chapterId;
            this.rccCenterId = rccCenterId;
            this.rccName = rccName;
            this.owner = owner;
        }

        /** True for unscoped users (everything visible). */
        public boolean all() {
            return this == ALL;
        }

        public boolean none() {
            return this == NONE;
        }

        /** The RCC center of an RCC-scoped user (null otherwise). */
        public Long rccCenterId() {
            return rccCenterId;
        }

        /** "your chapter" / "your RCC center", for messages. */
        public String label() {
            return chapterId != null ? "your chapter" : rccCenterId != null ? "your RCC center" : "your area";
        }

        public boolean includes(User student) {
            if (all()) return true;
            if (none() || student == null) return false;
            if (chapterId != null) return chapterId.equals(student.getChapterId());
            return rccCenterId.equals(student.getRccCenterId()) || rccName.equals(rccOf(student.getId()));
        }

        public boolean includesId(Long studentId, Map<Long, User> users) {
            return all() || (studentId != null && includes(users.get(studentId)));
        }

        /** An application: by its student account when linked, else (RCC scope) by the RCC name on the form. */
        public boolean includesApplication(GranteeDetails g, Map<Long, User> users) {
            if (all()) return true;
            if (none()) return false;
            if (g.getUserId() != null && users.get(g.getUserId()) != null) return includes(users.get(g.getUserId()));
            return rccCenterId != null && rccName.equals(normalize(g.getRccName()));
        }

        /** A sponsor (by users.id): chapter scope sees its chapter's sponsors and the sponsors of its students. */
        public boolean includesSponsor(User sponsor) {
            if (all()) return true;
            if (none() || sponsor == null) return false;
            if (chapterId != null && chapterId.equals(sponsor.getChapterId())) return true;
            return sponsorIds().contains(sponsor.getId());
        }

        private Set<Long> sponsorIds() {
            if (sponsorIds == null) {
                Map<Long, User> users = new HashMap<>();
                owner.userRepository.findAll().forEach(u -> users.put(u.getId(), u));
                sponsorIds = new HashSet<>();
                for (GrantorGrantee gg : owner.grantorGranteeRepository.findAll()) {
                    if (gg.getGrantorId() != null && includes(users.get(gg.getGranteeId()))) sponsorIds.add(gg.getGrantorId());
                }
            }
            return sponsorIds;
        }

        private String rccOf(Long studentId) {
            if (rccOfStudent == null) {
                rccOfStudent = new HashMap<>();
                // The student's RCC name: from their (first) application, where BA Details also saves it.
                owner.granteeDetailsRepository.findAll().stream()
                        .filter(g -> g.getUserId() != null)
                        .sorted(Comparator.comparing(GranteeDetails::getGranteeDetailId))
                        .forEach(g -> rccOfStudent.putIfAbsent(g.getUserId(), normalize(g.getRccName())));
            }
            return rccOfStudent.getOrDefault(studentId, "");
        }
    }
}
