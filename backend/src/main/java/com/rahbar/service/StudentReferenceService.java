package com.rahbar.service;

import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Students > Reference Numbers (Super Admin and Application Admin). A student's reference number is their user ID
 * (users.user_id). This gives every student of the current session year whose user ID isn't a reference number of
 * that year yet (e.g. the numeric code they got when their application was accepted) one: Govt college: M001-2026,
 * M002-2026, ...; Private, "Doesn't matter" or not set: PVT001-2026, ... Each prefix counts on from the highest
 * number it already has that year, oldest student first. Signing in uses the email or phone, so it keeps working.
 */
@Service
public class StudentReferenceService {

    static final String GOVT_PREFIX = "M", PRIVATE_PREFIX = "PVT";

    private final UserRepository userRepository;

    public StudentReferenceService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** M for a Govt college; everything else (Private, Doesn't matter, not set) counts as private. */
    static String prefixFor(String collegeType) {
        return "GOVT".equalsIgnoreCase(collegeType == null ? null : collegeType.trim()) ? GOVT_PREFIX : PRIVATE_PREFIX;
    }

    static String format(String prefix, int number, int year) {
        return String.format(Locale.ROOT, "%s%03d-%d", prefix, number, year);
    }

    /** True when the user ID already is a reference number of that year (M001-2026, PVT012-2026). */
    static boolean isReference(String userId, int year) {
        return numberOf(userId, GOVT_PREFIX, year) > 0 || numberOf(userId, PRIVATE_PREFIX, year) > 0;
    }

    /** The number in a reference of this prefix and year (M007-2026 gives 7), or 0 when it is another kind. */
    static int numberOf(String reference, String prefix, int year) {
        if (reference == null) return 0;
        Matcher m = Pattern.compile("^" + prefix + "(\\d+)-" + year + "$").matcher(reference.trim().toUpperCase(Locale.ROOT));
        return m.matches() ? Integer.parseInt(m.group(1)) : 0;
    }

    /** The year references can be generated for: always the current year. */
    static int currentYear() {
        return LocalDate.now().getYear();
    }

    /** Students who get a number: active, not merged, of that session year. */
    private List<User> studentsOf(int year) {
        return userRepository.findByRoleId(ServiceSupport.STUDENT_ROLE).stream()
                .filter(u -> u.getMergedIntoId() == null && !"Inactive".equalsIgnoreCase(u.getStatus()))
                .filter(u -> Integer.valueOf(year).equals(u.getYear()))
                .sorted(Comparator.comparing(User::getId))
                .toList();
    }

    private static boolean hasReference(User u) {
        return u.getYear() != null && isReference(u.getUserId(), u.getYear());
    }

    /** The page: the year, how many are still without a number (Govt / Private), and the numbers given so far. */
    public Map<String, Object> overview() {
        int year = currentYear();
        List<User> students = studentsOf(year);
        long govt = students.stream().filter(u -> !hasReference(u) && GOVT_PREFIX.equals(prefixFor(u.getCollegeType()))).count();
        long priv = students.stream().filter(u -> !hasReference(u) && PRIVATE_PREFIX.equals(prefixFor(u.getCollegeType()))).count();
        List<Map<String, Object>> numbered = new ArrayList<>();
        students.stream().filter(StudentReferenceService::hasReference)
                .sorted(Comparator.comparing(User::getUserId))
                .forEach(u -> numbered.add(row(u)));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("year", year);
        out.put("years", List.of(year));
        out.put("students", students.size());
        out.put("without_govt", govt);
        out.put("without_private", priv);
        out.put("numbered", numbered);
        return out;
    }

    /** Numbers every student of the year who has none; returns the overview plus the ones just numbered. */
    @Transactional
    public synchronized Map<String, Object> generate(Integer year) {
        int current = currentYear();
        if (year == null || year != current) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Reference numbers can only be generated for " + current + ".");
        }
        Map<String, Integer> last = new HashMap<>();
        for (User u : userRepository.findAll()) {
            for (String prefix : List.of(GOVT_PREFIX, PRIVATE_PREFIX)) {
                last.merge(prefix, numberOf(u.getUserId(), prefix, year), Math::max);
            }
        }
        List<Map<String, Object>> generated = new ArrayList<>();
        for (User u : studentsOf(year)) {
            if (hasReference(u)) continue;
            String prefix = prefixFor(u.getCollegeType());
            String reference;
            do { // skip a number some other account already has
                reference = format(prefix, last.merge(prefix, 1, Integer::sum), year);
            } while (userRepository.existsByUserId(reference));
            String previous = u.getUserId();
            u.setUserId(reference);
            userRepository.save(u);
            Map<String, Object> r = row(u);
            r.put("previous_user_id", previous);
            generated.add(r);
        }
        Map<String, Object> out = overview();
        out.put("generated", generated);
        return out;
    }

    private static Map<String, Object> row(User u) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", u.getId());
        r.put("user_id", u.getUserId());
        r.put("name", u.getName());
        r.put("chapter", u.getChapterName());
        r.put("college_type", u.getCollegeType() == null ? "Not set" : PaymentInstallmentService.collegeLabel(u.getCollegeType()));
        return r;
    }
}
