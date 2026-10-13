package com.rahbar.service;

import com.rahbar.entity.GranteeDetails;
import com.rahbar.repository.GranteeDetailsRepository;
import com.rahbar.security.StudentScope;
import java.util.ArrayList;
import java.util.Map;

import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.security.Access;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Who may open an uploaded file. Staff (every signed-in role other than sponsor and student) may open any file,
 * except chapter / RCC-scoped staff, who may only open their own students' files. Rahbar-e-Banaat documents
 * (files named reb_...) need "REB: private details & documents", whatever the role.
 * A student may open receipts / proofs of their own payments, their own progress reports and their own
 * application documents. A sponsor may open receipts / proofs of payments they made and the progress reports
 * of the students mapped to them.
 */
@Service
public class FileAccessService {

    private static final int SPONSOR = 5, STUDENT = 6;

    private final JdbcTemplate jdbc;
    private final FileStorageService fileStorageService;

    private final StudentScope studentScope;
    private final GranteeDetailsRepository granteeDetailsRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private com.rahbar.repository.ScholarshipApplicationRepository scholarshipApplications;

    public FileAccessService(JdbcTemplate jdbc, FileStorageService fileStorageService, StudentScope studentScope,
                             GranteeDetailsRepository granteeDetailsRepository) {
        this.studentScope = studentScope;
        this.granteeDetailsRepository = granteeDetailsRepository;
        this.jdbc = jdbc;
        this.fileStorageService = fileStorageService;
    }

    /** The file on disk, or 404 when it doesn't exist / the user may not see it (no hint either way). */
    public Path requireReadable(String name) {
        String file = name == null ? "" : Path.of(name).getFileName().toString();
        ApiException notFound = new ApiException(HttpStatus.NOT_FOUND, "File not found.");
        if (file.isBlank() || file.startsWith(".")) throw notFound;
        Path path = fileStorageService.resolve(file);
        if (!Files.isRegularFile(path) || !mayRead(Access.current().getUser(), file)) throw notFound;
        return path;
    }

    private boolean mayRead(User me, String file) {
        // Stored paths may carry a folder (legacy rows): match on the file name at the end.
        String like = "%/" + file, likeWin = "%\\\\" + file;
        // Rahbar-e-Banaat documents and photos (minors): only people with "REB: private details & documents".
        if (file.startsWith("reb_") || exists("select 1 from reb_student_documents where file_path = ?", file)) {
            return Access.can(com.rahbar.security.Section.REB_STUDENT_PRIVATE, com.rahbar.security.Section.Level.VIEW);
        }
        // Someone can be staff and a sponsor (or student) at once: any of their roles that allows the file is enough.
        java.util.Set<Integer> roles = Access.current().getRoleIds();
        if (roles.stream().anyMatch(r -> r != SPONSOR && r != STUDENT) && staffMayRead(file, like, likeWin)) return true;
        Long id = me.getId();
        if (roles.contains(STUDENT) && studentMayRead(id, file, like, likeWin)) return true;
        return roles.contains(SPONSOR) && sponsorMayRead(id, file, like, likeWin);
    }

    /** A student: their own payment receipts / proofs, progress reports and application documents. */
    private boolean studentMayRead(Long id, String file, String like, String likeWin) {
        return exists("""
                select 1 from payments where grantee_id = ? and (receipt_url in (?, ?) or student_proof_url in (?, ?)
                  or receipt_url like ? or student_proof_url like ? or receipt_url like ? or student_proof_url like ?)
                """, id, file, "uploads/" + file, file, "uploads/" + file, like, like, likeWin, likeWin)
                || exists("select 1 from student_progress where grantee_id = ? and (file_path = ? or file_path like ? or file_path like ?)",
                    id, file, like, likeWin)
                || exists("""
                select 1 from application_documents d join grantee_details g on g.grantee_detail_id = d.grantee_detail_id
                 where g.user_id = ? and (d.file_path = ? or d.file_path like ? or d.file_path like ?)
                """, id, file, like, likeWin)
                // documents of their scholarship application (once accepted)
                || exists("""
                select 1 from application_documents d join scholarship_applications a on a.id = d.application_id
                 where a.student_user_id = ? and (d.file_path = ? or d.file_path like ? or d.file_path like ?)
                """, id, file, like, likeWin);
    }

    /** A sponsor: receipts of their payments and progress reports of their (past) students. */
    private boolean sponsorMayRead(Long id, String file, String like, String likeWin) {
        return exists("""
                select 1 from payments where grantor_id = ? and (receipt_url in (?, ?) or student_proof_url in (?, ?)
                  or receipt_url like ? or student_proof_url like ? or receipt_url like ? or student_proof_url like ?)
                """, id, file, "uploads/" + file, file, "uploads/" + file, like, like, likeWin, likeWin)
                || exists("""
                select 1 from student_progress p join grantor_grantees gg on gg.grantee_id = p.grantee_id
                 where gg.grantor_id = ? and (p.file_path = ? or p.file_path like ? or p.file_path like ?)
                """, id, file, like, likeWin)
                // a past student's reports, uploaded while this sponsor was their sponsor
                || exists("""
                select 1 from student_progress p join sponsor_mapping_history h on h.grantee_id = p.grantee_id
                 where h.grantor_id = ? and (h.ended_at is null or p.created_at <= h.ended_at)
                   and (p.file_path = ? or p.file_path like ? or p.file_path like ?)
                """, id, file, like, likeWin);
    }

    /**
     * Staff: everything, except chapter / RCC-scoped users (e.g. a Chapter Lead), who may only open files of their own
     * students: payment receipts / proofs, progress reports and application documents.
     */
    private boolean staffMayRead(String file, String like, String likeWin) {
        StudentScope.Scope scope = studentScope.current();
        if (scope.all()) return true;
        if (scope.none()) return false;
        Map<Long, User> users = studentScope.usersById();
        List<Long> students = new ArrayList<>(jdbc.queryForList("""
                select grantee_id from payments where receipt_url in (?, ?) or student_proof_url in (?, ?)
                  or receipt_url like ? or student_proof_url like ? or receipt_url like ? or student_proof_url like ?
                """, Long.class, file, "uploads/" + file, file, "uploads/" + file, like, like, likeWin, likeWin));
        students.addAll(jdbc.queryForList("select grantee_id from student_progress where file_path = ? or file_path like ? or file_path like ?",
                Long.class, file, like, likeWin));
        for (Long s : students) if (scope.includesId(s, users)) return true;
        List<Long> apps = jdbc.queryForList("select application_id from application_documents where application_id is not null "
                + "and (file_path = ? or file_path like ? or file_path like ?)", Long.class, file, like, likeWin);
        for (Long a : apps) {
            com.rahbar.entity.ScholarshipApplication app = scholarshipApplications.findById(a).orElse(null);
            if (app != null && scope.includesApplication(app, users)) return true;
        }
        return false;
    }

    private boolean exists(String sql, Object... args) {
        List<Integer> rows = jdbc.queryForList(sql + " limit 1", Integer.class, args);
        return !rows.isEmpty();
    }
}
