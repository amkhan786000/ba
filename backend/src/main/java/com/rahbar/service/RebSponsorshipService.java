package com.rahbar.service;

import com.rahbar.util.UserRoles;
import com.rahbar.entity.RebSponsorship;
import com.rahbar.entity.RebStudent;
import com.rahbar.entity.User;
import com.rahbar.repository.RebSponsorshipRepository;
import com.rahbar.repository.RebStudentRepository;
import com.rahbar.repository.UserRepository;
import com.rahbar.util.Rows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;

import static com.rahbar.util.RebSupport.*;

/**
 * REB sponsorships: a Rahbar sponsor supports a girl with a monthly amount (default from REB Settings, Rs. 500).
 * Bills made for months inside the sponsorship put that amount on the sponsor's share. Sponsors are shown by name
 * and ID only (sponsor privacy). A girl has at most one sponsorship at a time.
 */
@Service
public class RebSponsorshipService {

    private final RebSponsorshipRepository sponsorships;
    private final RebStudentRepository students;
    private final UserRepository users;
    private final RebSetupService setup;

    public RebSponsorshipService(RebSponsorshipRepository sponsorships, RebStudentRepository students, UserRepository users,
                                 RebSetupService setup) {
        this.sponsorships = sponsorships;
        this.students = students;
        this.users = users;
        this.setup = setup;
    }

    /** Is the sponsorship running in that month (YYYY-MM)? */
    public static boolean covers(RebSponsorship s, String period) {
        return period.compareTo(s.getStartPeriod()) >= 0 && (s.getEndPeriod() == null || period.compareTo(s.getEndPeriod()) <= 0);
    }

    /** The sponsorship of a girl that covers the month, if any. */
    public Optional<RebSponsorship> covering(Long studentId, String period) {
        return sponsorships.findByStudentIdOrderByStartPeriodDesc(studentId).stream().filter(s -> covers(s, period)).findFirst();
    }

    /** Every sponsorship (or only ACTIVE / ENDED ones) with the girl and the sponsor's name and ID. */
    public List<Map<String, Object>> list(String status) {
        Map<Long, RebStudent> girls = new HashMap<>();
        students.findAll().forEach(s -> girls.put(s.getId(), s));
        List<RebSponsorship> all = sponsorships.findAllByOrderByIdDesc();
        Map<Long, User> sponsors = new HashMap<>();
        users.findAllById(all.stream().map(RebSponsorship::getSponsorUserId).distinct().toList()).forEach(u -> sponsors.put(u.getId(), u));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RebSponsorship s : all) {
            if (status != null && !status.isBlank() && !status.equalsIgnoreCase(s.getStatus())) continue;
            rows.add(row(s, girls.get(s.getStudentId()), sponsors.get(s.getSponsorUserId())));
        }
        return rows;
    }

    public List<Map<String, Object>> ofStudent(Long studentId) {
        RebStudent girl = students.findById(studentId).orElse(null);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RebSponsorship s : sponsorships.findByStudentIdOrderByStartPeriodDesc(studentId)) {
            rows.add(row(s, girl, users.findById(s.getSponsorUserId()).orElse(null)));
        }
        return rows;
    }

    static Map<String, Object> row(RebSponsorship s, RebStudent girl, User sponsor) {
        Map<String, Object> r = Rows.of(s);
        r.put("student_name", girl == null ? null : girl.getName());
        r.put("admission_no", girl == null ? null : girl.getAdmissionNo());
        r.put("sponsor_name", sponsor == null ? null : sponsor.getName());
        r.put("sponsor_code", sponsor == null ? null : sponsor.getUserId());
        return r;
    }

    /** Sponsors to choose from (name and ID only), matching q. */
    public List<Map<String, Object>> sponsorOptions(String q) {
        String query = q == null ? "" : q.strip().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> out = new ArrayList<>();
        for (User u : users.findByRoleIdIn(ServiceSupport.SPONSOR_ROLES)) {
            if ("Inactive".equalsIgnoreCase(u.getStatus()) || u.getMergedIntoId() != null) continue;
            if (!query.isEmpty() && !((u.getName() == null ? "" : u.getName().toLowerCase(Locale.ROOT)).contains(query)
                    || (u.getUserId() != null && u.getUserId().toLowerCase(Locale.ROOT).contains(query)))) continue;
            out.add(Map.of("id", u.getId(), "name", u.getName() == null ? "" : u.getName(), "user_id", u.getUserId() == null ? "" : u.getUserId()));
            if (out.size() >= 30) break;
        }
        out.sort(Comparator.comparing(m -> String.valueOf(m.get("name")), String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    /** Body: { studentId, sponsorUserId, monthlyAmount (default: REB setting), startPeriod (default: this month), note }. */
    @Transactional
    public RebSponsorship start(Map<String, Object> body) {
        RebStudent girl = students.findById(id(body.get("studentId"))).orElseThrow(() -> bad("Choose the girl."));
        User sponsor = requireSponsor(id(body.get("sponsorUserId")));
        RebSponsorship s = new RebSponsorship();
        s.setStudentId(girl.getId());
        s.setSponsorUserId(sponsor.getId());
        s.setMonthlyAmount(amount(body.get("monthlyAmount")));
        String start = RebFeeService.period(body.get("startPeriod"), "Start month");
        s.setStartPeriod(start == null ? YearMonth.now().toString() : start);
        s.setNote(text(body.get("note"), "Note", 500, false));
        checkOverlap(s);
        sponsorships.save(s);
        if (!"SPONSORED".equals(girl.getFeePlan())) {
            girl.setFeePlan("SPONSORED");
            students.save(girl);
        }
        return s;
    }

    /** Body: { monthlyAmount, startPeriod, note }. */
    @Transactional
    public RebSponsorship update(Long id, Map<String, Object> body) {
        RebSponsorship s = sponsorships.findById(id).orElseThrow(() -> notFound("Sponsorship"));
        if (body.containsKey("monthlyAmount")) s.setMonthlyAmount(amount(body.get("monthlyAmount")));
        if (body.containsKey("startPeriod")) {
            String start = RebFeeService.period(body.get("startPeriod"), "Start month");
            if (start == null) throw bad("Start month is required.");
            s.setStartPeriod(start);
        }
        if (body.containsKey("note")) s.setNote(text(body.get("note"), "Note", 500, false));
        checkOverlap(s);
        return sponsorships.save(s);
    }

    /** Body: { endPeriod } (default: this month): the last month the sponsor pays for. */
    @Transactional
    public RebSponsorship end(Long id, Map<String, Object> body) {
        RebSponsorship s = sponsorships.findById(id).orElseThrow(() -> notFound("Sponsorship"));
        String end = RebFeeService.period(body.get("endPeriod"), "Last month");
        s.setEndPeriod(end == null ? YearMonth.now().toString() : end);
        if (s.getEndPeriod().compareTo(s.getStartPeriod()) < 0) throw bad("The last month is before the start month.");
        s.setStatus("ENDED");
        sponsorships.save(s);
        RebStudent girl = students.findById(s.getStudentId()).orElse(null);
        boolean stillSponsored = sponsorships.findByStudentIdOrderByStartPeriodDesc(s.getStudentId()).stream().anyMatch(x -> "ACTIVE".equals(x.getStatus()));
        if (girl != null && !stillSponsored && "SPONSORED".equals(girl.getFeePlan())) {
            girl.setFeePlan("SELF");
            students.save(girl);
        }
        return s;
    }

    private BigDecimal amount(Object v) {
        BigDecimal a = money(v, "Monthly amount");
        if (a == null) a = new BigDecimal(setup.setting("sponsorship_amount", "500"));
        if (a.signum() <= 0) throw bad("The monthly amount must be more than 0.");
        return a;
    }

    private User requireSponsor(Long id) {
        User u = id == null ? null : users.findById(id).orElse(null);
        if (u == null || !UserRoles.hasAny(u, ServiceSupport.SPONSOR_ROLES, users)) throw bad("Choose a Rahbar sponsor.");
        return u;
    }

    /** A girl has one sponsorship at a time. */
    private void checkOverlap(RebSponsorship s) {
        for (RebSponsorship other : sponsorships.findByStudentIdOrderByStartPeriodDesc(s.getStudentId())) {
            if (other.getId() != null && other.getId().equals(s.getId())) continue;
            String aEnd = s.getEndPeriod() == null ? "9999-12" : s.getEndPeriod();
            String bEnd = other.getEndPeriod() == null ? "9999-12" : other.getEndPeriod();
            if (s.getStartPeriod().compareTo(bEnd) <= 0 && other.getStartPeriod().compareTo(aEnd) <= 0) {
                throw bad("She already has a sponsorship for those months. End it first.");
            }
        }
    }
}
