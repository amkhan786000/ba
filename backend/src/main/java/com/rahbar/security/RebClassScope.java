package com.rahbar.security;

import com.rahbar.entity.RebSection;
import com.rahbar.entity.RebStaff;
import com.rahbar.entity.RebTeacherAssignment;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.RebSectionRepository;
import com.rahbar.repository.RebStaffRepository;
import com.rahbar.repository.RebTeacherAssignmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Which Rahbar-e-Banaat sections and subjects the signed-in user may work with. Everyone sees everything, except
 * users whose role covers "only the REB classes they teach" (REB Teacher): they get the sections where they are
 * class teacher or teach a subject, and may enter marks only for the subjects they teach there. Their login must be
 * linked to their staff record (REB > Teachers & Staff); otherwise they see nothing.
 */
@Component
public class RebClassScope {

    /** all = no limit; else the sections, and "section:subject" pairs, the teacher may work with. */
    public record Scope(boolean all, Set<Long> sections, Set<String> subjects) {
        public boolean seesSection(Long sectionId) {
            return all || (sectionId != null && sections.contains(sectionId));
        }

        public boolean teaches(Long sectionId, Long subjectId) {
            return all || subjects.contains(sectionId + ":" + subjectId);
        }
    }

    private static final Scope ALL = new Scope(true, Set.of(), Set.of());

    private final RebStaffRepository staff;
    private final RebTeacherAssignmentRepository assignments;
    private final RebSectionRepository sections;

    public RebClassScope(RebStaffRepository staff, RebTeacherAssignmentRepository assignments, RebSectionRepository sections) {
        this.staff = staff;
        this.assignments = assignments;
        this.sections = sections;
    }

    public Scope current() {
        if (!Access.rebClassesScoped()) return ALL;
        RebStaff me = staff.findByUserId(Access.current().getUser().getId()).orElse(null);
        if (me == null || !"ACTIVE".equals(me.getStatus())) return new Scope(false, Set.of(), Set.of());
        Set<Long> secs = new HashSet<>();
        Set<String> subjects = new HashSet<>();
        for (RebTeacherAssignment a : assignments.findByStaffId(me.getId())) {
            secs.add(a.getSectionId());
            subjects.add(a.getSectionId() + ":" + a.getSubjectId());
        }
        for (RebSection s : sections.findAll()) {
            if (me.getId().equals(s.getClassTeacherStaffId())) secs.add(s.getId());
        }
        return new Scope(false, secs, subjects);
    }

    /** 404 unless the user may work with the section. */
    public void requireSection(Long sectionId) {
        if (!current().seesSection(sectionId)) throw new ApiException(HttpStatus.NOT_FOUND, "Class not found.");
    }
}
