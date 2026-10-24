package com.rahbar.service;

import com.rahbar.entity.GranteeDetails;
import com.rahbar.entity.User;

import java.math.BigDecimal;
import java.util.*;

/**
 * Does a student fit what a sponsor would like (college type, father's profession, family income)? A preference
 * the sponsor didn't give is no condition; a student whose college type is "Doesn't matter" or not set fits either
 * type; a student without a known profession or income doesn't fit a preference about it.
 */
public final class SponsorMatch {

    private SponsorMatch() {}

    /** What a sponsor (or their application) would like. */
    public record Wish(String collegeType, List<String> fatherProfessions, BigDecimal incomeMin, BigDecimal incomeMax) {
        public boolean any() {
            return (collegeType != null && !"ANY".equals(collegeType)) || (fatherProfessions != null && !fatherProfessions.isEmpty())
                    || incomeMin != null || incomeMax != null;
        }

        static Wish of(User sponsor) {
            return new Wish(sponsor.getPrefCollegeType(), SponsorPreferenceService.split(sponsor.getPrefFatherProfessions()),
                    sponsor.getPrefIncomeMin(), sponsor.getPrefIncomeMax());
        }
    }

    /** fits: every preference is met; why: the ones met; missing: the ones not met. */
    public record Fit(boolean fits, List<String> why, List<String> missing) {
        Map<String, Object> toMap() {
            return Map.of("fits", fits, "why", why, "missing", missing);
        }
    }

    public static Fit fit(Wish w, User student, GranteeDetails family) {
        List<String> why = new ArrayList<>(), missing = new ArrayList<>();
        if (w.collegeType() != null && !"ANY".equals(w.collegeType())) {
            String t = student.getCollegeType();
            String wanted = PaymentInstallmentService.collegeLabel(w.collegeType()) + " college";
            if (t == null || "ANY".equals(t) || w.collegeType().equals(t)) why.add(wanted); else missing.add(wanted);
        }
        if (w.fatherProfessions() != null && !w.fatherProfessions().isEmpty()) {
            String p = family == null ? null : family.getFatherProfession();
            boolean ok = p != null && w.fatherProfessions().stream().anyMatch(x -> x.equalsIgnoreCase(p.trim()));
            if (ok) why.add("father: " + p.trim()); else missing.add(p == null || p.isBlank() ? "father's profession not known" : "father: " + p.trim());
        }
        if (w.incomeMin() != null || w.incomeMax() != null) {
            BigDecimal income = family == null ? null : family.getAverageAnnualSalary();
            if (income == null) {
                missing.add("family income not known");
            } else if ((w.incomeMin() == null || income.compareTo(w.incomeMin()) >= 0) && (w.incomeMax() == null || income.compareTo(w.incomeMax()) <= 0)) {
                why.add("income in range");
            } else {
                missing.add("income out of range");
            }
        }
        return new Fit(missing.isEmpty(), why, missing);
    }
}
