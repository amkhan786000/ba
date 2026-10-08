package com.rahbar.service;

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
 * Who may open an uploaded file. Staff (every signed-in role other than sponsor and student) may open any file.
 * A student may open receipts / proofs of their own payments, their own progress reports and their own
 * application documents. A sponsor may open receipts / proofs of payments they made and the progress reports
 * of the students mapped to them.
 */
@Service
public class FileAccessService {

    private static final int SPONSOR = 5, STUDENT = 6;

    private final JdbcTemplate jdbc;
    private final FileStorageService fileStorageService;

    public FileAccessService(JdbcTemplate jdbc, FileStorageService fileStorageService) {
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
        Integer role = me.getRoleId();
        if (role == null || (role != SPONSOR && role != STUDENT)) return true; // staff
        Long id = me.getId();
        // Stored paths may carry a folder (legacy rows): match on the file name at the end.
        String like = "%/" + file, likeWin = "%\\\\" + file;
        if (role == STUDENT) {
            return exists("""
                select 1 from payments where grantee_id = ? and (receipt_url in (?, ?) or student_proof_url in (?, ?)
                  or receipt_url like ? or student_proof_url like ? or receipt_url like ? or student_proof_url like ?)
                """, id, file, "uploads/" + file, file, "uploads/" + file, like, like, likeWin, likeWin)
                || exists("select 1 from student_progress where grantee_id = ? and (file_path = ? or file_path like ? or file_path like ?)",
                    id, file, like, likeWin)
                || exists("""
                select 1 from application_documents d join grantee_details g on g.grantee_detail_id = d.grantee_detail_id
                 where g.user_id = ? and (d.file_path = ? or d.file_path like ? or d.file_path like ?)
                """, id, file, like, likeWin);
        }
        // Sponsor
        return exists("""
                select 1 from payments where grantor_id = ? and (receipt_url in (?, ?) or student_proof_url in (?, ?)
                  or receipt_url like ? or student_proof_url like ? or receipt_url like ? or student_proof_url like ?)
                """, id, file, "uploads/" + file, file, "uploads/" + file, like, like, likeWin, likeWin)
                || exists("""
                select 1 from student_progress p join grantor_grantees gg on gg.grantee_id = p.grantee_id
                 where gg.grantor_id = ? and (p.file_path = ? or p.file_path like ? or p.file_path like ?)
                """, id, file, like, likeWin);
    }

    private boolean exists(String sql, Object... args) {
        List<Integer> rows = jdbc.queryForList(sql + " limit 1", Integer.class, args);
        return !rows.isEmpty();
    }
}
