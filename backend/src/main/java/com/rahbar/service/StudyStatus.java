package com.rahbar.service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Where a student is in their studies; separate from the account's Active / Inactive (which controls sign-in).
 * Graduated and dropped-out students drop out of payment dues and reminders; students on hold keep their dues
 * but get no reminders.
 */
public final class StudyStatus {
    public static final String STUDYING = "STUDYING";
    public static final String ON_HOLD = "ON_HOLD";
    public static final String GRADUATED = "GRADUATED";
    public static final String DROPPED_OUT = "DROPPED_OUT";

    /** Code -> label, in display order. */
    public static final Map<String, String> LABELS = new LinkedHashMap<>();
    static {
        LABELS.put(STUDYING, "Studying");
        LABELS.put(ON_HOLD, "On hold");
        LABELS.put(GRADUATED, "Graduated");
        LABELS.put(DROPPED_OUT, "Dropped out");
    }

    private StudyStatus() {}

    /** NULL (older rows) counts as studying. */
    public static String of(String status) {
        return status == null || status.isBlank() ? STUDYING : status;
    }

    /** Still part of the programme: payment dues apply. */
    public static boolean inProgramme(String status) {
        String s = of(status);
        return STUDYING.equals(s) || ON_HOLD.equals(s);
    }

    /** Payments can be recorded or paid only while the student is studying (not on hold, graduated or dropped out). */
    public static boolean takesPayments(String status) {
        return STUDYING.equals(of(status));
    }

    /** Gets payment / progress / document reminders. */
    public static boolean getsReminders(String status) {
        return STUDYING.equals(of(status));
    }

    public static String label(String status) {
        return LABELS.getOrDefault(of(status), of(status));
    }

    /** [{ code, label }] in display order, for the screens (the one list of study statuses). */
    public static java.util.List<Map<String, String>> options() {
        return LABELS.entrySet().stream().map(e -> Map.of("code", e.getKey(), "label", e.getValue())).toList();
    }
}
