package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
import com.rahbar.util.Rows;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.*;

import static com.rahbar.service.ServiceSupport.*;

/** Coordinator (role 3) screens. */
@Service
public class CoordinatorService {

    private static final List<String> PAYMENT_SORT_COLUMNS = List.of("grantee_name", "grantor_name", "amount", "status", "receipt_url");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final GranteeDetailsRepository granteeDetailsRepository;
    private final GrantorGranteeRepository grantorGranteeRepository;
    private final ApplicationStatusRepository applicationStatusRepository;
    private final PaymentRepository paymentRepository;
    private final SponsorMappingService sponsorMappingService;
    private final ReportService reportService;

    public CoordinatorService(UserRepository userRepository, RoleRepository roleRepository,
                              GranteeDetailsRepository granteeDetailsRepository,
                              GrantorGranteeRepository grantorGranteeRepository,
                              ApplicationStatusRepository applicationStatusRepository,
                              PaymentRepository paymentRepository, SponsorMappingService sponsorMappingService,
                              ReportService reportService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.granteeDetailsRepository = granteeDetailsRepository;
        this.grantorGranteeRepository = grantorGranteeRepository;
        this.applicationStatusRepository = applicationStatusRepository;
        this.paymentRepository = paymentRepository;
        this.sponsorMappingService = sponsorMappingService;
        this.reportService = reportService;
    }

    public Map<String, Object> dashboard(String coordinatorId, Integer year) {
        List<Integer> years = yearsDesc(
                granteeDetailsRepository.findCreatedYears(),
                userRepository.findCreatedYearsByRoleIdIn(SPONSOR_CONVENOR_ROLES),
                userRepository.findCreatedYearsByRoleIdIn(List.of(STUDENT_ROLE)));
        int selectedYear = year != null ? year : (years.isEmpty() ? Year.now().getValue() : years.get(0));

        // Chart data: latest status of the year's applications, and that year's sponsors per region.
        Map<Long, ApplicationStatus> latest = latestByApplication(applicationStatusRepository.findLatestPerApplication());
        List<Map<String, Object>> byStatus = countBy(granteeDetailsRepository.findCreatedInYear(selectedYear), gd -> {
            ApplicationStatus s = latest.get(gd.getGranteeDetailId());
            return s == null || s.getStatus() == null ? "no status" : s.getStatus();
        });
        List<Map<String, Object>> byRegion = countBy(userRepository.findByRoleIdCreatedInYear(5, selectedYear),
                u -> regionLabel(u.getRegion()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("coordinator", Rows.pick(requireUser(userRepository, coordinatorId, "User not found"),
                "user_id", "name", "email", "phone"));
        result.put("applicationsByStatus", byStatus);
        result.put("sponsorsByRegion", byRegion);
        result.put("availableYears", years);
        result.put("selectedYear", selectedYear);
        result.put("applicationsCount", granteeDetailsRepository.countCreatedInYear(selectedYear));
        result.put("sponsorsCount", userRepository.countByRoleIdCreatedInYear(5, selectedYear));
        result.put("granteesCount", userRepository.countByRoleIdCreatedInYear(STUDENT_ROLE, selectedYear));
        return result;
    }

    /** Every application with the applicant's name and its latest status / comments. */
    public List<Map<String, Object>> applications() {
        Map<Long, ApplicationStatus> latest = latestByApplication(applicationStatusRepository.findLatestPerApplication());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object[] r : granteeDetailsRepository.findAllWithApplicantName()) {
            GranteeDetails gd = (GranteeDetails) r[0];
            ApplicationStatus s = latest.get(gd.getGranteeDetailId());
            Map<String, Object> row = Rows.of(gd);
            row.put("applicant_name", r[1]);
            row.put("status", s == null ? null : s.getStatus());
            row.put("comments", s == null ? null : s.getComments());
            rows.add(row);
        }
        return rows;
    }

    public void assignSponsor(String granteeId, String grantorId) {
        sponsorMappingService.map(granteeId, grantorId, "Assigned", true);
    }

    /** Sets a user's status; an inactive sponsor's students go back to the unassigned grantor. */
    public void updateUserStatus(String userId, String status) {
        userRepository.findById(userId).ifPresent(u -> {
            u.setStatus(status);
            userRepository.save(u);
        });
        if ("Inactive".equalsIgnoreCase(status)) {
            List<GrantorGrantee> mappings = grantorGranteeRepository.findByGrantorId(userId);
            mappings.forEach(gg -> {
                gg.setGrantorId(UNASSIGNED_GRANTOR);
                gg.setStatus("Unassigned");
            });
            grantorGranteeRepository.saveAll(mappings);
        }
    }

    public Map<String, Object> mapStudentsScreen(String sponsorId) {
        List<Map<String, Object>> mapped = Rows.list(userRepository.findGranteesOf(sponsorId));
        return Map.of("students", Rows.list(userRepository.findGranteesOf(UNASSIGNED_GRANTOR)),
                "mappedStudents", mapped,
                "mappedStudentIds", mapped.stream().map(m -> m.get("user_id")).toList());
    }

    /** Moves each student's existing mapping to the sponsor. */
    public void mapStudents(String sponsorId, List<String> studentIds) {
        for (String studentId : studentIds) {
            grantorGranteeRepository.findFirstByGranteeId(studentId).ifPresent(gg -> {
                gg.setGrantorId(sponsorId);
                grantorGranteeRepository.save(gg);
            });
        }
    }

    /** Convenors and sponsors with their role name and description. */
    public List<Map<String, Object>> sponsorsConvenors() {
        Map<Integer, Role> roles = new HashMap<>();
        roleRepository.findAll().forEach(r -> roles.put(r.getRoleId(), r));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (User u : userRepository.findByRoleIdIn(SPONSOR_CONVENOR_ROLES)) {
            Role role = roles.get(u.getRoleId());
            if (role == null) continue;
            Map<String, Object> row = Rows.of(u);
            row.put("role_name", role.getRoleName());
            row.put("description", role.getDescription());
            rows.add(row);
        }
        return rows;
    }

    public Map<String, Object> manageSponsors() {
        return Map.of("sponsorsConvenors", sponsorsConvenors(),
                "grantees", Rows.list(userRepository.findByRoleId(STUDENT_ROLE)));
    }

    public void appointConvenor(String sponsorId, String region) {
        User user = requireUser(userRepository, sponsorId, "User not found");
        user.setRoleId(4);
        user.setRegion(region);
        userRepository.save(user);
    }

    public void changeRegion(String userId, String region) {
        User user = requireUser(userRepository, userId, "User not found");
        user.setRegion(region);
        userRepository.save(user);
    }

    public void assignStudentsBulk(String sponsorId, List<String> studentIds) {
        for (String studentId : studentIds) {
            sponsorMappingService.map(studentId, sponsorId, "Assigned", true);
        }
    }

    /** Paged, searchable, sortable list of all payments with student and sponsor names (DataTables shape). */
    public Map<String, Object> monitorPayments(int start, int length, String search, String orderBy, String orderDir) {
        String column = PAYMENT_SORT_COLUMNS.contains(orderBy) ? orderBy : "grantee_name";
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object[] r : paymentRepository.findAllWithUsers()) {
            Payment p = (Payment) r[0];
            User grantee = (User) r[1];
            User grantor = (User) r[2];
            if (!isBlank(search) && !(like(grantee.getName(), search) || like(grantee.getUserId(), search)
                    || like(grantee.getPhone(), search) || (grantor != null && (like(grantor.getName(), search)
                    || like(grantor.getUserId(), search) || like(grantor.getPhone(), search)))
                    || like(p.getStatus(), search))) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("grantee_name", grantee.getName());
            row.put("grantee_id", grantee.getUserId());
            row.put("grantee_phone", grantee.getPhone());
            row.put("grantor_name", grantor == null ? null : grantor.getName());
            row.put("grantor_id", p.getGrantorId());
            row.put("grantor_phone", grantor == null ? null : grantor.getPhone());
            row.put("payment_date", p.getPaymentDate());
            row.put("amount", p.getAmount());
            row.put("status", p.getStatus());
            row.put("receipt_url", p.getReceiptUrl());
            row.put("payment_id", p.getPaymentId());
            rows.add(row);
        }
        rows.sort(byColumn(column, "desc".equalsIgnoreCase(orderDir)));

        int from = Math.min(Math.max(start, 0), rows.size());
        int to = Math.min(from + Math.max(length, 0), rows.size());
        return Map.of("recordsTotal", paymentRepository.count(),
                "recordsFiltered", (long) rows.size(),
                "data", new ArrayList<>(rows.subList(from, to)));
    }

    public ReportService.Report report(String reportType, String format) {
        List<Map<String, Object>> data = new ArrayList<>();
        switch (reportType) {
            case "applications" -> {
                for (Object[] r : granteeDetailsRepository.findAllWithStatusHistory()) {
                    Map<String, Object> row = Rows.of(r[0]);
                    row.putAll(Rows.of(r[1]));
                    data.add(row);
                }
            }
            case "payments" -> data = Rows.list(paymentRepository.findAll());
            case "sponsors_convenors" -> data = Rows.list(userRepository.findByRoleIdIn(SPONSOR_CONVENOR_ROLES));
            case "grantees" -> data = Rows.list(userRepository.findByRoleId(STUDENT_ROLE));
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid report type");
        }
        return reportService.build(data, reportType + "_report", format, reportType);
    }
}
