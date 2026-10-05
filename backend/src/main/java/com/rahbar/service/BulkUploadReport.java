package com.rahbar.service;

import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Row-by-row outcome of a CSV bulk upload, shown to the admin after the upload. */
public class BulkUploadReport {

    private int totalRows;
    private int created;
    private int updated;
    private int skipped;
    private int mappings;
    private final List<Map<String, Object>> problems = new ArrayList<>();

    public void row() { totalRows++; }
    public void created() { created++; }
    public void updated() { updated++; }
    public void mapped() { mappings++; }

    /** A row that was left out on purpose (e.g. an empty line). */
    public void skipped(int rowNumber, String reference, String reason) {
        skipped++;
        problem(rowNumber, reference, reason, "skipped");
    }

    /** A row that could not be saved. */
    public void failed(int rowNumber, String reference, Exception e) {
        problem(rowNumber, reference, reason(e), "failed");
    }

    /** A row that was saved but with a warning (e.g. an unknown student in "Student Assigned"). */
    public void warning(int rowNumber, String reference, String reason) {
        problem(rowNumber, reference, reason, "warning");
    }

    public int processed() { return created + updated; }

    public int failedCount() {
        return (int) problems.stream().filter(p -> "failed".equals(p.get("type"))).count();
    }

    private void problem(int rowNumber, String reference, String reason, String type) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("row", rowNumber);
        p.put("reference", reference);
        p.put("type", type);
        p.put("reason", reason);
        problems.add(p);
    }

    /** Turns database errors into something an admin can act on. */
    private static String reason(Exception e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String msg = root.getMessage() == null ? e.getClass().getSimpleName() : root.getMessage();
        if (e instanceof DataIntegrityViolationException || msg.contains("Duplicate entry")) {
            if (msg.contains("email")) return "The email address is already used by another account.";
            if (msg.contains("phone")) return "The phone number is already used by another account.";
            if (msg.contains("cannot be null") || msg.contains("doesn't have a default")) return "A required value is missing: " + msg;
            return "Duplicate or invalid value: " + msg;
        }
        return msg;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalRows", totalRows);
        m.put("created", created);
        m.put("updated", updated);
        m.put("skipped", skipped);
        m.put("failed", failedCount());
        m.put("mappings", mappings);
        m.put("problems", problems);
        return m;
    }
}
