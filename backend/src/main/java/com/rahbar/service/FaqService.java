package com.rahbar.service;

import com.rahbar.entity.Faq;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.FaqRepository;
import com.rahbar.security.Access;
import com.rahbar.security.Section;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Help & FAQ. Everyone signed in reads the FAQs meant for them (by audience); roles with FAQS:EDIT add, change and
 * delete them, and choose which ones the biharanjuman.com FAQ page shows.
 */
@Service
public class FaqService {

    public static final Map<String, String> AUDIENCES = new LinkedHashMap<>();
    static {
        AUDIENCES.put("ALL", "Everyone");
        AUDIENCES.put("STAFF", "Staff (admin area)");
        AUDIENCES.put("SPONSORS", "Sponsors");
        AUDIENCES.put("STUDENTS", "Students");
    }
    /** Coordinators, convenors and sponsors read the sponsor questions; students the student ones; other roles are staff. */
    private static final Set<Integer> SPONSOR_ROLES = Set.of(3, 4, 5);
    private static final int STUDENT_ROLE = 6;

    private final FaqRepository faqs;

    public FaqService(FaqRepository faqs) {
        this.faqs = faqs;
    }

    static boolean editor() {
        return Access.can(Section.FAQS, Section.Level.EDIT);
    }

    /**
     * The FAQs for the signed-in user: editors see all (also hidden ones) unless asRole is given; others the active
     * ones for their roles. asRole (a portal's role, e.g. 5 in the Sponsor portal) shows what that role reads.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> list(Integer asRole) {
        Set<Integer> roles = Access.current().getRoleIds();
        if (asRole != null) {
            if (!roles.contains(asRole)) throw Access.forbidden("You don't have that role.");
            roles = Set.of(asRole);
        }
        boolean editor = asRole == null && editor();
        Set<String> audiences = audiencesOf(roles);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Faq f : faqs.findAllByOrderByDisplayOrderAscIdAsc()) {
            if (!editor && (!f.isActive() || !audiences.contains(f.getAudience()))) continue;
            rows.add(row(f, editor));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("faqs", rows);
        body.put("editor", editor);
        body.put("audiences", AUDIENCES);
        return body;
    }

    /** ALL plus STAFF / SPONSORS / STUDENTS for the user's roles. */
    static Set<String> audiencesOf(Set<Integer> roles) {
        Set<String> out = new HashSet<>(Set.of("ALL"));
        for (Integer r : roles) {
            if (r == STUDENT_ROLE) out.add("STUDENTS");
            else if (SPONSOR_ROLES.contains(r)) out.add("SPONSORS");
            else out.add("STAFF");
        }
        return out;
    }

    /** biharanjuman.com: active FAQs switched on for the website, without anything internal. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> website() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Faq f : faqs.findByActiveTrueAndOnWebsiteTrueOrderByDisplayOrderAscIdAsc()) {
            rows.add(Map.of("id", f.getId(), "category", f.getCategory(), "question", f.getQuestion(), "answer", f.getAnswer()));
        }
        return rows;
    }

    /** Body: { category, audience, question, answer, onWebsite, active, displayOrder }. */
    @Transactional
    public Faq save(Long id, Map<String, Object> body) {
        Faq f = id == null ? new Faq() : faqs.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FAQ not found."));
        f.setCategory(required(body.get("category"), "Category", 80));
        String audience = ServiceSupport.str(body.get("audience"));
        f.setAudience(audience == null || !AUDIENCES.containsKey(audience) ? "ALL" : audience);
        f.setQuestion(required(body.get("question"), "Question", 300));
        f.setAnswer(required(body.get("answer"), "Answer", 10_000));
        f.setOnWebsite(flag(body.get("onWebsite"), false));
        f.setActive(flag(body.get("active"), true));
        Object order = body.get("displayOrder");
        if (order != null && !String.valueOf(order).isBlank()) {
            try {
                f.setDisplayOrder(Integer.parseInt(String.valueOf(order).trim()));
            } catch (NumberFormatException e) {
                throw bad("Order must be a whole number.");
            }
        } else if (id == null) {
            f.setDisplayOrder(faqs.findAllByOrderByDisplayOrderAscIdAsc().stream().mapToInt(Faq::getDisplayOrder).max().orElse(0) + 10);
        }
        return faqs.save(f);
    }

    @Transactional
    public void delete(Long id) {
        faqs.delete(faqs.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FAQ not found.")));
    }

    private static Map<String, Object> row(Faq f, boolean editor) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", f.getId());
        r.put("category", f.getCategory());
        r.put("question", f.getQuestion());
        r.put("answer", f.getAnswer());
        if (editor) {
            r.put("audience", f.getAudience());
            r.put("on_website", f.isOnWebsite());
            r.put("active", f.isActive());
            r.put("display_order", f.getDisplayOrder());
            r.put("updated_at", f.getUpdatedAt());
        }
        return r;
    }

    private static String required(Object v, String label, int max) {
        String s = ServiceSupport.str(v);
        s = s == null ? "" : s.strip();
        if (s.isEmpty()) throw bad(label + " is required.");
        if (s.length() > max) throw bad(label + " can be at most " + max + " characters.");
        return s;
    }

    private static boolean flag(Object v, boolean fallback) {
        return v == null ? fallback : Boolean.parseBoolean(String.valueOf(v));
    }

    private static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
