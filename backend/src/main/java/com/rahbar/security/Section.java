package com.rahbar.security;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Admin screens that permissions are granted on. A role holds keys such as "RCC_CENTERS:VIEW" and
 * "RCC_CENTERS:EDIT" (manage = add / edit / delete); EDIT implies VIEW. The Super Admin always has every key.
 */
public enum Section {
    DASHBOARD("Dashboard"),
    USERS("Users"),
    ROLES("Roles & permissions"),
    CHAPTERS("Chapters"),
    RCC_CENTERS("RCC centers"),
    COURSES("Courses & institutions"),
    PAYMENT_CONFIG("Payment config"),
    PAYMENT_DUES("Payment dues"),
    REPORTS("Reports"),
    APPLICATION_PERIOD("Application period"),
    APPLICATIONS("Applications"),
    SPONSORSHIPS("Sponsorships"),
    STUDENTS("Student directory"),
    ACTIVITY("Activity log"),
    MESSAGES("Broadcast messages"),
    /**
     * Sponsors' contact and personal details (everyone may see a sponsor's name and user ID).
     * VIEW shows them; EDIT also allows editing a sponsor's profile.
     */
    SPONSOR_DETAILS("Sponsor details (contact & personal)"),
    PROGRESS_DUE_DATES("Progress report due dates"),
    /** Per-chapter overview; with "own chapter" scope a user sees only their chapter. */
    CHAPTER_DASHBOARD("Chapter dashboard"),
    DATA_QUALITY("Data quality"),
    /**
     * Installments between sponsors and students (Admin > Payment Records). EDIT also records payments against them.
     * With "own chapter" scope a user sees only their chapter's students. Sponsors and students always see their own.
     */
    PAYMENT_RECORDS("Payment records (sponsor-student installments)"),
    /** Every email the application sent, with its text (codes and passwords hidden). */
    EMAIL_LOG("Email log"),
    /** The wording of every kind of email. EDIT adds / changes templates; only the Super Admin can delete one. */
    EMAIL_TEMPLATES("Email templates"),
    /** Graduated students and what they do now. */
    ALUMNI("Alumni");

    public enum Level { VIEW, EDIT }

    private final String label;

    Section(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Permission key as stored on a role, e.g. "USERS:EDIT". */
    public String key(Level level) {
        return name() + ":" + level.name();
    }

    /** Every key (VIEW and EDIT of every section): what the Super Admin has. */
    public static Set<String> allKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (Section s : values()) {
            keys.add(s.key(Level.VIEW));
            keys.add(s.key(Level.EDIT));
        }
        return keys;
    }

    /** Keeps only known keys and adds VIEW wherever EDIT is granted. */
    public static Set<String> normalize(Iterable<String> keys) {
        Set<String> known = allKeys();
        Set<String> result = new LinkedHashSet<>();
        for (String k : keys) {
            String key = k == null ? "" : k.trim().toUpperCase();
            if (!known.contains(key)) continue;
            result.add(key);
            if (key.endsWith(":EDIT")) result.add(key.substring(0, key.length() - 4) + "VIEW");
        }
        return result;
    }
}
