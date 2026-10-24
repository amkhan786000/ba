package com.rahbar.service;

import com.rahbar.entity.ApplicationPeriod;
import com.rahbar.entity.SponsorshipApplication;
import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.ApplicationPeriodRepository;
import com.rahbar.repository.SponsorshipApplicationRepository;
import com.rahbar.repository.UserRepository;
import com.rahbar.security.Access;
import com.rahbar.security.Section;
import com.rahbar.security.StudentScope;
import com.rahbar.util.Rows;
import com.rahbar.util.UserRoles;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Applications > Apply for Sponsorship: while an application period is active, a sponsor says how many students they
 * want to sponsor and what kind (college type, fathers' professions, family income). With SPONSOR_APPLY:EDIT the
 * office applies on behalf of a sponsor (chapter-scoped users: their chapter's) and sees all the applications.
 * Applying again in the same period updates the application; it also becomes the sponsor's preferences.
 */
@Service
public class SponsorApplicationService {

    static final int MAX_STUDENTS = 100;

    private final SponsorshipApplicationRepository applications;
    private final ApplicationPeriodRepository periods;
    private final UserRepository userRepository;
    private final SponsorPreferenceService preferences;
    private final StudentScope studentScope;
    private final com.rahbar.repository.GrantorGranteeRepository mappings;
    private final com.rahbar.repository.GranteeDetailsRepository families;

    public SponsorApplicationService(SponsorshipApplicationRepository applications, ApplicationPeriodRepository periods,
                                     UserRepository userRepository, SponsorPreferenceService preferences, StudentScope studentScope,
                                     com.rahbar.repository.GrantorGranteeRepository mappings, com.rahbar.repository.GranteeDetailsRepository families) {
        this.mappings = mappings;
        this.families = families;
        this.applications = applications;
        this.periods = periods;
        this.userRepository = userRepository;
        this.preferences = preferences;
        this.studentScope = studentScope;
    }

    /** The session sponsors apply for: the active application period (none: "stay tuned"). */
    private Optional<ApplicationPeriod> session() {
        return periods.findFirstByIsActiveTrueOrderByStartDateDesc();
    }

    private static boolean onBehalf() {
        return Access.can(Section.SPONSOR_APPLY, Section.Level.EDIT);
    }

    private static boolean isSponsor() {
        return Access.current().hasRole(5);
    }

    /**
     * The page: open or not, the period, the choices, whether the user applies for themselves (a sponsor) and / or
     * for others (then the sponsors they may choose), and the user's own application so far.
     */
    public Map<String, Object> form() {
        Map<String, Object> out = new LinkedHashMap<>();
        Optional<ApplicationPeriod> period = session();
        out.put("open", period.isPresent());
        out.put("for_self", isSponsor());
        out.put("for_others", onBehalf()); // also sees the applications of earlier sessions
        if (period.isEmpty()) return out;
        out.put("period", periodRow(period.get()));
        out.put("options", preferences.options());
        if (onBehalf()) {
            StudentScope.Scope scope = studentScope.current();
            Long unassigned = ServiceSupport.unassignedGrantorId(userRepository);
            List<Map<String, Object>> sponsors = new ArrayList<>();
            userRepository.findByRoleId(5).stream()
                    .filter(u -> !"Inactive".equalsIgnoreCase(u.getStatus()) && u.getMergedIntoId() == null && !u.getId().equals(unassigned))
                    .filter(scope::includesSponsor)
                    .sorted(Comparator.comparing(User::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                    .forEach(u -> sponsors.add(Rows.pick(u, "id", "user_id", "name", "chapter_name")));
            out.put("sponsors", sponsors);
        }
        Long me = Access.current().getUser().getId();
        out.put("me", me);
        return out;
    }

    /** What the form starts with for this sponsor: their application this period, else their preferences. */
    public Map<String, Object> current(Long sponsorId) {
        ApplicationPeriod period = requireSession();
        User sponsor = requireMayApplyFor(sponsorId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sponsor", Rows.pick(sponsor, "id", "user_id", "name", "chapter_name"));
        SponsorshipApplication a = applications.findByPeriodIdAndSponsorId(period.getId(), sponsor.getId()).orElse(null);
        out.put("application", a == null ? null : row(a, sponsor, Map.of()));
        out.put("preferences", SponsorPreferenceService.of(sponsor));
        return out;
    }

    /**
     * Body: { sponsorId (empty: oneself), studentsWanted, collegeType, fatherProfessions, incomeMin, incomeMax, note }.
     * Saves (or updates) the application for the active period and the sponsor's preferences.
     */
    @Transactional
    public Map<String, Object> apply(Map<String, Object> body) {
        ApplicationPeriod period = requireSession();
        Object id = body.get("sponsorId");
        User sponsor = requireMayApplyFor(id == null || String.valueOf(id).isBlank() ? null : Long.valueOf(String.valueOf(id).trim()));
        Integer wanted = ServiceSupport.isBlank(body.get("studentsWanted")) ? null : ServiceSupport.toInteger(body.get("studentsWanted"));
        if (wanted == null || wanted < 1 || wanted > MAX_STUDENTS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "How many students: a number from 1 to " + MAX_STUDENTS + ".");
        }
        String note = body.get("note") == null ? null : String.valueOf(body.get("note")).trim();
        if (note != null && note.length() > 500) throw new ApiException(HttpStatus.BAD_REQUEST, "The note can be at most 500 characters.");
        SponsorPreferenceService.Preferences p = SponsorPreferenceService.read(body);

        SponsorshipApplication a = applications.findByPeriodIdAndSponsorId(period.getId(), sponsor.getId()).orElseGet(() -> {
            SponsorshipApplication n = new SponsorshipApplication();
            n.setPeriodId(period.getId());
            n.setSponsorId(sponsor.getId());
            return n;
        });
        a.setStudentsWanted(wanted);
        a.setCollegeType(p.collegeType());
        a.setFatherProfessions(SponsorPreferenceService.join(p.fatherProfessions()));
        a.setIncomeMin(p.incomeMin());
        a.setIncomeMax(p.incomeMax());
        a.setNote(note == null || note.isEmpty() ? null : note);
        applications.save(a);
        SponsorPreferenceService.apply(sponsor, p);
        userRepository.save(sponsor);
        return row(a, sponsor, Map.of());
    }

    /** Applications for a period (default: the active one), for SPONSOR_APPLY:EDIT; chapter-scoped: their chapter's sponsors. */
    public Map<String, Object> list(Long periodId) {
        if (!onBehalf()) throw Access.forbidden("You may not see the sponsorship applications.");
        ApplicationPeriod period = periodId != null
                ? periods.findById(periodId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Application period not found."))
                : session().orElse(null);
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> periodRows = new ArrayList<>();
        periods.findAll().stream().sorted(Comparator.comparing(ApplicationPeriod::getStartDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .forEach(x -> periodRows.add(periodRow(x)));
        out.put("periods", periodRows);
        out.put("period", period == null ? null : periodRow(period));
        if (period == null) {
            out.put("rows", List.of());
            out.put("students_wanted", 0);
            return out;
        }
        StudentScope.Scope scope = studentScope.current();
        List<SponsorshipApplication> list = applications.findByPeriodIdOrderByCreatedAtDesc(period.getId());
        Set<Long> ids = new HashSet<>();
        list.forEach(a -> { ids.add(a.getSponsorId()); if (a.getUpdatedBy() != null) ids.add(a.getUpdatedBy()); });
        Map<Long, User> users = new HashMap<>();
        userRepository.findAllById(ids).forEach(u -> users.put(u.getId(), u));
        Map<Long, String> names = new HashMap<>();
        users.values().forEach(u -> names.put(u.getId(), u.getName()));
        List<Map<String, Object>> rows = new ArrayList<>();
        int wanted = 0;
        for (SponsorshipApplication a : list) {
            User sponsor = users.get(a.getSponsorId());
            if (sponsor == null || !scope.includesSponsor(sponsor)) continue;
            rows.add(row(a, sponsor, names));
            wanted += a.getStudentsWanted() == null ? 0 : a.getStudentsWanted();
        }
        out.put("rows", rows);
        out.put("students_wanted", wanted);
        return out;
    }

    /**
     * Students without a sponsor (studying, the user may see them) who fit an application's preferences: up to three
     * times as many as the sponsor wants (at least 10), with why they fit.
     */
    public Map<String, Object> suggestions(Long applicationId) {
        if (!onBehalf()) throw Access.forbidden("You may not see the sponsorship applications.");
        SponsorshipApplication a = applications.findById(applicationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Application not found."));
        User sponsor = studentScope.requireSponsor(a.getSponsorId());
        SponsorMatch.Wish wish = new SponsorMatch.Wish(a.getCollegeType(), SponsorPreferenceService.split(a.getFatherProfessions()),
                a.getIncomeMin(), a.getIncomeMax());
        Long unassigned = ServiceSupport.unassignedGrantorId(userRepository);
        Set<Long> sponsored = new HashSet<>();
        mappings.findAll().forEach(g -> { if (g.getGrantorId() != null && !g.getGrantorId().equals(unassigned)) sponsored.add(g.getGranteeId()); });
        Map<Long, com.rahbar.entity.GranteeDetails> family = new HashMap<>();
        families.findAll().stream().filter(g -> g.getUserId() != null)
                .sorted(Comparator.comparing(com.rahbar.entity.GranteeDetails::getGranteeDetailId)).forEach(g -> family.putIfAbsent(g.getUserId(), g));
        StudentScope.Scope scope = studentScope.current();
        int limit = Math.max(10, (a.getStudentsWanted() == null ? 1 : a.getStudentsWanted()) * 3);
        List<Map<String, Object>> rows = new ArrayList<>();
        int fitting = 0;
        for (User u : userRepository.findByRoleId(ServiceSupport.STUDENT_ROLE).stream()
                .filter(u -> "active".equalsIgnoreCase(u.getStatus()) && u.getMergedIntoId() == null && !sponsored.contains(u.getId()))
                .filter(u -> StudyStatus.STUDYING.equals(StudyStatus.of(u.getStudyStatus())) && !DataQualityService.coachingOnly(u))
                .filter(scope::includes)
                .sorted(Comparator.comparing(User::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))).toList()) {
            SponsorMatch.Fit fit = SponsorMatch.fit(wish, u, family.get(u.getId()));
            if (!fit.fits()) continue;
            fitting++;
            if (rows.size() >= limit) continue;
            Map<String, Object> r = Rows.pick(u, "id", "user_id", "name", "chapter_name", "year");
            r.put("why", fit.why());
            rows.add(r);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sponsor", Rows.pick(sponsor, "id", "user_id", "name"));
        out.put("students_wanted", a.getStudentsWanted());
        out.put("fitting", fitting);
        out.put("rows", rows);
        return out;
    }

    private ApplicationPeriod requireSession() {
        return session().orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                "Sponsorship applications aren't open yet. Stay tuned: you can apply once the next session starts."));
    }

    /** Oneself when a sponsor; another sponsor needs SPONSOR_APPLY:EDIT (and, chapter-scoped, one of their chapter). */
    private User requireMayApplyFor(Long sponsorId) {
        User me = Access.current().getUser();
        if (sponsorId == null || sponsorId.equals(me.getId())) {
            if (!isSponsor()) throw new ApiException(HttpStatus.BAD_REQUEST, "Choose the sponsor you are applying for.");
            return ServiceSupport.requireUser(userRepository, me.getId(), "Sponsor not found");
        }
        if (!onBehalf()) throw Access.forbidden("You can only apply for yourself.");
        User sponsor = studentScope.requireSponsor(sponsorId);
        if (!UserRoles.has(sponsor, 5, userRepository)) throw new ApiException(HttpStatus.BAD_REQUEST, "That person is not a sponsor.");
        return sponsor;
    }

    private static Map<String, Object> periodRow(ApplicationPeriod p) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", p.getId());
        r.put("start_date", p.getStartDate());
        r.put("end_date", p.getEndDate());
        r.put("active", Boolean.TRUE.equals(p.getIsActive()));
        return r;
    }

    private static Map<String, Object> row(SponsorshipApplication a, User sponsor, Map<Long, String> names) {
        List<String> professions = SponsorPreferenceService.split(a.getFatherProfessions());
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("application_id", a.getApplicationId());
        r.put("period_id", a.getPeriodId());
        r.put("sponsor_id", sponsor.getId());
        r.put("sponsor_code", sponsor.getUserId());
        r.put("sponsor_name", sponsor.getName());
        r.put("chapter_name", sponsor.getChapterName());
        r.put("students_wanted", a.getStudentsWanted());
        r.put("college_type", a.getCollegeType());
        r.put("father_professions", professions);
        r.put("income_min", a.getIncomeMin());
        r.put("income_max", a.getIncomeMax());
        r.put("preferences", SponsorPreferenceService.summary(a.getCollegeType(), professions, a.getIncomeMin(), a.getIncomeMax()));
        r.put("note", a.getNote());
        r.put("applied_by", a.getUpdatedBy() == null || a.getUpdatedBy().equals(sponsor.getId()) ? null : names.get(a.getUpdatedBy()));
        r.put("created_at", a.getCreatedAt());
        r.put("updated_at", a.getUpdatedAt());
        return r;
    }
}
