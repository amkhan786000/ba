package com.rahbar.service;

import com.rahbar.entity.GranteeDetails;
import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.GranteeDetailsRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * What kind of student a sponsor would like to sponsor (Edit Sponsor, and Apply for Sponsorship): a college type,
 * fathers' professions (from the students' applications) and a yearly family income range (within the students'
 * lowest and highest).
 */
@Service
public class SponsorPreferenceService {

    /** Read from a request: GOVT / PRIVATE / ANY (or null), the professions and the income range. */
    public record Preferences(String collegeType, List<String> fatherProfessions, BigDecimal incomeMin, BigDecimal incomeMax) {}

    static final int MAX_PROFESSIONS = 50, MAX_PROFESSION_LENGTH = 100;

    private final GranteeDetailsRepository granteeDetailsRepository;

    public SponsorPreferenceService(GranteeDetailsRepository granteeDetailsRepository) {
        this.granteeDetailsRepository = granteeDetailsRepository;
    }

    /** The choices: fathers' professions the students gave (A-Z, each once) and their lowest and highest family income. */
    public Map<String, Object> options() {
        Map<String, String> professions = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        BigDecimal min = null, max = null;
        for (GranteeDetails g : granteeDetailsRepository.findAll()) {
            String p = clean(g.getFatherProfession());
            if (p != null) professions.putIfAbsent(p, p);
            BigDecimal income = g.getAverageAnnualSalary();
            if (income != null && income.signum() > 0) {
                min = min == null || income.compareTo(min) < 0 ? income : min;
                max = max == null || income.compareTo(max) > 0 ? income : max;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("father_professions", new ArrayList<>(professions.values()));
        out.put("income_min", min);
        out.put("income_max", max);
        return out;
    }

    /** Reads { collegeType, fatherProfessions: [...], incomeMin, incomeMax } (each may be empty). 400 when invalid. */
    public static Preferences read(Map<String, Object> body) {
        String type = body.get("collegeType") == null ? "" : String.valueOf(body.get("collegeType")).trim().toUpperCase(Locale.ROOT);
        if (!type.isEmpty() && !PaymentInstallmentService.COLLEGE_TYPES.contains(type)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "College type must be Govt, Private or Doesn't matter.");
        }
        Map<String, String> professions = new LinkedHashMap<>();
        Object raw = body.get("fatherProfessions");
        Collection<?> items = raw instanceof Collection<?> c ? c : raw == null ? List.of() : List.of(String.valueOf(raw).split("\n"));
        for (Object item : items) {
            String p = clean(item == null ? null : String.valueOf(item));
            if (p == null) continue;
            if (p.length() > MAX_PROFESSION_LENGTH) throw new ApiException(HttpStatus.BAD_REQUEST, "A father's profession can be at most " + MAX_PROFESSION_LENGTH + " characters.");
            professions.putIfAbsent(p.toLowerCase(Locale.ROOT), p);
        }
        if (professions.size() > MAX_PROFESSIONS) throw new ApiException(HttpStatus.BAD_REQUEST, "Choose at most " + MAX_PROFESSIONS + " professions.");
        BigDecimal min = income(body.get("incomeMin"), "Minimum income"), max = income(body.get("incomeMax"), "Maximum income");
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The minimum income can't be more than the maximum.");
        }
        return new Preferences(type.isEmpty() ? null : type, new ArrayList<>(professions.values()), min, max);
    }

    /** Sets the preferences on the sponsor (not saved here). */
    public static void apply(User sponsor, Preferences p) {
        sponsor.setPrefCollegeType(p.collegeType());
        sponsor.setPrefFatherProfessions(join(p.fatherProfessions()));
        sponsor.setPrefIncomeMin(p.incomeMin());
        sponsor.setPrefIncomeMax(p.incomeMax());
    }

    /** The sponsor's preferences for a page: pref_college_type, pref_father_professions (a list), pref_income_min/max. */
    public static Map<String, Object> of(User sponsor) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pref_college_type", sponsor.getPrefCollegeType());
        out.put("pref_father_professions", split(sponsor.getPrefFatherProfessions()));
        out.put("pref_income_min", sponsor.getPrefIncomeMin());
        out.put("pref_income_max", sponsor.getPrefIncomeMax());
        return out;
    }

    /** Stored one per line. */
    static String join(List<String> professions) {
        return professions == null || professions.isEmpty() ? null : String.join("\n", professions);
    }

    static List<String> split(String stored) {
        if (stored == null || stored.isBlank()) return List.of();
        return Arrays.stream(stored.split("\n")).map(SponsorPreferenceService::clean).filter(Objects::nonNull).toList();
    }

    /** "Govt college, Farmer, Driver, income 50,000 to 2,00,000" for lists; "Any" when nothing is chosen. */
    static String summary(String collegeType, List<String> professions, BigDecimal min, BigDecimal max) {
        List<String> parts = new ArrayList<>();
        if (collegeType != null) parts.add(PaymentInstallmentService.collegeLabel(collegeType) + " college");
        if (professions != null && !professions.isEmpty()) parts.add("father: " + String.join(" / ", professions));
        if (min != null && max != null) parts.add("income " + plain(min) + " to " + plain(max));
        else if (min != null) parts.add("income from " + plain(min));
        else if (max != null) parts.add("income up to " + plain(max));
        return parts.isEmpty() ? "Any student" : String.join("; ", parts);
    }

    private static String plain(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    private static String clean(String s) {
        if (s == null) return null;
        String t = s.trim().replaceAll("\\s+", " ");
        return t.isEmpty() ? null : t;
    }

    private static BigDecimal income(Object v, String label) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        try {
            BigDecimal d = new BigDecimal(String.valueOf(v).trim().replace(",", ""));
            if (d.signum() < 0) throw new ApiException(HttpStatus.BAD_REQUEST, label + " can't be negative.");
            return d;
        } catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + " must be a number.");
        }
    }
}
