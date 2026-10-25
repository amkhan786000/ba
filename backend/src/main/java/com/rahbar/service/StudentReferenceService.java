package com.rahbar.service;

import com.rahbar.entity.ReferenceBatch;
import com.rahbar.entity.ReferenceCounter;
import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.ReferenceBatchRepository;
import com.rahbar.repository.ReferenceCounterRepository;
import com.rahbar.repository.UserRepository;
import com.rahbar.util.Rows;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Students > Reference Numbers (Super Admin and Application Admin). A student's reference number is their user ID
 * (users.user_id). This gives students of a session year (the current one or a later one) whose user ID isn't a
 * reference number of that year yet (e.g. the numeric code they got when their application was accepted) one: Govt
 * college: M001-2026, M002-2026, ...; Private, "Doesn't matter" or not set: PVT001-2026, ...
 * <p>
 * It can be done all at once or chapter by chapter (e.g. Patna gets PVT001 to PVT040, then Gaya carries on from
 * PVT041): the last number given per year and prefix is stored (reference_counters), so a number is never given twice,
 * and each batch is kept (reference_batches). Oldest student first. Signing in uses the email or phone, so it keeps
 * working.
 */
@Service
public class StudentReferenceService {

    static final String GOVT_PREFIX = "M", PRIVATE_PREFIX = "PVT";
    static final List<String> PREFIXES = List.of(GOVT_PREFIX, PRIVATE_PREFIX);

    /** The chapter filter for students who have no chapter. */
    public static final long NO_CHAPTER = 0L;

    private final UserRepository userRepository;
    private final ReferenceCounterRepository counters;
    private final ReferenceBatchRepository batches;

    public StudentReferenceService(UserRepository userRepository, ReferenceCounterRepository counters, ReferenceBatchRepository batches) {
        this.userRepository = userRepository;
        this.counters = counters;
        this.batches = batches;
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

    /** The first year references are given for: the current one (earlier years keep the IDs they were given). */
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

    /** The chapter filter a student falls under: their chapter, or NO_CHAPTER. */
    private static long chapterKey(User u) {
        return u.getChapterId() == null ? NO_CHAPTER : u.getChapterId();
    }

    /** The session years to choose from: the current one and any later one that has students, each with how many are left. */
    private List<Map<String, Object>> years() {
        int from = currentYear();
        TreeMap<Integer, long[]> byYear = new TreeMap<>();
        byYear.put(from, new long[2]);
        for (User u : userRepository.findByRoleId(ServiceSupport.STUDENT_ROLE)) {
            if (u.getYear() == null || u.getYear() < from || u.getMergedIntoId() != null || "Inactive".equalsIgnoreCase(u.getStatus())) continue;
            long[] n = byYear.computeIfAbsent(u.getYear(), k -> new long[2]);
            n[0]++;
            if (!hasReference(u)) n[1]++;
        }
        List<Map<String, Object>> out = new ArrayList<>();
        byYear.forEach((y, n) -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("year", y);
            r.put("students", n[0]);
            r.put("without", n[1]);
            out.add(r);
        });
        return out;
    }

    /** The highest number of this prefix and year given so far: the stored counter or a user ID, whichever is higher. */
    private int lastNumber(String prefix, int year, Collection<User> everyone) {
        int last = counters.findBySessionYearAndPrefix(year, prefix).map(ReferenceCounter::getLastNumber).orElse(0);
        for (User u : everyone) last = Math.max(last, numberOf(u.getUserId(), prefix, year));
        return last;
    }

    public Map<String, Object> overview() {
        return overview(null);
    }

    /**
     * The page for a session year (default: the current one): the years to choose from (with how many are left), the
     * chapters with how many are still without a number (Govt / Private), the next numbers, the batches so far and
     * the numbers given.
     */
    public Map<String, Object> overview(Integer year) {
        int y = year == null ? currentYear() : year;
        List<User> students = studentsOf(y);
        Map<Long, Map<String, Object>> chapters = new TreeMap<>();
        for (User u : students) {
            Map<String, Object> c = chapters.computeIfAbsent(chapterKey(u), k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("chapter_id", k);
                m.put("chapter", k == NO_CHAPTER ? "No chapter" : u.getChapterName());
                m.put("students", 0L);
                m.put("without_govt", 0L);
                m.put("without_private", 0L);
                return m;
            });
            c.merge("students", 1L, (a, b) -> (Long) a + (Long) b);
            if (!hasReference(u)) c.merge(GOVT_PREFIX.equals(prefixFor(u.getCollegeType())) ? "without_govt" : "without_private", 1L, (a, b) -> (Long) a + (Long) b);
        }
        List<Map<String, Object>> chapterRows = new ArrayList<>(chapters.values());
        chapterRows.sort(Comparator.comparing((Map<String, Object> c) -> NO_CHAPTER == (Long) c.get("chapter_id"))
                .thenComparing(c -> String.valueOf(c.get("chapter")), String.CASE_INSENSITIVE_ORDER));
        List<User> everyone = userRepository.findAll();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("year", y);
        out.put("years", years());
        out.put("students", students.size());
        out.put("without_govt", students.stream().filter(u -> !hasReference(u) && GOVT_PREFIX.equals(prefixFor(u.getCollegeType()))).count());
        out.put("without_private", students.stream().filter(u -> !hasReference(u) && PRIVATE_PREFIX.equals(prefixFor(u.getCollegeType()))).count());
        out.put("chapters", chapterRows);
        out.put("next_govt", format(GOVT_PREFIX, lastNumber(GOVT_PREFIX, y, everyone) + 1, y));
        out.put("next_private", format(PRIVATE_PREFIX, lastNumber(PRIVATE_PREFIX, y, everyone) + 1, y));
        List<Map<String, Object>> batchRows = new ArrayList<>();
        for (ReferenceBatch b : batches.findBySessionYearOrderByIdDesc(y)) {
            Map<String, Object> r = Rows.of(b);
            r.put("chapter", b.isAllChapters() ? "All chapters" : b.getChapterName() == null ? "No chapter" : b.getChapterName());
            r.put("first", format(b.getPrefix(), b.getFirstNumber(), y));
            r.put("last", format(b.getPrefix(), b.getLastNumber(), y));
            batchRows.add(r);
        }
        out.put("batches", batchRows);
        List<Map<String, Object>> numbered = new ArrayList<>();
        students.stream().filter(StudentReferenceService::hasReference)
                .sorted(Comparator.comparing(User::getUserId))
                .forEach(u -> numbered.add(row(u)));
        out.put("numbered", numbered);
        return out;
    }

    public Map<String, Object> generate(Integer year) {
        return generate(year, null);
    }

    /**
     * Numbers the students of the year who have none (chapterId: only that chapter's; NO_CHAPTER: those without a
     * chapter; null: all), carrying on from the last number given; stores the new last numbers and the batch.
     * Returns the overview plus the ones just numbered.
     */
    @Transactional
    public synchronized Map<String, Object> generate(Integer year, Long chapterId) {
        int current = currentYear();
        if (year == null || year < current) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Reference numbers can only be generated for " + current + " or a later session year.");
        }
        List<User> waiting = studentsOf(year).stream().filter(u -> !hasReference(u))
                .filter(u -> chapterId == null || chapterKey(u) == chapterId).toList();
        if (waiting.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, chapterId == null
                    ? "All reference numbers of " + year + " have been generated."
                    : "Every student of this chapter in " + year + " already has a reference number.");
        }
        List<User> everyone = userRepository.findAll();
        Map<String, Integer> last = new HashMap<>();
        Map<String, int[]> range = new LinkedHashMap<>(); // prefix -> first, last, how many
        for (String prefix : PREFIXES) last.put(prefix, lastNumber(prefix, year, everyone));
        List<Map<String, Object>> generated = new ArrayList<>();
        for (User u : waiting) {
            String prefix = prefixFor(u.getCollegeType());
            int n;
            String reference;
            do { // skip a number some other account already has
                n = last.merge(prefix, 1, Integer::sum);
                reference = format(prefix, n, year);
            } while (userRepository.existsByUserId(reference));
            final int number = n;
            range.compute(prefix, (k, r) -> r == null ? new int[]{number, number, 1} : new int[]{r[0], number, r[2] + 1});
            String previous = u.getUserId();
            u.setUserId(reference);
            userRepository.save(u);
            Map<String, Object> r = row(u);
            r.put("previous_user_id", previous);
            generated.add(r);
        }
        String chapterName = chapterId == null || chapterId == NO_CHAPTER ? null : waiting.get(0).getChapterName();
        range.forEach((prefix, r) -> {
            ReferenceCounter c = counters.findBySessionYearAndPrefix(year, prefix).orElseGet(() -> {
                ReferenceCounter fresh = new ReferenceCounter();
                fresh.setSessionYear(year);
                fresh.setPrefix(prefix);
                return fresh;
            });
            c.setLastNumber(last.get(prefix));
            counters.save(c);
            ReferenceBatch b = new ReferenceBatch();
            b.setSessionYear(year);
            b.setAllChapters(chapterId == null);
            b.setChapterId(chapterId == null || chapterId == NO_CHAPTER ? null : chapterId);
            b.setChapterName(chapterName);
            b.setPrefix(prefix);
            b.setFirstNumber(r[0]);
            b.setLastNumber(r[1]);
            b.setHowMany(r[2]);
            batches.save(b);
        });
        Map<String, Object> out = overview(year);
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
