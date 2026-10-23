package com.rahbar.service;

import com.rahbar.entity.RccCenter;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.RccCenterRepository;
import com.rahbar.security.StudentScope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Which coaching centers the signed-in user may work with: every center for the office, only their own for an
 * RCC-scoped user (coaching center coordinator or teacher), none for a chapter-scoped one.
 */
@Component
public class CoachingAccess {

    private final RccCenterRepository centers;
    private final StudentScope studentScope;

    public CoachingAccess(RccCenterRepository centers, StudentScope studentScope) {
        this.centers = centers;
        this.studentScope = studentScope;
    }

    /** The centers the user may see, by name. */
    public List<RccCenter> centers() {
        StudentScope.Scope scope = studentScope.current();
        if (scope.all()) {
            List<RccCenter> all = new ArrayList<>(centers.findAll());
            all.sort(Comparator.comparing(RccCenter::getCenterName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
            return all;
        }
        return scope.rccCenterId() == null ? List.of() : centers.findById(scope.rccCenterId()).map(List::of).orElse(List.of());
    }

    /** True when the user is limited to their own center (the screens then hide the center choice). */
    public boolean scoped() {
        return !studentScope.current().all();
    }

    /** Ids of {@link #centers()}. */
    public Set<Long> centerIds() {
        Set<Long> ids = new HashSet<>();
        centers().forEach(c -> ids.add(c.getRccCenterId()));
        return ids;
    }

    /** Center id -> name for the user's centers. */
    public Map<Long, String> names() {
        Map<Long, String> names = new HashMap<>();
        centers().forEach(c -> names.put(c.getRccCenterId(), c.getCenterName()));
        return names;
    }

    /**
     * The center a record goes to: the one asked for (which the user must be allowed), or, for a user limited to one
     * center, theirs. 400 when none is given, 404 when it isn't theirs.
     */
    public Long requireCenter(Object requested) {
        Set<Long> allowed = centerIds();
        if (requested == null || String.valueOf(requested).isBlank()) {
            if (scoped() && allowed.size() == 1) return allowed.iterator().next();
            throw new ApiException(HttpStatus.BAD_REQUEST, scoped() && allowed.isEmpty()
                    ? "Your account is not linked to a coaching center. Ask the office to set it." : "Choose the coaching center.");
        }
        Long id;
        try {
            id = Long.valueOf(String.valueOf(requested).trim());
        } catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose the coaching center.");
        }
        if (!allowed.contains(id)) throw new ApiException(HttpStatus.NOT_FOUND, "Coaching center not found.");
        return id;
    }

    /** 404 unless the record's center is one of the user's. */
    public void requireVisible(Long centerId, String what) {
        if (centerId == null || !centerIds().contains(centerId)) throw new ApiException(HttpStatus.NOT_FOUND, what + " not found.");
    }
}
