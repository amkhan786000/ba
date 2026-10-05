package com.rahbar.service;

import com.rahbar.entity.ApplicationDocument;
import com.rahbar.entity.ApplicationStatus;
import com.rahbar.entity.GranteeDetails;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.ApplicationDocumentRepository;
import com.rahbar.repository.ApplicationStatusRepository;
import com.rahbar.repository.GranteeDetailsRepository;
import com.rahbar.util.Rows;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Scholarship applications after submission: status changes, interview scheduling, supporting documents,
 * the staff detail view and the public "track my application" page.
 */
@Service
public class ApplicationService {

    public static final Set<String> VALID_STATUSES = Set.of(
            "draft", "submitted", "interviewing", "accepted", "rejected",
            "on hold", "provisional admission letter", "admitted");

    public static final List<String> DOCUMENT_TYPES = List.of(
            "Marksheet", "ID proof", "Income certificate", "Photo", "Admission letter", "Other");

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png", "webp", "doc", "docx");
    private static final int MAX_DOCUMENTS = 10;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a");

    private final GranteeDetailsRepository granteeDetailsRepository;
    private final ApplicationStatusRepository applicationStatusRepository;
    private final ApplicationDocumentRepository documentRepository;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;

    public ApplicationService(GranteeDetailsRepository granteeDetailsRepository,
                              ApplicationStatusRepository applicationStatusRepository,
                              ApplicationDocumentRepository documentRepository, FileStorageService fileStorageService,
                              NotificationService notificationService) {
        this.granteeDetailsRepository = granteeDetailsRepository;
        this.applicationStatusRepository = applicationStatusRepository;
        this.documentRepository = documentRepository;
        this.fileStorageService = fileStorageService;
        this.notificationService = notificationService;
    }

    private GranteeDetails require(Long applicationId) {
        return granteeDetailsRepository.findById(applicationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Application not found."));
    }

    // ---------------------------------------------------------------- staff actions

    /** Adds a status row (the history is kept) and tells the applicant if they have an account. */
    public void updateStatus(Long applicationId, String status, String comments) {
        if (status == null || !VALID_STATUSES.contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid status selected!");
        }
        GranteeDetails gd = require(applicationId);
        addStatus(applicationId, status, comments);
        notificationService.notify(gd.getUserId(), "Application update",
                "Your scholarship application #" + applicationId + " is now '" + status + "'."
                        + (comments == null || comments.isBlank() ? "" : " Note: " + comments.trim()),
                NotificationService.APPLICATION, null, true);
    }

    /** Sets the interview date / venue and moves the application to "interviewing". */
    public void scheduleInterview(Long applicationId, String interviewAt, String venue) {
        GranteeDetails gd = require(applicationId);
        LocalDateTime when = ServiceSupport.parseDateTime(interviewAt);
        if (when == null) throw new ApiException(HttpStatus.BAD_REQUEST, "Please choose the interview date and time.");
        if (venue == null || venue.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "Please enter the interview venue.");
        gd.setInterviewAt(when);
        gd.setInterviewVenue(venue.trim());
        granteeDetailsRepository.save(gd);

        String text = "Interview on " + when.format(WHEN) + " at " + venue.trim();
        addStatus(applicationId, "interviewing", text);
        notificationService.notify(gd.getUserId(), "Interview scheduled",
                "Your scholarship interview is scheduled: " + text + ".", NotificationService.APPLICATION, null, true);
    }

    private void addStatus(Long applicationId, String status, String comments) {
        ApplicationStatus as = new ApplicationStatus();
        as.setGranteeDetailId(applicationId);
        as.setStatus(status);
        as.setComments(comments == null || comments.isBlank() ? null : comments.trim());
        applicationStatusRepository.save(as);
    }

    /** Full application for the staff detail page: fields, latest status, status history and documents. */
    public Map<String, Object> details(Long applicationId) {
        GranteeDetails gd = require(applicationId);
        List<ApplicationStatus> history = applicationStatusRepository.findByGranteeDetailIdOrderByCreatedAtDesc(applicationId);
        Map<String, Object> app = Rows.of(gd);
        ApplicationStatus latest = history.isEmpty() ? null : history.get(0);
        app.put("status", latest == null ? null : latest.getStatus());
        app.put("comments", latest == null ? null : latest.getComments());
        app.put("status_date", latest == null ? null : latest.getCreatedAt());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("application", app);
        result.put("history", historyRows(history));
        result.put("documents", documentRows(applicationId));
        result.put("documentTypes", DOCUMENT_TYPES);
        return result;
    }

    // ---------------------------------------------------------------- public tracking

    /** Public tracker: needs the application id and one of the three mobile numbers given on the form. */
    public Map<String, Object> track(Long applicationId, String mobile) {
        GranteeDetails gd = requireForApplicant(applicationId, mobile);
        List<ApplicationStatus> history = applicationStatusRepository.findByGranteeDetailIdOrderByCreatedAtDesc(applicationId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("applicationId", gd.getGranteeDetailId());
        result.put("name", gd.getName());
        result.put("courseApplied", gd.getCourseApplied());
        result.put("rccName", gd.getRccName());
        result.put("submittedAt", gd.getCreatedAt());
        result.put("status", history.isEmpty() ? "submitted" : history.get(0).getStatus());
        result.put("interviewAt", gd.getInterviewAt());
        result.put("interviewVenue", gd.getInterviewVenue());
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

    private GranteeDetails requireForApplicant(Long applicationId, String mobile) {
        String m = mobile == null ? "" : mobile.replaceAll("\\D", "");
        GranteeDetails gd = applicationId == null ? null : granteeDetailsRepository.findById(applicationId).orElse(null);
        if (gd == null || m.length() < 7
                || !(sameMobile(m, gd.getStudentMobile()) || sameMobile(m, gd.getFatherMobile()) || sameMobile(m, gd.getMotherMobile()))) {
            throw new ApiException(HttpStatus.NOT_FOUND, "No application matches that application number and mobile number.");
        }
        return gd;
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
        if (documentRepository.countByGranteeDetailId(applicationId) >= MAX_DOCUMENTS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "An application can have at most " + MAX_DOCUMENTS + " documents.");
        }
        String stored = fileStorageService.store(file, "app_" + applicationId + "_" + System.currentTimeMillis() + "_" + original);
        ApplicationDocument doc = new ApplicationDocument();
        doc.setGranteeDetailId(applicationId);
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
            row.put("status", s.getStatus());
            row.put("comments", s.getComments());
            row.put("date", s.getCreatedAt());
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> documentRows(Long applicationId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ApplicationDocument d : documentRepository.findByGranteeDetailIdOrderByDocumentIdDesc(applicationId)) {
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
