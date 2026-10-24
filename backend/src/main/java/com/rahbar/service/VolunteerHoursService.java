package com.rahbar.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.rahbar.entity.User;
import com.rahbar.entity.VolunteerApplication;
import com.rahbar.entity.VolunteerHours;
import com.rahbar.repository.UserRepository;
import com.rahbar.repository.VolunteerApplicationRepository;
import com.rahbar.repository.VolunteerHoursRepository;
import com.rahbar.security.Access;
import com.rahbar.util.Rows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.rahbar.service.FormInput.*;

/**
 * Community > Volunteer Hours: hours given by website volunteers, mentors, alumni and other Rahbar users. Staff log
 * them (approved straight away); people with a Rahbar login log their own in their portal, and the office approves
 * them. Only approved hours count, and a certificate (PDF) lists them.
 */
@Service
public class VolunteerHoursService {

    public static final String VOLUNTEER = "VOLUNTEER", USER = "USER";
    static final Map<String, String> ACTIVITIES = new LinkedHashMap<>();
    static {
        ACTIVITIES.put("MENTORING", "Mentoring");
        ACTIVITIES.put("TUTORING", "Tutoring");
        ACTIVITIES.put("EVENT", "Events & workshops");
        ACTIVITIES.put("CHAPTER", "Chapter work");
        ACTIVITIES.put("COACHING", "Coaching centre");
        ACTIVITIES.put("OFFICE", "Office / admin help");
        ACTIVITIES.put("OTHER", "Other");
    }
    static final BigDecimal MAX_HOURS = new BigDecimal("24");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final VolunteerHoursRepository hours;
    private final VolunteerApplicationRepository volunteers;
    private final UserRepository users;
    private final NotificationService notifications;

    public VolunteerHoursService(VolunteerHoursRepository hours, VolunteerApplicationRepository volunteers, UserRepository users,
                                 NotificationService notifications) {
        this.hours = hours;
        this.volunteers = volunteers;
        this.users = users;
        this.notifications = notifications;
    }

    // ================================================================ office

    /** Hours in a period (default: this year), with totals by activity and per person (approved only). */
    public Map<String, Object> list(String status, LocalDate from, LocalDate to, String q) {
        LocalDate start = from == null ? LocalDate.of(LocalDate.now().getYear(), 1, 1) : from, end = to == null ? LocalDate.now() : to;
        Names names = new Names();
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, BigDecimal> byActivity = new LinkedHashMap<>();
        ACTIVITIES.values().forEach(a -> byActivity.put(a, BigDecimal.ZERO));
        Map<String, Map<String, Object>> people = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        long pending = 0;
        for (VolunteerHours h : hours.findAllByOrderByWorkedOnDescIdDesc()) {
            if ("PENDING".equals(h.getStatus())) pending++;
            if (h.getWorkedOn().isBefore(start) || h.getWorkedOn().isAfter(end)) continue;
            String name = names.of(h);
            if (status != null && !status.isBlank() && !status.equalsIgnoreCase(h.getStatus())) continue;
            if (!com.rahbar.util.Paging.matches(q, name, h.getDescription())) continue;
            Map<String, Object> r = Rows.of(h);
            r.put("person", name);
            r.put("person_kind", h.getVolunteerId() != null ? VOLUNTEER : USER);
            r.put("activity_label", ACTIVITIES.getOrDefault(h.getActivity(), h.getActivity()));
            rows.add(r);
            if (!"APPROVED".equals(h.getStatus())) continue;
            total = total.add(h.getHours());
            byActivity.merge(ACTIVITIES.getOrDefault(h.getActivity(), "Other"), h.getHours(), BigDecimal::add);
            String key = (h.getVolunteerId() != null ? VOLUNTEER + ":" + h.getVolunteerId() : USER + ":" + h.getUserId());
            Map<String, Object> p = people.computeIfAbsent(key, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("kind", h.getVolunteerId() != null ? VOLUNTEER : USER);
                m.put("id", h.getVolunteerId() != null ? h.getVolunteerId() : h.getUserId());
                m.put("name", name);
                m.put("hours", BigDecimal.ZERO);
                m.put("days", new HashSet<LocalDate>());
                return m;
            });
            p.put("hours", ((BigDecimal) p.get("hours")).add(h.getHours()));
            @SuppressWarnings("unchecked") Set<LocalDate> days = (Set<LocalDate>) p.get("days");
            days.add(h.getWorkedOn());
        }
        List<Map<String, Object>> top = new ArrayList<>(people.values());
        top.forEach(p -> p.put("days", ((Set<?>) p.get("days")).size()));
        top.sort(Comparator.comparing((Map<String, Object> p) -> (BigDecimal) p.get("hours")).reversed());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("from", start);
        out.put("to", end);
        out.put("rows", rows);
        out.put("total_hours", total);
        out.put("volunteers", people.size());
        out.put("pending", pending);
        out.put("by_activity", byActivity);
        out.put("people", top);
        out.put("activities", ACTIVITIES);
        return out;
    }

    /** Staff log hours (approved): { personKind: VOLUNTEER | USER, personId, activity, workedOn, hours, description }. */
    @Transactional
    public Map<String, Object> log(Map<String, Object> b) {
        VolunteerHours h = new VolunteerHours();
        String kind = code(b.get("personKind"), Set.of(VOLUNTEER, USER), null, "Who");
        Long id = ServiceSupport.toLong(b.get("personId"));
        if (id == null) throw bad("Choose who gave the hours.");
        if (VOLUNTEER.equals(kind)) {
            volunteers.findById(id).orElseThrow(() -> notFound("Volunteer"));
            h.setVolunteerId(id);
        } else {
            users.findById(id).orElseThrow(() -> notFound("Person"));
            h.setUserId(id);
        }
        fill(h, b);
        h.setStatus("APPROVED");
        h.setReviewedBy(Access.current().getUser().getId());
        h.setReviewedAt(LocalDateTime.now());
        hours.save(h);
        return Map.of("message", h.getHours().stripTrailingZeros().toPlainString() + " hour(s) logged.");
    }

    /** Approves or rejects self-logged hours: { approve: true | false }. */
    @Transactional
    public Map<String, Object> review(Long id, boolean approve) {
        VolunteerHours h = hours.findById(id).orElseThrow(() -> notFound("Hours"));
        h.setStatus(approve ? "APPROVED" : "REJECTED");
        h.setReviewedBy(Access.current().getUser().getId());
        h.setReviewedAt(LocalDateTime.now());
        hours.save(h);
        if (h.getUserId() != null) {
            notifications.notify(h.getUserId(), approve ? "Volunteer hours approved" : "Volunteer hours not approved",
                    h.getHours().stripTrailingZeros().toPlainString() + " hour(s) on " + h.getWorkedOn().format(DAY) + (approve ? " were approved. Thank you!" : " were not approved."),
                    "volunteer", null, false);
        }
        return Map.of("message", approve ? "Approved." : "Rejected.");
    }

    @Transactional
    public void delete(Long id) {
        hours.delete(hours.findById(id).orElseThrow(() -> notFound("Hours")));
    }

    /** Website volunteers and Rahbar users matching q, to log hours for. */
    public List<Map<String, Object>> people(String q) {
        if (q == null || q.trim().length() < 2) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        volunteers.findAll().stream().filter(v -> com.rahbar.util.Paging.matches(q, v.getFullName(), v.getEmail(), v.getPhone())).limit(15)
                .forEach(v -> out.add(Map.of("kind", VOLUNTEER, "id", v.getVolunteerId(), "name", v.getFullName(), "who", "Volunteer sign-up"
                        + (v.getCity() == null ? "" : ", " + v.getCity()))));
        users.findAll().stream().filter(u -> !"Inactive".equalsIgnoreCase(u.getStatus()) && u.getMergedIntoId() == null)
                .filter(u -> com.rahbar.util.Paging.matches(q, u.getName(), u.getUserId(), u.getEmail(), u.getPhone())).limit(15)
                .forEach(u -> out.add(Map.of("kind", USER, "id", u.getId(), "name", u.getName(), "who", "Rahbar user" + (u.getUserId() == null ? "" : " " + u.getUserId()))));
        return out;
    }

    // ================================================================ portal (students incl. alumni, sponsors)

    public Map<String, Object> mine(Long me) {
        List<VolunteerHours> list = hours.findByUserIdOrderByWorkedOnDesc(me);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", list.stream().map(h -> {
            Map<String, Object> r = Rows.of(h);
            r.put("activity_label", ACTIVITIES.getOrDefault(h.getActivity(), h.getActivity()));
            return r;
        }).toList());
        out.put("approved_hours", list.stream().filter(h -> "APPROVED".equals(h.getStatus())).map(VolunteerHours::getHours).reduce(BigDecimal.ZERO, BigDecimal::add));
        out.put("activities", ACTIVITIES);
        return out;
    }

    /** Someone logs their own hours; the office approves them. */
    @Transactional
    public Map<String, Object> logMine(User me, Map<String, Object> b) {
        VolunteerHours h = new VolunteerHours();
        h.setUserId(me.getId());
        fill(h, b);
        h.setStatus("PENDING");
        h.setSelfLogged(true);
        hours.save(h);
        notifications.notifyRoles(CareerService.OFFICE_ROLES, "Volunteer hours to approve",
                me.getName() + " logged " + h.getHours().stripTrailingZeros().toPlainString() + " hour(s).", "volunteer", "/admin/volunteer-hours");
        return Map.of("message", "Thank you! The office will approve your hours.");
    }

    // ================================================================ certificate

    /** A certificate of approved hours for a person in a period (default: all time). */
    public byte[] certificate(String kind, Long id, LocalDate from, LocalDate to) {
        String name;
        List<VolunteerHours> list;
        if (VOLUNTEER.equalsIgnoreCase(kind)) {
            VolunteerApplication v = volunteers.findById(id).orElseThrow(() -> notFound("Volunteer"));
            name = v.getFullName();
            list = hours.findByVolunteerIdOrderByWorkedOnDesc(id);
        } else {
            User u = users.findById(id).orElseThrow(() -> notFound("Person"));
            name = u.getName();
            list = hours.findByUserIdOrderByWorkedOnDesc(id);
        }
        List<VolunteerHours> approved = list.stream().filter(h -> "APPROVED".equals(h.getStatus()))
                .filter(h -> (from == null || !h.getWorkedOn().isBefore(from)) && (to == null || !h.getWorkedOn().isAfter(to)))
                .sorted(Comparator.comparing(VolunteerHours::getWorkedOn)).toList();
        if (approved.isEmpty()) throw bad("No approved hours in this period.");
        return pdf(name, approved);
    }

    private static byte[] pdf(String name, List<VolunteerHours> list) {
        BigDecimal total = list.stream().map(VolunteerHours::getHours).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, BigDecimal> byActivity = new LinkedHashMap<>();
        list.forEach(h -> byActivity.merge(ACTIVITIES.getOrDefault(h.getActivity(), "Other"), h.getHours(), BigDecimal::add));
        LocalDate first = list.get(0).getWorkedOn(), last = list.get(list.size() - 1).getWorkedOn();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 54, 54, 72, 54);
        PdfWriter.getInstance(doc, out);
        doc.open();
        Color green = new Color(0x3D, 0x6B, 0x1F);
        try {
            add(doc, new Paragraph("Rahbar - Bihar Anjuman", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, green)), Element.ALIGN_CENTER, 30);
            add(doc, new Paragraph("Certificate of Volunteering", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22)), Element.ALIGN_CENTER, 24);
            add(doc, new Paragraph("This is to certify that", FontFactory.getFont(FontFactory.HELVETICA, 12)), Element.ALIGN_CENTER, 10);
            add(doc, new Paragraph(name, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, green)), Element.ALIGN_CENTER, 10);
            add(doc, new Paragraph("gave " + total.stripTrailingZeros().toPlainString() + " hours of voluntary service to the education work of Bihar Anjuman"
                    + (first.equals(last) ? " on " + first.format(DAY) : " between " + first.format(DAY) + " and " + last.format(DAY)) + ".",
                    FontFactory.getFont(FontFactory.HELVETICA, 12)), Element.ALIGN_CENTER, 24);
            PdfPTable t = new PdfPTable(new float[]{4f, 1.5f});
            t.setWidthPercentage(60);
            Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10), text = FontFactory.getFont(FontFactory.HELVETICA, 10);
            t.addCell(cell("Activity", bold, true));
            t.addCell(cell("Hours", bold, true));
            byActivity.forEach((a, h) -> { t.addCell(cell(a, text, false)); t.addCell(cell(h.stripTrailingZeros().toPlainString(), text, false)); });
            t.addCell(cell("Total", bold, false));
            t.addCell(cell(total.stripTrailingZeros().toPlainString(), bold, false));
            doc.add(t);
            add(doc, new Paragraph("Thank you for helping students from marginalised communities in Bihar get an education.",
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 11)), Element.ALIGN_CENTER, 30);
            add(doc, new Paragraph("Issued on " + LocalDate.now().format(DAY), FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY)), Element.ALIGN_CENTER, 40);
        } catch (DocumentException e) {
            throw new IllegalStateException(e);
        }
        doc.close();
        return out.toByteArray();
    }

    private static void add(Document doc, Paragraph p, int align, float spaceBefore) throws DocumentException {
        p.setAlignment(align);
        p.setSpacingBefore(spaceBefore);
        doc.add(p);
    }

    private static PdfPCell cell(String s, Font f, boolean head) {
        PdfPCell c = new PdfPCell(new Phrase(s, f));
        c.setPadding(5);
        if (head) c.setBackgroundColor(new Color(0xEE, 0xF2, 0xEC));
        return c;
    }

    // ================================================================ helpers

    private void fill(VolunteerHours h, Map<String, Object> b) {
        h.setActivity(code(b.get("activity"), ACTIVITIES.keySet(), "OTHER", "Activity"));
        LocalDate on = date(b.get("workedOn"), "Date");
        h.setWorkedOn(on == null ? LocalDate.now() : on);
        if (h.getWorkedOn().isAfter(LocalDate.now())) throw bad("The date can't be in the future.");
        BigDecimal n;
        try {
            n = ServiceSupport.isBlank(b.get("hours")) ? null : new BigDecimal(String.valueOf(b.get("hours")).trim());
        } catch (NumberFormatException e) {
            n = null;
        }
        if (n == null || n.signum() <= 0 || n.compareTo(MAX_HOURS) > 0) throw bad("Hours: more than 0 and at most 24 for a day.");
        h.setHours(n);
        h.setDescription(text(b.get("description"), "What you did", 500, false));
    }

    /** Names of the people behind hours rows, looked up once each. */
    private final class Names {
        private final Map<String, String> cache = new HashMap<>();

        String of(VolunteerHours h) {
            if (h.getVolunteerId() != null) {
                return cache.computeIfAbsent("V" + h.getVolunteerId(), k -> volunteers.findById(h.getVolunteerId()).map(VolunteerApplication::getFullName).orElse("?"));
            }
            return cache.computeIfAbsent("U" + h.getUserId(), k -> users.findById(h.getUserId()).map(User::getName).orElse("?"));
        }
    }
}
