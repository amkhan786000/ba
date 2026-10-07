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
 */
@Component
public class SponsorPrivacy {

    /** What may be shown of a sponsor's own row. */
    private static final Set<String> VISIBLE = Set.of("id", "user_id", "name", "role_id", "role_name", "role",
            "status", "student_count", "students", "masked");
    /** "<prefix>_<detail>" keys that describe a student's sponsor, e.g. grantor_phone, sponsor_email. */
    private static final List<String> PREFIXES = List.of("sponsor", "grantor", "current_sponsor", "assigned_sponsor");
    private static final List<String> DETAILS = List.of("email", "phone", "mobile", "chapter", "chapter_id",
            "chapter_name", "address", "region");
    /** Nested objects that are a student's sponsor, e.g. { "sponsor": { ... } }. */
    private static final Set<String> SPONSOR_OBJECTS = Set.of("sponsor", "grantor");

    private final UserRepository userRepository;

    public SponsorPrivacy(UserRepository userRepository) {
        this.userRepository = userRepository;
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
        return new Run(me.getUser().getId(), me.getUser().getUserId()).walk(body, null);
    }

    /** One masking pass; the sponsor ids are only loaded when a row needs them. */
    private final class Run {
        private final Long myId;
        private final String myCode;
        private Set<Long> sponsorIds;

        Run(Long myId, String myCode) {
            this.myId = myId;
            this.myCode = myCode;
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
                row.replaceAll((k, v) -> VISIBLE.contains(k) ? v : null);
                row.put("masked", true);
                return row;
            }
            for (String prefix : PREFIXES) {
                if (isMe(row.get(prefix + "_id"), row.get(prefix + "_code"))) continue;
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
