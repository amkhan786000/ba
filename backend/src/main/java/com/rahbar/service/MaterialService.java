package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.repository.*;
import com.rahbar.security.Access;
import com.rahbar.security.Section;
import com.rahbar.security.StudentScope;
import com.rahbar.util.Rows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

import static com.rahbar.service.FormInput.*;

/**
 * Community > Materials: items (books, uniforms, devices, bicycles...), stock received from donors, and hand-outs to
 * scholarship students, coaching students and Rahbar-e-Banaat girls. Stock is shared by the whole organisation;
 * stock left = received - handed out, and can't go below zero. Chapter leads hand out to their chapter's students,
 * coaching coordinators to their centre's; REB girls need REB students access.
 */
@Service
public class MaterialService {

    public static final String SCHOLAR = "SCHOLAR", COACHING = "COACHING", REB = "REB";
    static final Set<String> CATEGORIES = Set.of("BOOK", "UNIFORM", "STATIONERY", "DEVICE", "BICYCLE", "OTHER");

    private final MaterialItemRepository items;
    private final MaterialReceiptRepository receipts;
    private final MaterialDistributionRepository distributions;
    private final UserRepository users;
    private final CoachingStudentRepository coachingStudents;
    private final RebStudentRepository rebStudents;
    private final StudentScope studentScope;
    private final CoachingAccess coachingAccess;

    public MaterialService(MaterialItemRepository items, MaterialReceiptRepository receipts, MaterialDistributionRepository distributions,
                           UserRepository users, CoachingStudentRepository coachingStudents, RebStudentRepository rebStudents,
                           StudentScope studentScope, CoachingAccess coachingAccess) {
        this.items = items;
        this.receipts = receipts;
        this.distributions = distributions;
        this.users = users;
        this.coachingStudents = coachingStudents;
        this.rebStudents = rebStudents;
        this.studentScope = studentScope;
        this.coachingAccess = coachingAccess;
    }

    // ================================================================ items and stock

    /** Every item with how many were received, handed out and are left. */
    public List<Map<String, Object>> items() {
        Map<Long, long[]> counts = counts();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MaterialItem i : items.findAllByOrderByNameAsc()) {
            long[] c = counts.getOrDefault(i.getId(), new long[2]);
            Map<String, Object> r = Rows.of(i);
            r.put("received", c[0]);
            r.put("given", c[1]);
            r.put("in_stock", c[0] - c[1]);
            rows.add(r);
        }
        return rows;
    }

    /** item id -> [received, handed out]. */
    private Map<Long, long[]> counts() {
        Map<Long, long[]> out = new HashMap<>();
        receipts.findAll().forEach(r -> out.computeIfAbsent(r.getItemId(), k -> new long[2])[0] += r.getQuantity());
        distributions.findAll().forEach(d -> out.computeIfAbsent(d.getItemId(), k -> new long[2])[1] += d.getQuantity());
        return out;
    }

    long inStock(Long itemId) {
        long[] c = counts().getOrDefault(itemId, new long[2]);
        return c[0] - c[1];
    }

    @Transactional
    public Map<String, Object> saveItem(Long id, Map<String, Object> b) {
        requireOffice();
        MaterialItem i = id == null ? new MaterialItem() : items.findById(id).orElseThrow(() -> notFound("Item"));
        i.setName(text(b.get("name"), "Name", 150, true));
        i.setCategory(code(b.get("category"), CATEGORIES, "OTHER", "Kind"));
        i.setUnit(text(b.get("unit"), "Unit", 20, false));
        i.setNotes(text(b.get("notes"), "Notes", 500, false));
        i.setActive(!b.containsKey("active") || flag(b.get("active")));
        items.save(i);
        return Map.of("id", i.getId(), "message", "Item saved.");
    }

    @Transactional
    public void deleteItem(Long id) {
        requireOffice();
        MaterialItem i = items.findById(id).orElseThrow(() -> notFound("Item"));
        if (!receipts.findByItemIdOrderByReceivedOnDesc(id).isEmpty() || distributions.findAll().stream().anyMatch(d -> id.equals(d.getItemId()))) {
            throw bad("This item has stock or hand-outs recorded. Make it inactive instead.");
        }
        items.delete(i);
    }

    public List<Map<String, Object>> receipts(Long itemId) {
        items.findById(itemId).orElseThrow(() -> notFound("Item"));
        Map<Long, String> donors = new HashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MaterialReceipt r : receipts.findByItemIdOrderByReceivedOnDesc(itemId)) {
            Map<String, Object> row = Rows.of(r);
            row.put("donor", r.getDonorName() != null ? r.getDonorName() : r.getDonorUserId() == null ? null
                    : donors.computeIfAbsent(r.getDonorUserId(), k -> users.findById(k).map(User::getName).orElse(null)));
            rows.add(row);
        }
        return rows;
    }

    /** Stock received: { itemId, quantity, receivedOn, donorName, donorUserId, note }. */
    @Transactional
    public Map<String, Object> addReceipt(Map<String, Object> b) {
        requireOffice();
        MaterialReceipt r = new MaterialReceipt();
        Long itemId = ServiceSupport.toLong(b.get("itemId"));
        items.findById(itemId == null ? -1L : itemId).orElseThrow(() -> bad("Choose the item."));
        r.setItemId(itemId);
        r.setQuantity(quantity(b.get("quantity")));
        r.setReceivedOn(day(b.get("receivedOn"), "Received on"));
        r.setDonorName(text(b.get("donorName"), "Donor", 150, false));
        r.setDonorUserId(ServiceSupport.toLong(b.get("donorUserId")));
        r.setNote(text(b.get("note"), "Note", 500, false));
        receipts.save(r);
        return Map.of("message", r.getQuantity() + " added to stock.");
    }

    @Transactional
    public void deleteReceipt(Long id) {
        requireOffice();
        MaterialReceipt r = receipts.findById(id).orElseThrow(() -> notFound("Stock entry"));
        if (inStock(r.getItemId()) - r.getQuantity() < 0) throw bad("Some of this stock was already handed out, so it can't be removed.");
        receipts.delete(r);
    }

    // ================================================================ hand-outs

    /** Hand-outs to the students the user may see, newest first; optionally one item / kind of student. */
    public List<Map<String, Object>> distributions(Long itemId, String kind, String q) {
        Map<Long, String> itemNames = new HashMap<>();
        items.findAll().forEach(i -> itemNames.put(i.getId(), i.getName()));
        Recipients who = recipients();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MaterialDistribution d : distributions.findAllByOrderByGivenOnDescIdDesc()) {
            if (itemId != null && !itemId.equals(d.getItemId())) continue;
            if (kind != null && !kind.isBlank() && !kind.equalsIgnoreCase(d.getRecipientKind())) continue;
            Map<String, Object> person = who.find(d.getRecipientKind(), d.getRecipientId());
            if (person == null) continue; // not one of the user's students
            if (!com.rahbar.util.Paging.matches(q, person.get("name"), person.get("code"), person.get("place"), itemNames.get(d.getItemId()))) continue;
            Map<String, Object> r = Rows.of(d);
            r.put("item", itemNames.get(d.getItemId()));
            r.put("recipient", person.get("name"));
            r.put("recipient_code", person.get("code"));
            r.put("recipient_place", person.get("place"));
            r.put("recipient_label", person.get("label"));
            rows.add(r);
        }
        return rows;
    }

    /**
     * Hands out an item to one or more students: { itemId, quantity (each), givenOn, note, recipients: [{ kind, id }] }.
     * The stock must cover everyone.
     */
    @Transactional
    public Map<String, Object> give(Map<String, Object> b) {
        Long itemId = ServiceSupport.toLong(b.get("itemId"));
        MaterialItem item = items.findById(itemId == null ? -1L : itemId).orElseThrow(() -> bad("Choose the item."));
        int each = quantity(b.get("quantity"));
        LocalDate on = day(b.get("givenOn"), "Given on");
        String note = text(b.get("note"), "Note", 500, false);
        List<long[]> list = new ArrayList<>();
        Recipients who = recipients();
        if (b.get("recipients") instanceof Collection<?> c) {
            for (Object o : c) {
                if (!(o instanceof Map<?, ?> m)) continue;
                String kind = code(m.get("kind"), Set.of(SCHOLAR, COACHING, REB), null, "Kind of student");
                Long id = ServiceSupport.toLong(m.get("id"));
                if (id == null || who.find(kind, id) == null) throw bad("One of the students isn't one you can give to.");
                list.add(new long[]{kindIndex(kind), id});
            }
        }
        if (list.isEmpty()) throw bad("Choose who gets it.");
        long needed = (long) each * list.size();
        long left = inStock(itemId);
        if (needed > left) throw bad("Only " + left + " " + item.getName() + " in stock; " + needed + " needed.");
        for (long[] r : list) {
            MaterialDistribution d = new MaterialDistribution();
            d.setItemId(itemId);
            d.setQuantity(each);
            d.setGivenOn(on);
            d.setRecipientKind(KINDS.get((int) r[0]));
            d.setRecipientId(r[1]);
            d.setNote(note);
            distributions.save(d);
        }
        return Map.of("message", needed + " " + item.getName() + " handed out to " + list.size() + " student(s). " + (left - needed) + " left in stock.");
    }

    /** Undoes a hand-out recorded by mistake (back into stock). */
    @Transactional
    public void deleteDistribution(Long id) {
        MaterialDistribution d = distributions.findById(id).orElseThrow(() -> notFound("Hand-out"));
        if (recipients().find(d.getRecipientKind(), d.getRecipientId()) == null) throw notFound("Hand-out");
        distributions.delete(d);
    }

    /** Students the user may give to, matching q (name, ID / admission no., phone). */
    public List<Map<String, Object>> searchRecipients(String q) {
        if (q == null || q.trim().length() < 2) return List.of();
        return recipients().all().stream()
                .filter(p -> com.rahbar.util.Paging.matches(q, p.get("name"), p.get("code"), p.get("phone")))
                .limit(25).toList();
    }

    /** What a scholarship student received (Student Directory). */
    public List<Map<String, Object>> forScholar(Long userId) {
        Map<Long, String> itemNames = new HashMap<>();
        items.findAll().forEach(i -> itemNames.put(i.getId(), i.getName()));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MaterialDistribution d : distributions.findByRecipientKindAndRecipientIdOrderByGivenOnDesc(SCHOLAR, userId)) {
            rows.add(Map.of("item", itemNames.getOrDefault(d.getItemId(), "?"), "quantity", d.getQuantity(), "given_on", d.getGivenOn(),
                    "note", d.getNote() == null ? "" : d.getNote()));
        }
        return rows;
    }

    // ================================================================ who the user may give to

    private static final List<String> KINDS = List.of(SCHOLAR, COACHING, REB);

    private static int kindIndex(String kind) {
        return KINDS.indexOf(kind);
    }

    /** The students the signed-in user may give to (loaded once per request). */
    Recipients recipients() {
        Map<String, Map<String, Object>> byKey = new LinkedHashMap<>();
        StudentScope.Scope scope = studentScope.current();
        if (!scope.none()) {
            for (User u : users.findByRoleId(ServiceSupport.STUDENT_ROLE)) {
                if ("Inactive".equalsIgnoreCase(u.getStatus()) || u.getMergedIntoId() != null || DataQualityService.coachingOnly(u) || !scope.includes(u)) continue;
                byKey.put(SCHOLAR + ":" + u.getId(), person(SCHOLAR, u.getId(), u.getName(), u.getUserId(), u.getChapterName(), "Scholarship", u.getPhone()));
            }
        }
        Set<Long> centers = coachingAccess.centerIds();
        Map<Long, String> centerNames = coachingAccess.names();
        for (CoachingStudent s : coachingStudents.findAll()) {
            if (!centers.contains(s.getRccCenterId()) || !"ACTIVE".equals(s.getStatus())) continue;
            byKey.put(COACHING + ":" + s.getId(), person(COACHING, s.getId(), s.getName(), s.getAdmissionNo(), centerNames.get(s.getRccCenterId()),
                    "Coaching class " + s.getClassLevel(), s.getGuardianMobile()));
        }
        if (scope.all() && Access.can(Section.REB_STUDENTS, Section.Level.VIEW)) {
            for (RebStudent g : rebStudents.findAll()) {
                if (!"ACTIVE".equals(g.getStatus())) continue;
                byKey.put(REB + ":" + g.getId(), person(REB, g.getId(), g.getName(), g.getAdmissionNo(), "Rahbar-e-Banaat", "REB", null));
            }
        }
        return new Recipients(byKey);
    }

    record Recipients(Map<String, Map<String, Object>> byKey) {
        Map<String, Object> find(String kind, Long id) {
            return byKey.get(kind + ":" + id);
        }

        Collection<Map<String, Object>> all() {
            return byKey.values();
        }
    }

    private static Map<String, Object> person(String kind, Long id, String name, String code, String place, String label, String phone) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("kind", kind);
        p.put("id", id);
        p.put("name", name);
        p.put("code", code);
        p.put("place", place);
        p.put("label", label);
        p.put("phone", phone);
        return p;
    }

    // ================================================================ helpers

    /** Items and stock are shared by everyone, so only the office (not a chapter / centre lead) changes them. */
    private static void requireOffice() {
        if (Access.chapterScoped() || Access.rccScoped()) throw Access.forbidden("Items and stock are kept by the office.");
    }

    private static int quantity(Object v) {
        Integer q = ServiceSupport.isBlank(v) ? null : ServiceSupport.toInteger(v);
        if (q == null || q < 1 || q > 10000) throw bad("Quantity: a number from 1 to 10000.");
        return q;
    }

    private static LocalDate day(Object v, String label) {
        LocalDate d = date(v, label);
        if (d == null) return LocalDate.now();
        if (d.isAfter(LocalDate.now())) throw bad(label + " can't be in the future.");
        return d;
    }
}
