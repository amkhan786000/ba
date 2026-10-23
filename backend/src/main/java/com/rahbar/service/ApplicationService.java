package com.rahbar.service;

import com.rahbar.entity.ApplicationDocument;
import com.rahbar.entity.ApplicationStatus;
import com.rahbar.entity.ScholarshipApplication;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.ApplicationDocumentRepository;
import com.rahbar.repository.ApplicationStatusRepository;
import com.rahbar.repository.ScholarshipApplicationRepository;
import com.rahbar.repository.UserRepository;
import com.rahbar.util.Rows;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Scholarship applications (scholarship_applications) after submission: status changes, interview scheduling,
 * supporting documents, the staff detail view and the public "track my application" page. Accepting (which makes the
 * student account) is in ApplicationAcceptService.
 */
@Service
public class ApplicationService {

    /** The steps of an application, in order, with what applicants and staff see. */
    public static final Map<String, String> STATUS_LABELS = new LinkedHashMap<>();
    static {
        STATUS_LABELS.put(ScholarshipApplication.APPLIED, "Applied");
        STATUS_LABELS.put(ScholarshipApplication.IN_PROCESS, "In process");
        STATUS_LABELS.put(ScholarshipApplication.INTERVIEW, "Interview called");
        STATUS_LABELS.put(ScholarshipApplication.ACCEPTED, "Accepted");
        STATUS_LABELS.put(ScholarshipApplication.REJECTED, "Rejected");
    }
    public static final Set<String> VALID_STATUSES = STATUS_LABELS.keySet();
    /** Still waiting for a decision. */
    public static final Set<String> OPEN_STATUSES = Set.of(ScholarshipApplication.APPLIED, ScholarshipApplication.IN_PROCESS,
            ScholarshipApplication.INTERVIEW);

    public static final List<String> DOCUMENT_TYPES = List.of(
            // the 2026 form's list of enclosures, then what the office also asks for
            "Photograph", "10th marksheet", "12th marksheet", "ITI / Diploma marksheet", "Aadhaar card (self-attested)",
            "Bank passbook", "Birth certificate (self-attested)", "Specimen signature", "Income certificate", "Admission letter",
            "Marksheet", "ID proof", "Photo", "Other");

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png", "webp", "doc", "docx");
    private static final int MAX_DOCUMENTS = 10;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a");

    private final ScholarshipApplicationRepository applications;
    private final ApplicationStatusRepository applicationStatusRepository;
    private final ApplicationDocumentRepository documentRepository;
    private final FileStorageService fileStorageService;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final com.rahbar.security.StudentScope studentScope;

    public ApplicationService(ScholarshipApplicationRepository applications,
                              ApplicationStatusRepository applicationStatusRepository,
                              ApplicationDocumentRepository documentRepository, FileStorageService fileStorageService,
                              EmailService emailService, UserRepository userRepository,
                              com.rahbar.security.StudentScope studentScope) {
        this.applications = applications;
        this.applicationStatusRepository = applicationStatusRepository;
        this.documentRepository = documentRepository;
        this.fileStorageService = fileStorageService;
        this.emailService = emailService;
        this.userRepository = userRepository;
        this.studentScope = studentScope;
    }

    public static String label(String status) {
        return STATUS_LABELS.getOrDefault(status, status);
    }

    /** Staff access to an application: chapter / RCC-scoped users only reach their own area's (else 404). */
    public ScholarshipApplication require(Long applicationId) {
        return studentScope.requireApplication(applications.findById(applicationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Application not found.")));
    }

    // ---------------------------------------------------------------- staff actions

    /**
     * Moves the application to another step (Applied, In process, Interview called or Rejected; accepting has its
     * own action because it creates the student), keeps the history and emails the applicant.
     */
    public void updateStatus(Long applicationId, String status, String comments) {
        String to = status == null ? "" : status.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if (!VALID_STATUSES.contains(to)) throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid status selected!");
        if (ScholarshipApplication.ACCEPTED.equals(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Use \"Accept\" to accept an application: it also creates the student.");
        }
        ScholarshipApplication a = require(applicationId);
        if (ScholarshipApplication.ACCEPTED.equals(a.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "This application was accepted and the student created; it can't change any more.");
        }
        setStatus(a, to, comments);
        boolean rejected = ScholarshipApplication.REJECTED.equals(to);
        a.setDecidedAt(rejected ? LocalDateTime.now() : null);
        a.setDecidedBy(rejected ? com.rahbar.security.Access.current().getUser().getId() : null);
        applications.save(a);
        notifyApplicant(a, comments);
    }

    /** Sets the interview date / venue and moves the application to "Interview called". */
    public void scheduleInterview(Long applicationId, String interviewAt, String venue) {
        ScholarshipApplication a = require(applicationId);
        if (ScholarshipApplication.ACCEPTED.equals(a.getStatus()) || ScholarshipApplication.REJECTED.equals(a.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "This application has been decided already.");
        }
        LocalDateTime when = ServiceSupport.parseDateTime(interviewAt);
        if (when == null) throw new ApiException(HttpStatus.BAD_REQUEST, "Please choose the interview date and time.");
        if (venue == null || venue.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "Please enter the interview venue.");
        a.setInterviewAt(when);
        a.setInterviewVenue(venue.trim());
        String text = "Interview on " + when.format(WHEN) + " at " + venue.trim();
        setStatus(a, ScholarshipApplication.INTERVIEW, text);
        applications.save(a);
        emailService.send(EmailType.INTERVIEW_SCHEDULED, a.getEmail(),
                ServiceSupport.vars("name", a.getName(), "interview_at", when.format(WHEN), "venue", venue.trim()));
    }

    /** Sets the current status and adds it to the history (not saved: the caller saves the application). */
    void setStatus(ScholarshipApplication a, String status, String comments) {
        a.setStatus(status);
        ApplicationStatus as = new ApplicationStatus();
        as.setApplicationId(a.getId());
        as.setStatus(status);
        as.setComments(comments == null || comments.isBlank() ? null : comments.trim());
        applicationStatusRepository.save(as);
    }

    /** "Your application is now ...": by email (the applicant has no account until accepted). */
    void notifyApplicant(ScholarshipApplication a, String comments) {
        emailService.send(EmailType.APPLICATION_STATUS, a.getEmail(),
                ServiceSupport.vars("name", a.getName(), "application_id", a.getId(), "status", label(a.getStatus()),
                        "comments", comments == null || comments.isBlank() ? "" : "Note: " + comments.trim()));
    }

    /** Full application for the staff detail page: fields, status, history and documents. */
    public Map<String, Object> details(Long applicationId) {
        ScholarshipApplication a = require(applicationId);
        List<ApplicationStatus> history = applicationStatusRepository.findByApplicationIdOrderByCreatedAtDescStatusIdDesc(applicationId);
        Map<String, Object> app = Rows.of(a);
        app.put("status_label", label(a.getStatus()));
        app.put("status_date", history.isEmpty() ? a.getCreatedAt() : history.get(0).getCreatedAt());
        app.put("comments", history.isEmpty() ? null : history.get(0).getComments());
        app.put("student_code", a.getStudentUserId() == null ? null
                : userRepository.findById(a.getStudentUserId()).map(com.rahbar.entity.User::getUserId).orElse(null));
        app.put("decided_by_name", a.getDecidedBy() == null ? null
                : userRepository.findById(a.getDecidedBy()).map(com.rahbar.entity.User::getName).orElse(null));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("application", app);
        result.put("history", historyRows(history));
        result.put("documents", documentRows(applicationId));
        result.put("documentTypes", DOCUMENT_TYPES);
        result.put("statuses", statusOptions());
        return result;
    }

    /** [{ code, label }] in order, for the screens. */
    public static List<Map<String, String>> statusOptions() {
        List<Map<String, String>> out = new ArrayList<>();
        STATUS_LABELS.forEach((code, label) -> out.add(Map.of("code", code, "label", label)));
        return out;
    }

    // ---------------------------------------------------------------- staff look-up

    /**
     * Applications > Track Application: find applications by number ("123" or "#123"), a mobile on the form (any of the
     * three, last 10 digits), email or part of the name; at most 20, newest first, only those the user may see. Each
     * comes with its steps so far, so staff can answer a family's call without opening every application.
     */
    public List<Map<String, Object>> lookup(String q) {
        String text = q == null ? "" : q.trim();
        if (text.length() < 2) throw new ApiException(HttpStatus.BAD_REQUEST, "Type an application number, a mobile number, an email or a name.");
        String digits = text.replaceAll("\\D", "");
        List<ScholarshipApplication> found = new ArrayList<>();
        if (text.matches("#?\\d{1,9}")) {
            applications.findById(Long.valueOf(digits)).ifPresent(found::add);
        } else if (digits.length() >= 10 && text.matches("[+\\d\\s()-]+")) {
            String m = digits.substring(digits.length() - 10);
            found.addAll(applications.findByStudentMobileOrFatherMobileOrMotherMobileOrderByIdDesc(m, m, m));
        } else {
            found.addAll(applications.searchText("%" + text.toLowerCase(Locale.ROOT) + "%",
                    org.springframework.data.domain.PageRequest.of(0, 50)));
        }
        com.rahbar.security.StudentScope.Scope scope = studentScope.current();
        Map<Long, com.rahbar.entity.User> users = scope.all() ? Map.of() : studentScope.usersById();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ScholarshipApplication a : found) {
            if (!scope.includesApplication(a, users)) continue;
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", a.getId());
            r.put("name", a.getName());
            r.put("email", a.getEmail());
            r.put("student_mobile", a.getStudentMobile());
            r.put("father_mobile", a.getFatherMobile());
            r.put("mother_mobile", a.getMotherMobile());
            r.put("course_applied", a.getCourseApplied());
            r.put("rcc_name", a.getRccName());
            r.put("created_at", a.getCreatedAt());
            r.put("status", a.getStatus());
            r.put("status_label", label(a.getStatus()));
            r.put("interview_at", a.getInterviewAt());
            r.put("interview_venue", a.getInterviewVenue());
            r.put("student_user_id", a.getStudentUserId());
            r.put("documents", documentRepository.countByApplicationId(a.getId()));
            r.put("history", historyRows(applicationStatusRepository.findByApplicationIdOrderByCreatedAtDescStatusIdDesc(a.getId())));
            rows.add(r);
            if (rows.size() == 20) break;
        }
        return rows;
    }

    // ---------------------------------------------------------------- public tracking

    /** Public tracker: needs the application id and one of the three mobile numbers given on the form. */
    public Map<String, Object> track(Long applicationId, String mobile) {
        ScholarshipApplication a = requireForApplicant(applicationId, mobile);
        List<ApplicationStatus> history = applicationStatusRepository.findByApplicationIdOrderByCreatedAtDescStatusIdDesc(applicationId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("applicationId", a.getId());
        result.put("name", a.getName());
        result.put("courseApplied", a.getCourseApplied());
        result.put("rccName", a.getRccName());
        result.put("submittedAt", a.getCreatedAt());
        result.put("status", label(a.getStatus()));
        result.put("statusCode", a.getStatus());
        result.put("interviewAt", a.getInterviewAt());
        result.put("interviewVenue", a.getInterviewVenue());
        result.put("history", historyRows(history));
        // The public page only lists what was uploaded; files are not linked (they hold ID / income proofs).
        List<Map<String, Object>> documents = new ArrayList<>();
        for (Map<String, Object> d : documentRows(applicationId)) {
            Map<String, Object> copy = new LinkedHashMap<>(d);
            copy.remove("filePath");
            documents.add(copy);
        }
        result.put("documents", documents);
        result.put("documentTypes", DOCUMENT_TYPES);
        return result;
    }

    /** Upload by the applicant (public page): the mobile number proves it is their application. */
    public Map<String, Object> uploadByApplicant(Long applicationId, String mobile, String docType, MultipartFile file) {
        requireForApplicant(applicationId, mobile);
        return store(applicationId, docType, file);
    }

    /** Upload by staff from the application detail page. */
    public Map<String, Object> uploadByStaff(Long applicationId, String docType, MultipartFile file) {
        require(applicationId);
        return store(applicationId, docType, file);
    }

    private ScholarshipApplication requireForApplicant(Long applicationId, String mobile) {
        String m = mobile == null ? "" : mobile.replaceAll("\\D", "");
        ScholarshipApplication a = applicationId == null ? null : applications.findById(applicationId).orElse(null);
        if (a == null || m.length() < 7
                || !(sameMobile(m, a.getStudentMobile()) || sameMobile(m, a.getFatherMobile()) || sameMobile(m, a.getMotherMobile()))) {
            throw new ApiException(HttpStatus.NOT_FOUND, "No application matches that application number and mobile number.");
        }
        return a;
    }

    /** Compares digits only, and the last 10 digits when either side has a country code (+91...). */
    private static boolean sameMobile(String typedDigits, String stored) {
        if (stored == null) return false;
        String s = stored.replaceAll("\\D", "");
        if (s.isEmpty()) return false;
        if (s.equals(typedDigits)) return true;
        return s.length() >= 10 && typedDigits.length() >= 10
                && s.substring(s.length() - 10).equals(typedDigits.substring(typedDigits.length() - 10));
    }

    private Map<String, Object> store(Long applicationId, String docType, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Please choose a file to upload.");
        String type = DOCUMENT_TYPES.contains(docType) ? docType : "Other";
        String original = fileStorageService.sanitizeFilename(file.getOriginalFilename());
        int dot = original.lastIndexOf('.');
        String ext = dot >= 0 ? original.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only PDF, image (JPG, PNG, WEBP) or Word files can be uploaded.");
        }
        if (documentRepository.countByApplicationId(applicationId) >= MAX_DOCUMENTS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "An application can have at most " + MAX_DOCUMENTS + " documents.");
        }
        String stored = fileStorageService.store(file, "app_" + applicationId + "_" + System.currentTimeMillis() + "_" + original);
        ApplicationDocument doc = new ApplicationDocument();
        doc.setApplicationId(applicationId);
        doc.setDocType(type);
        doc.setFileName(file.getOriginalFilename() == null ? original : file.getOriginalFilename());
        doc.setFilePath(stored);
        documentRepository.save(doc);
        Map<String, Object> row = documentRow(doc);
        row.remove("filePath");
        return row;
    }

    // ---------------------------------------------------------------- rows

    private static List<Map<String, Object>> historyRows(List<ApplicationStatus> history) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ApplicationStatus s : history) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("status", label(s.getStatus()));
            row.put("comments", s.getComments());
            row.put("date", s.getCreatedAt());
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> documentRows(Long applicationId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ApplicationDocument d : documentRepository.findByApplicationIdOrderByDocumentIdDesc(applicationId)) {
            rows.add(documentRow(d));
        }
        return rows;
    }

    private static Map<String, Object> documentRow(ApplicationDocument d) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", d.getDocumentId());
        row.put("docType", d.getDocType());
        row.put("fileName", d.getFileName());
        row.put("filePath", d.getFilePath());
        row.put("uploadedAt", d.getCreatedAt());
        return row;
    }
}
