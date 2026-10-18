package com.rahbar.security;

import com.rahbar.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Sponsors' details are private: without the SPONSOR_DETAILS permission a user sees only a sponsor's name and
 * user ID. Everything else about the sponsor (email, phone, address, chapter, payments total, ...) is blanked in
 * every API response (SponsorPrivacyAdvice) and in report downloads. A sponsor always sees their own details.
 *
 * A "sponsor" is a user with the Sponsor role, plus whoever sponsors a student (grantor) wherever a row describes
 * a student's sponsor.
 * <p>
 * An <b>anonymous sponsor</b> (users.anonymous_sponsor) is hidden from everyone but the Super Admin and the sponsor
 * themselves, whatever their permissions: the name shows as "Anonymous sponsor" and every detail is blanked. Their id
 * and user ID stay (so staff can still pick them, e.g. when mapping a student), but say nothing about who they are.
 */
@Component
public class SponsorPrivacy {

    /** What may be shown of a sponsor's own row. */
    private static final Set<String> VISIBLE = Set.of("id", "user_id", "name", "role_id", "role_name", "role",
            "status", "student_count", "students", "masked");
    /** "<prefix>_<detail>" keys that describe a student's sponsor, e.g. grantor_phone, sponsor_email. */
    private static final List<String> PREFIXES = List.of("sponsor", "grantor", "current_sponsor", "assigned_sponsor");
    private static final List<String> DETAILS = List.of("email", "phone", "mobile", "chapter", "chapter_id",
            "chapter_name", "address", "region", "pan");
    /** Nested objects that are a student's sponsor, e.g. { "sponsor": { ... } }. */
    private static final Set<String> SPONSOR_OBJECTS = Set.of("sponsor", "grantor");

    /** How an anonymous sponsor is named to everyone but the Super Admin and themselves. */
    public static final String ANONYMOUS = "Anonymous sponsor";

    private final UserRepository userRepository;

    public SponsorPrivacy(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Whether responses must go through {@link #mask}: details hidden, or anonymous sponsors to hide. */
    public boolean appliesNow() {
        return hidesDetails() || hidesAnonymous();
    }

    /** True for a signed-in user other than the Super Admin, when there is at least one anonymous sponsor. */
    public boolean hidesAnonymous() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof RahbarUserPrincipal p && !p.hasRole(com.rahbar.entity.Role.SUPER_ADMIN)
                && !userRepository.findAnonymousSponsorIds().isEmpty();
    }

    /** How a student is told who their sponsor is: the name, or "A sponsor from <student's chapter>" when anonymous. */
    public static String forStudent(com.rahbar.entity.User sponsor, com.rahbar.entity.User student) {
        if (sponsor == null) return null;
        if (!Boolean.TRUE.equals(sponsor.getAnonymousSponsor())) return sponsor.getName();
        String chapter = student == null ? null : student.getChapterName();
        return chapter == null || chapter.isBlank() ? ANONYMOUS : "A sponsor from " + chapter;
    }

    /**
     * How to name a sponsor in text the signed-in user (or, with {@code forStudent}, a student) reads: the name, or for
     * an anonymous sponsor "Anonymous sponsor" ("A sponsor from Jeddah Chapter" when the student's chapter is given).
     */
    public String nameFor(com.rahbar.entity.User sponsor, String studentChapter) {
        if (sponsor == null) return null;
        if (!Boolean.TRUE.equals(sponsor.getAnonymousSponsor())) return sponsor.getName();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (studentChapter == null && auth != null && auth.getPrincipal() instanceof RahbarUserPrincipal p
                && (p.hasRole(com.rahbar.entity.Role.SUPER_ADMIN) || sponsor.getId().equals(p.getUser().getId()))) return sponsor.getName();
        return studentChapter == null || studentChapter.isBlank() ? ANONYMOUS : "A sponsor from " + studentChapter;
    }

    /** True when the signed-in user may not see sponsors' details (unauthenticated requests carry no sponsor data). */
    public boolean hidesDetails() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof RahbarUserPrincipal p
                && !p.getPermissions().contains(Section.SPONSOR_DETAILS.key(Section.Level.VIEW));
    }

    /** Copy of the body (maps / lists, any depth) with sponsors' details blanked for the signed-in user. */
    public Object mask(Object body) {
        RahbarUserPrincipal me = Access.current();
        boolean anonymous = hidesAnonymous();
        return new Run(me.getUser().getId(), me.getUser().getUserId(), hidesDetails(),
                anonymous ? new HashSet<>(userRepository.findAnonymousSponsorIds()) : Set.of(),
                anonymous ? new HashSet<>(userRepository.findAnonymousSponsorCodes()) : Set.of()).walk(body, null);
    }

    /** One masking pass; the sponsor ids are only loaded when a row needs them. */
    private final class Run {
        private final Long myId;
        private final String myCode;
        private final boolean hideDetails;
        private final Set<Long> anonymousIds;
        private final Set<String> anonymousCodes;
        private Set<Long> sponsorIds;

        Run(Long myId, String myCode, boolean hideDetails, Set<Long> anonymousIds, Set<String> anonymousCodes) {
            this.myId = myId;
            this.myCode = myCode;
            this.hideDetails = hideDetails;
            this.anonymousIds = anonymousIds;
            this.anonymousCodes = anonymousCodes;
        }

        /** An anonymous sponsor, by users.id (a number) or user ID (text; reports put the code in "sponsor_id"). */
        private boolean anonymous(Object id, Object code) {
            if (anonymousIds.isEmpty()) return false;
            return (id instanceof Number n && anonymousIds.contains(n.longValue()))
                    || (id instanceof String sid && anonymousCodes.contains(sid))
                    || (code != null && anonymousCodes.contains(String.valueOf(code)));
        }

        Object walk(Object value, String key) {
            if (value instanceof Map<?, ?> map) return maskMap(map, key);
            if (value instanceof Collection<?> list) {
                List<Object> out = new ArrayList<>(list.size());
                for (Object item : list) out.add(walk(item, key));
                return out;
            }
            return value;
        }

        private Map<String, Object> maskMap(Map<?, ?> source, String parentKey) {
            Map<String, Object> row = new LinkedHashMap<>();
            source.forEach((k, v) -> row.put(String.valueOf(k), walk(v, String.valueOf(k))));

            if (isSponsorRow(row, parentKey) && !isMe(row.get("id"), row.get("user_id"))) {
                if (anonymous(row.get("id"), row.get("user_id"))) {
                    row.replaceAll((k, v) -> VISIBLE.contains(k) && !"name".equals(k) ? v : null);
                    row.put("name", ANONYMOUS);
                    row.put("masked", true);
                    row.put("anonymous", true);
                    return row;
                }
                if (hideDetails) {
                    row.replaceAll((k, v) -> VISIBLE.contains(k) ? v : null);
                    row.put("masked", true);
                    return row;
                }
            }
            for (String prefix : PREFIXES) {
                if (isMe(row.get(prefix + "_id"), row.get(prefix + "_code"))) continue;
                boolean anon = anonymous(row.get(prefix + "_id"), row.get(prefix + "_code"));
                if (anon && row.containsKey(prefix + "_name")) row.put(prefix + "_name", ANONYMOUS);
                if (!anon && !hideDetails) continue;
                for (String detail : DETAILS) {
                    String k = prefix + "_" + detail;
                    if (row.containsKey(k)) row.put(k, null);
                }
            }
            return row;
        }

        /** A row about a user who is a sponsor: by role, by being a student's sponsor object, or by id. */
        private boolean isSponsorRow(Map<String, Object> row, String parentKey) {
            if (!row.containsKey("user_id") || !row.containsKey("name")) return false;
            if (parentKey != null && SPONSOR_OBJECTS.contains(parentKey)) return true;
            Object roleId = row.get("role_id");
            if (roleId instanceof Number n && n.intValue() == 5) return true;
            Object roleName = row.containsKey("role_name") ? row.get("role_name") : row.get("role");
            if (roleName != null && "sponsor".equalsIgnoreCase(String.valueOf(roleName))) return true;
            return row.get("id") instanceof Number id && sponsorIds().contains(id.longValue());
        }

        private boolean isMe(Object id, Object code) {
            return (id instanceof Number n && n.longValue() == myId) || (code != null && String.valueOf(code).equals(myCode));
        }

        private Set<Long> sponsorIds() {
            if (sponsorIds == null) sponsorIds = new HashSet<>(userRepository.findSponsorIds());
            return sponsorIds;
        }
    }
}
