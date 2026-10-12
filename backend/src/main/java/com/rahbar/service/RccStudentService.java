package com.rahbar.service;

import com.rahbar.util.UserRoles;
import com.rahbar.entity.GranteeDetails;
import com.rahbar.entity.RccCenter;
import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.GranteeDetailsRepository;
import com.rahbar.repository.RccCenterRepository;
import com.rahbar.repository.UserRepository;
import com.rahbar.security.Access;
import com.rahbar.security.StudentScope;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Coaching center students: ordinary student accounts tagged with their RCC center (users.rcc_center_id, and the
 * center's name on their application row). A coaching center lead (RCC scope) sees, adds and edits only their own
 * center's students; the office sees every center. Only personal and family details are kept here; course, bank,
 * sponsor and payments stay in the Student Directory.
 */
@Service
public class RccStudentService {

    private static final int STUDENT_ROLE = ServiceSupport.STUDENT_ROLE;

    private final UserRepository userRepository;
    private final GranteeDetailsRepository granteeDetailsRepository;
    private final RccCenterRepository rccCenterRepository;
    private final StudentScope studentScope;
    private final PasswordEncoder passwordEncoder;

    public RccStudentService(UserRepository userRepository, GranteeDetailsRepository granteeDetailsRepository,
                             RccCenterRepository rccCenterRepository, StudentScope studentScope, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.granteeDetailsRepository = granteeDetailsRepository;
        this.rccCenterRepository = rccCenterRepository;
        this.studentScope = studentScope;
        this.passwordEncoder = passwordEncoder;
    }

    /** One page of the students the signed-in user may see (optionally of one center, matching q), and the centers they may pick. */
    public Map<String, Object> list(Long centerId, String q, Integer page, Integer size) {
        StudentScope.Scope scope = studentScope.current();
        List<RccCenter> centers = centers(scope);
        Map<String, RccCenter> centerByName = new HashMap<>();
        centers.forEach(c -> centerByName.put(StudentScope.normalize(c.getCenterName()), c));

        Map<Long, GranteeDetails> applications = new HashMap<>();
        granteeDetailsRepository.findAll().stream()
                .filter(g -> g.getUserId() != null)
                .sorted(Comparator.comparing(GranteeDetails::getGranteeDetailId))
                .forEach(g -> applications.putIfAbsent(g.getUserId(), g));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (User u : userRepository.findByRoleId(STUDENT_ROLE)) {
            if (u.getMergedIntoId() != null || !scope.includes(u)) continue;
            GranteeDetails g = applications.get(u.getId());
            RccCenter center = centerOf(u, g, centerByName);
            if (center == null) continue; // not at a coaching center (or one this user can't see)
            if (centerId != null && !centerId.equals(center.getRccCenterId())) continue;
            Map<String, Object> r = row(u, g, center);
            if (!com.rahbar.util.Paging.matches(q, r.get("name"), r.get("user_id"), r.get("phone"), r.get("email"), r.get("father_name"))) continue;
            rows.add(r);
        }
        rows.sort(Comparator.comparing(r -> String.valueOf(r.get("name")), String.CASE_INSENSITIVE_ORDER));
        Map<String, Object> body = com.rahbar.util.Paging.of(rows, page, size);
        body.put("centers", centers.stream().map(RccStudentService::center).toList());
        body.put("scoped", !scope.all());
        return body;
    }

    /** One student's personal and family details (404 when not one of the user's coaching center students). */
    public Map<String, Object> get(Long id) {
        User u = requireRccStudent(id);
        GranteeDetails g = granteeDetailsRepository.findFirstByUserIdOrderByGranteeDetailIdAsc(id).orElse(null);
        return row(u, g, centerOf(u, g, centersByName(studentScope.current())));
    }

    /** Adds a student at a coaching center. Returns their name (they get a reference ID from the office later). */
    @Transactional
    public String create(Map<String, Object> body) {
        RccCenter center = requireCenter(body.get("rccCenterId"));
        User u = new User(); // no reference ID yet: the office assigns it after the student's application
        u.setRoleId(STUDENT_ROLE);
        u.setStatus("Active");
        u.setStudyStatus(StudyStatus.STUDYING);
        u.setPasswordHash(passwordEncoder.encode(PasswordPolicy.DEFAULT_PASSWORD));
        u.setMustChangePassword(true);
        save(u, null, center, body);
        return u.getName();
    }

    /** Updates a student's personal and family details (and, for the office, their center). */
    @Transactional
    public void update(Long id, Map<String, Object> body) {
        User u = requireRccStudent(id);
        GranteeDetails g = granteeDetailsRepository.findFirstByUserIdOrderByGranteeDetailIdAsc(id).orElse(null);
        RccCenter center = body.get("rccCenterId") != null ? requireCenter(body.get("rccCenterId"))
                : centerOf(u, g, centersByName(studentScope.current()));
        save(u, g, center, body);
    }

    private void save(User u, GranteeDetails g, RccCenter center, Map<String, Object> body) {
        String name = text(body.get("name"));
        if (name == null) throw new ApiException(HttpStatus.BAD_REQUEST, "Name is required.");
        String email = text(body.get("email"));
        String phone = text(body.get("phone"));
        if (email != null && (u.getId() == null ? userRepository.findByEmail(email).isPresent() : userRepository.existsByEmailAndIdNot(email, u.getId()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Another account already uses this email.");
        }
        if (phone != null && (u.getId() == null ? userRepository.findByPhone(phone).isPresent() : userRepository.existsByPhoneAndIdNot(phone, u.getId()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Another account already uses this phone number.");
        }
        String sex = text(body.get("sex"));
        if (body.get("refId") != null) ServiceSupport.setRefId(userRepository, u, body.get("refId")); // office only
        u.setName(name);
        u.setEmail(email);
        u.setPhone(phone);
        u.setSex("F".equalsIgnoreCase(sex) ? "F" : "M");
        u.setRccCenterId(center.getRccCenterId());
        Long id = userRepository.save(u).getId();

        if (g == null) {
            g = new GranteeDetails();
            g.setUserId(id);
        }
        g.setName(name);
        g.setStudentMobile(phone);
        g.setRccName(center.getCenterName());
        g.setAddress(text(body.get("address")));
        g.setFatherName(text(body.get("fatherName")));
        g.setMotherName(text(body.get("motherName")));
        g.setFatherProfession(text(body.get("fatherProfession")));
        g.setMotherProfession(text(body.get("motherProfession")));
        g.setFatherMobile(text(body.get("fatherMobile")));
        g.setMotherMobile(text(body.get("motherMobile")));
        g.setAverageAnnualSalary(income(body.get("averageAnnualSalary")));
        granteeDetailsRepository.save(g);
    }

    /** 404 unless this is a student at a coaching center the signed-in user may see. */
    private User requireRccStudent(Long id) {
        User u = studentScope.requireStudent(id);
        GranteeDetails g = granteeDetailsRepository.findFirstByUserIdOrderByGranteeDetailIdAsc(id).orElse(null);
        if (!UserRoles.has(u, STUDENT_ROLE, userRepository) || centerOf(u, g, centersByName(studentScope.current())) == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Student not found");
        }
        return u;
    }

    /** The center to save a student at: a coaching center lead always uses their own. */
    private RccCenter requireCenter(Object value) {
        StudentScope.Scope scope = studentScope.current();
        if (scope.none()) throw Access.forbidden("Your account is not linked to an RCC center. Ask the office to set it.");
        Long id = scope.rccCenterId() != null ? scope.rccCenterId() : toLong(value);
        if (id == null) throw new ApiException(HttpStatus.BAD_REQUEST, "Choose the RCC center.");
        RccCenter c = rccCenterRepository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Unknown RCC center."));
        if (!scope.all() && scope.rccCenterId() == null) throw Access.forbidden("Only the office or a coaching center lead can add these students.");
        return c;
    }

    /** The centers this user may see: their own (RCC scope), none (chapter scope), or all. */
    private List<RccCenter> centers(StudentScope.Scope scope) {
        if (scope.all()) {
            List<RccCenter> all = new ArrayList<>(rccCenterRepository.findAll());
            all.sort(Comparator.comparing(RccCenter::getCenterName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
            return all;
        }
        return scope.rccCenterId() == null ? List.of() : rccCenterRepository.findById(scope.rccCenterId()).map(List::of).orElse(List.of());
    }

    private Map<String, RccCenter> centersByName(StudentScope.Scope scope) {
        Map<String, RccCenter> byName = new HashMap<>();
        centers(scope).forEach(c -> byName.put(StudentScope.normalize(c.getCenterName()), c));
        return byName;
    }

    /** The student's center: users.rcc_center_id, else the RCC name on their application. */
    private static RccCenter centerOf(User u, GranteeDetails g, Map<String, RccCenter> centerByName) {
        if (u.getRccCenterId() != null) {
            for (RccCenter c : centerByName.values()) if (c.getRccCenterId().equals(u.getRccCenterId())) return c;
        }
        return g == null ? null : centerByName.get(StudentScope.normalize(g.getRccName()));
    }

    private static Map<String, Object> row(User u, GranteeDetails g, RccCenter c) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", u.getId());
        r.put("user_id", u.getUserId());
        r.put("name", u.getName());
        r.put("sex", u.getSex());
        r.put("email", u.getEmail());
        r.put("phone", u.getPhone());
        r.put("status", u.getStatus());
        r.put("study_status", StudyStatus.of(u.getStudyStatus()));
        r.put("rcc_center_id", c == null ? null : c.getRccCenterId());
        r.put("rcc_center_name", c == null ? null : c.getCenterName());
        r.put("address", g == null ? null : g.getAddress());
        r.put("father_name", g == null ? null : g.getFatherName());
        r.put("mother_name", g == null ? null : g.getMotherName());
        r.put("father_profession", g == null ? null : g.getFatherProfession());
        r.put("mother_profession", g == null ? null : g.getMotherProfession());
        r.put("father_mobile", g == null ? null : g.getFatherMobile());
        r.put("mother_mobile", g == null ? null : g.getMotherMobile());
        r.put("average_annual_salary", g == null ? null : g.getAverageAnnualSalary());
        return r;
    }

    private static Map<String, Object> center(RccCenter c) {
        return Map.of("rcc_center_id", c.getRccCenterId(), "center_name", c.getCenterName() == null ? "" : c.getCenterName());
    }

    private static String text(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static Long toLong(Object v) {
        String s = text(v);
        if (s == null) return null;
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal income(Object v) {
        String s = text(v);
        if (s == null) return null;
        try {
            BigDecimal b = new BigDecimal(s);
            if (b.signum() < 0) throw new ApiException(HttpStatus.BAD_REQUEST, "Average annual family income cannot be negative.");
            return b;
        } catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Average annual family income must be a number.");
        }
    }
}
