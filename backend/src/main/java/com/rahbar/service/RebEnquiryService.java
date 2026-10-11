package com.rahbar.service;

import com.rahbar.entity.RebAdmissionEnquiry;
import com.rahbar.repository.RebAdmissionEnquiryRepository;
import com.rahbar.security.Section;
import com.rahbar.util.Rows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

import static com.rahbar.util.RebSupport.*;

/**
 * REB admission enquiries: from the website's "Apply for admission" form (the office is notified), or entered by
 * the office after a phone call or a visit. The office follows them up (contacted, entrance test) and admits the
 * girl from the enquiry, which fills the admission form with what the family gave.
 */
@Service
public class RebEnquiryService {

    static final Set<String> STATUSES = Set.of("NEW", "CONTACTED", "TEST", "ADMITTED", "NOT_ADMITTED");
    static final Set<String> SOURCES = Set.of("WEBSITE", "PHONE", "WALK_IN");

    private final RebAdmissionEnquiryRepository enquiries;
    private final RebStudentService students;
    private final WebsiteNotifier notifier;

    public RebEnquiryService(RebAdmissionEnquiryRepository enquiries, RebStudentService students, WebsiteNotifier notifier) {
        this.enquiries = enquiries;
        this.students = students;
        this.notifier = notifier;
    }

    private void read(RebAdmissionEnquiry e, Map<String, Object> body) {
        e.setGirlName(text(body.get("girlName"), "Girl's name", 120, true));
        e.setDateOfBirth(date(body.get("dateOfBirth"), "Date of birth"));
        if (e.getDateOfBirth() != null && e.getDateOfBirth().isAfter(LocalDate.now())) throw bad("The date of birth is in the future.");
        e.setGuardianName(text(body.get("guardianName"), "Parent's or guardian's name", 120, true));
        e.setRelation(code(body.get("relation"), RebStudentService.RELATIONS, "FATHER", "relation"));
        String phone = text(body.get("phone"), "Phone", 20, true);
        if (phone.replaceAll("\\D", "").length() < 10) throw bad("Please give a phone number with at least 10 digits.");
        e.setPhone(phone);
        String email = text(body.get("email"), "Email", 150, false);
        if (email != null && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw bad("Please check the email address.");
        e.setEmail(email);
        e.setDistrict(text(body.get("district"), "District", 80, false));
        e.setState(text(body.get("state"), "State", 40, false));
        e.setClassSought(text(body.get("classSought"), "Class", 80, false));
        e.setPreviousSchool(text(body.get("previousSchool"), "Previous school", 150, false));
        e.setResidential(flag(body.get("residential"), true));
        e.setMessage(text(body.get("message"), "Message", 2000, false));
    }

    /** From the website (the caller checks the bot trap and the per-visitor limit). The office is notified. */
    @Transactional
    public void submitFromWebsite(Map<String, Object> body) {
        RebAdmissionEnquiry e = new RebAdmissionEnquiry();
        read(e, body);
        e.setSource("WEBSITE");
        enquiries.save(e);
        notifier.notify(Section.REB_ENQUIRIES, null, null, "Admission enquiry: " + e.getGirlName(),
                e.getGuardianName() + " asked about admission" + (e.getClassSought() == null ? "" : " to " + e.getClassSought())
                        + " at Rahbar-e-Banaat.", "/admin/reb/enquiries");
    }

    public List<Map<String, Object>> list(String status) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RebAdmissionEnquiry e : enquiries.findAllByOrderByIdDesc()) {
            if (status != null && !status.isBlank() && !status.equalsIgnoreCase(e.getStatus())) continue;
            rows.add(Rows.of(e));
        }
        return rows;
    }

    /** An enquiry taken by phone or at a visit. Body: the form fields and source PHONE | WALK_IN. */
    @Transactional
    public RebAdmissionEnquiry create(Map<String, Object> body) {
        RebAdmissionEnquiry e = new RebAdmissionEnquiry();
        read(e, body);
        e.setSource(code(body.get("source"), SOURCES, "PHONE", "source"));
        e.setNotes(text(body.get("notes"), "Notes", 2000, false));
        return enquiries.save(e);
    }

    /** Body: { status, notes } (and the form fields, to correct them). */
    @Transactional
    public RebAdmissionEnquiry update(Long id, Map<String, Object> body) {
        RebAdmissionEnquiry e = enquiries.findById(id).orElseThrow(() -> notFound("Enquiry"));
        if (body.containsKey("girlName")) read(e, body);
        if (body.containsKey("status")) {
            String s = code(body.get("status"), STATUSES, e.getStatus(), "status");
            if ("ADMITTED".equals(s) && e.getStudentId() == null) throw bad("Use Admit to admit her: it creates her record.");
            e.setStatus(s);
        }
        if (body.containsKey("notes")) e.setNotes(text(body.get("notes"), "Notes", 2000, false));
        return enquiries.save(e);
    }

    /**
     * Admits the girl: her record is made from the enquiry (name, date of birth, district, hostel, previous school,
     * the guardian) plus the class chosen. Body: { sectionId, rollNo, feePlan, admittedOn }. Returns her id.
     */
    @Transactional
    public Long admit(Long id, Map<String, Object> body) {
        RebAdmissionEnquiry e = enquiries.findById(id).orElseThrow(() -> notFound("Enquiry"));
        if (e.getStudentId() != null) throw bad("She was already admitted from this enquiry.");
        Map<String, Object> form = new HashMap<>(body);
        form.put("name", e.getGirlName());
        form.put("dateOfBirth", e.getDateOfBirth() == null ? null : e.getDateOfBirth().toString());
        form.put("district", e.getDistrict());
        form.put("state", e.getState());
        form.put("previousSchool", e.getPreviousSchool());
        form.put("residential", e.isResidential());
        form.put("guardians", List.of(Map.of("relation", e.getRelation() == null ? "FATHER" : e.getRelation(), "name", e.getGuardianName(),
                "phone", e.getPhone(), "email", e.getEmail() == null ? "" : e.getEmail(), "primary", true)));
        Long studentId = students.admit(form);
        e.setStudentId(studentId);
        e.setStatus("ADMITTED");
        enquiries.save(e);
        return studentId;
    }
}
