package com.rahbar.service;

import com.rahbar.entity.Chapter;
import com.rahbar.entity.Role;
import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.ChapterRepository;
import com.rahbar.repository.RoleRepository;
import com.rahbar.repository.UserRepository;
import com.rahbar.security.Access;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

/**
 * Administration > Manage Users > Bulk add users: a CSV with Name, Chapter Name, Phone Number and Role adds one
 * account per row (any role but Student, which has its own upload in the Student Directory). A name like
 * "Abdul Rahman c/o Imran Khan" is saved as the name "Abdul Rahman" with c/o "Imran Khan" (who brought them).
 * <ul>
 *   <li>Name, Chapter Name and Role are required; Chapter Name must be an existing chapter (ignoring case).</li>
 *   <li>Any phone number is taken as typed (spaces / dashes removed; an Indian one as its 10 digits, others with "+").</li>
 *   <li>An empty phone number gets a generated one starting with 5: Indian mobiles start with 6 to 9, so it can never be
 *   someone's real number (Data Quality lists it as invalid until the real one is entered).</li>
 *   <li>A name that looks like someone already in Rahbar in the same chapter (any role), or like an earlier row of the file
 *   with the same chapter, is not added and is listed as a possible duplicate to check: names are compared ignoring
 *   case, dots and spaces, with Md / Mohd / Mohammad / Muhammad treated as the same; no chapter only matches no
 *   chapter. A phone number another account has is skipped too.</li>
 *   <li>Each account gets the next user ID, the sign-in email &lt;user ID&gt;@rahbar.com (until they add a real one) and
 *   the default temporary password, which they must change at their first sign-in.</li>
 * </ul>
 */
@Service
public class BulkUserService {

    static final int STUDENT_ROLE = 6;
    static final String PLACEHOLDER_DOMAIN = "@rahbar.com";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final RoleRepository roles;
    private final ChapterRepository chapters;
    private final PasswordEncoder passwordEncoder;

    public BulkUserService(UserRepository users, RoleRepository roles, ChapterRepository chapters, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.roles = roles;
        this.chapters = chapters;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * The upload's result: the row report, every account added (to hand out their sign-in details), and the rows not
     * added because the name looks like someone who exists (to check by hand).
     */
    public record Result(BulkUploadReport report, List<Map<String, Object>> added, List<Map<String, Object>> duplicates) {}

    public Result upload(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a CSV file.");
        byte[] raw = file.getBytes();
        String text = new String(raw, StandardCharsets.UTF_8);
        if (text.contains("�")) text = new String(raw, StandardCharsets.ISO_8859_1);
        if (text.startsWith("﻿")) text = text.substring(1);

        Map<String, Role> roleByName = new HashMap<>();
        for (Role r : roles.findAll()) roleByName.put(key(r.getRoleName()), r);
        Set<String> phonesTaken = new HashSet<>();
        users.findAll().forEach(u -> { if (u.getPhone() != null && !u.getPhone().isBlank()) phonesTaken.add(digits(u.getPhone())); });
        long nextCode = users.findAllUserIds().stream().filter(id -> id != null && id.matches("\\d{1,15}"))
                .mapToLong(Long::parseLong).max().orElse(1000) + 1;

        BulkUploadReport report = new BulkUploadReport();
        List<Map<String, Object>> added = new ArrayList<>(), duplicates = new ArrayList<>();
        // everyone already in Rahbar by name (canonical form) and chapter, and the names + chapters seen earlier in this file
        Map<String, List<String>> existingByName = new HashMap<>();
        Map<Long, String> chapterNames = new HashMap<>();
        chapters.findAll().forEach(c -> chapterNames.put(c.getChapterId(), c.getChapterName()));
        Map<Integer, String> roleNames = new HashMap<>();
        roles.findAll().forEach(r -> roleNames.put(r.getRoleId(), r.getRoleName()));
        for (User u : users.findAll()) {
            if (u.getName() == null || u.getMergedIntoId() != null) continue;
            existingByName.computeIfAbsent(nameInChapter(u.getName(), u.getChapterId()), k -> new ArrayList<>()).add(u.getName() + " (" + Objects.requireNonNullElse(u.getUserId(), "no ID")
                    + ", " + roleNames.getOrDefault(u.getRoleId(), "?") + (u.getChapterId() == null ? "" : ", " + chapterNames.get(u.getChapterId())) + ")");
        }
        // name + chapter of the rows added so far (a row that failed or was skipped doesn't count)
        Map<String, Integer> fileRows = new HashMap<>();
        try (CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true)
                .setTrim(true).setAllowMissingColumnNames(true).build().parse(new StringReader(text))) {
            Map<String, String> columns = new HashMap<>(); // normalised -> header as in the file
            for (String h : parser.getHeaderNames()) columns.put(key(h), h);
            String nameCol = column(columns, "name", "fullname"), roleCol = column(columns, "role", "rolename");
            String chapterCol = column(columns, "chaptername", "chapter"), phoneCol = column(columns, "phonenumber", "phone", "mobile", "mobilenumber");
            if (nameCol == null || chapterCol == null || roleCol == null || phoneCol == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "The file needs the columns Name, Chapter Name, Phone Number and Role. "
                        + "Download the template from the upload window.");
            }

            for (CSVRecord row : parser) {
                int rowNumber = (int) row.getRecordNumber() + 1; // +1 for the header line
                report.row();
                String name = value(row, nameCol);
                String reference = name;
                try {
                    String roleName = value(row, roleCol), chapterName = value(row, chapterCol), phone = value(row, phoneCol);
                    if (name == null && roleName == null && chapterName == null && phone == null) { report.skipped(rowNumber, null, "Empty row."); continue; }
                    // Name, Chapter Name and Role are required; the phone number is optional
                    List<String> missing = new ArrayList<>();
                    if (name == null) missing.add("Name");
                    if (chapterName == null) missing.add("Chapter Name");
                    if (roleName == null) missing.add("Role");
                    if (!missing.isEmpty()) throw bad(String.join(", ", missing) + (missing.size() == 1 ? " is" : " are") + " required.");
                    // "Abdul Rahman c/o Imran Khan": the name, and who brought them (c/o)
                    String[] split = splitCareOf(name);
                    String fullName = name;
                    name = split[0];
                    String careOf = split[1];
                    if (name.isEmpty()) throw bad("No name before c/o in '" + fullName + "'.");
                    if (name.length() > 150) throw bad("The name is longer than 150 characters.");
                    if (careOf != null && careOf.length() > 150) throw bad("The c/o is longer than 150 characters.");

                    Role role = roleName == null ? null : roleByName.get(key(roleName));
                    if (role == null) throw bad("Unknown role '" + Objects.requireNonNullElse(roleName, "") + "'. Use a role name from Roles & Permissions, e.g. Sponsor or Chapter Lead.");
                    if (role.getRoleId() == STUDENT_ROLE) throw bad("Students are added in Student Directory > Bulk upload (they need more details).");
                    if (role.getRoleId() == Role.SUPER_ADMIN && !Access.isSuperAdmin()) throw bad("Only a Super Admin can add Super Admins.");

                    Long chapterId = null;
                    if (chapterName != null) {
                        chapterId = chapters.findByChapterNameIgnoreCase(chapterName.trim()).map(Chapter::getChapterId)
                                .orElseThrow(() -> bad("Chapter '" + chapterName + "' doesn't exist. Add it in Chapters first, or fix the spelling."));
                    }

                    String canon = nameInChapter(name, chapterId);
                    List<String> alike = existingByName.getOrDefault(canon, List.of());
                    Integer earlier = fileRows.get(canon);
                    if (!alike.isEmpty() || earlier != null) {
                        Map<String, Object> d = new LinkedHashMap<>();
                        d.put("row", rowNumber);
                        d.put("name", name);
                        d.put("role", role.getRoleName());
                        d.put("chapter", chapterName);
                        d.put("phone", phone);
                        d.put("looks_like", !alike.isEmpty() ? String.join("; ", alike) : "row " + earlier + " of this file");
                        duplicates.add(d);
                        report.skipped(rowNumber, reference, "Possible duplicate, not added: looks like " + d.get("looks_like") + ".");
                        continue;
                    }

                    String typedPhone = phone(phone);
                    boolean generated = typedPhone == null; // blank, or no digits at all (e.g. "NA")
                    String mobile;
                    if (generated) {
                        mobile = placeholderPhone(phonesTaken);
                    } else {
                        mobile = typedPhone;
                        if (phonesTaken.contains(digits(mobile))) {
                            report.skipped(rowNumber, reference, "Phone " + mobile + " already belongs to another account.");
                            continue;
                        }
                    }

                    String code = String.valueOf(nextCode++);
                    while (users.existsByUserId(code)) code = String.valueOf(nextCode++);
                    User u = new User();
                    u.setUserId(code);
                    u.setName(name);
                    u.setPhone(mobile);
                    u.setEmail(code.toLowerCase(Locale.ROOT) + PLACEHOLDER_DOMAIN);
                    u.setRoleId(role.getRoleId());
                    u.setChapterId(chapterId);
                    u.setCareOf(careOf);
                    u.setSex("M");
                    u.setStatus("Active");
                    u.setPasswordHash(passwordEncoder.encode(PasswordPolicy.DEFAULT_PASSWORD));
                    u.setMustChangePassword(true);
                    users.save(u);
                    phonesTaken.add(digits(mobile));
                    fileRows.put(canon, rowNumber);
                    report.created();
                    if (generated) report.warning(rowNumber, reference, "No phone number, so " + mobile + " was generated (not a real number). Add the real one in Manage Users.");

                    Map<String, Object> a = new LinkedHashMap<>();
                    a.put("user_id", code);
                    a.put("name", name);
                    a.put("care_of", careOf);
                    a.put("role", role.getRoleName());
                    a.put("chapter", chapterName);
                    a.put("phone", mobile);
                    a.put("phone_generated", generated);
                    a.put("sign_in", u.getEmail());
                    added.add(a);
                } catch (Exception e) {
                    report.failed(rowNumber, reference, e);
                }
            }
        }
        return new Result(report, added, duplicates);
    }

    private static final java.util.regex.Pattern CARE_OF = java.util.regex.Pattern.compile("(?i)^(.*?)[\\s,(]+(?:c\\s*/\\s*o|care\\s+of)[\\s.:-]+(.+?)\\)?$");

    /** "Abdul Rahman c/o Imran Khan" (also C/O, c / o, care of) -> ["Abdul Rahman", "Imran Khan"]; no c/o -> [name, null]. */
    static String[] splitCareOf(String name) {
        java.util.regex.Matcher m = CARE_OF.matcher(name.trim());
        if (!m.matches()) return new String[]{name.trim(), null};
        String careOf = m.group(2).trim();
        return new String[]{m.group(1).trim(), careOf.isEmpty() ? null : careOf};
    }

    /** The duplicate key: the canonical name together with the chapter (no chapter only matches no chapter). */
    static String nameInChapter(String name, Long chapterId) {
        return canonical(name) + "|" + (chapterId == null ? "-" : chapterId);
    }

    /** A name to compare: lower case, no dots or extra spaces, Md / Mohd / Mohammad / Muhammad... as "md". */
    static String canonical(String name) {
        if (name == null) return "";
        List<String> words = new ArrayList<>();
        for (String w : name.toLowerCase(Locale.ROOT).replaceAll("[.,'`]", " ").trim().split("\\s+")) {
            if (w.isEmpty()) continue;
            words.add(w.matches("m(o|u)?h?d|m[ou]hamm?[ae]d|mohammed|muhammed|mohamad|muhamad") ? "md" : w);
        }
        return String.join(" ", words);
    }

    /** The template offered in the upload window. */
    public static String template() {
        return "Name,Chapter Name,Phone Number,Role\n"
                + "Abdul Rahman,Jeddah,9800000001,Sponsor\n"
                + "Sara Khan,Patna,,Chapter Lead\n";
    }

    /** A 10-digit number starting with 5 that no account has (never a real Indian mobile). */
    static String placeholderPhone(Set<String> taken) {
        for (int i = 0; i < 1000; i++) {
            String p = "5" + String.format("%09d", RANDOM.nextInt(1_000_000_000));
            if (!taken.contains(p)) return p;
        }
        throw new IllegalStateException("Could not generate a free phone number.");
    }

    /**
     * Any phone number, as typed without spaces or dashes: an Indian one (+91 / 0 / 10 digits) as its 10 digits, like
     * the rest of Rahbar; another country's with its "+" kept (+966500621750). Null when there are no digits at all.
     */
    static String phone(String typed) {
        String d = digits(typed);
        if (d.isEmpty()) return null;
        boolean plus = typed.trim().startsWith("+") || typed.trim().startsWith("00");
        if (typed.trim().startsWith("00")) d = d.substring(2);
        if (d.length() == 12 && d.startsWith("91")) return d.substring(2);
        if (!plus && d.length() == 11 && d.startsWith("0")) return d.substring(1);
        if (d.length() > 20) throw bad("Phone number '" + typed + "' is too long.");
        return plus ? "+" + d : d;
    }

    private static String digits(String s) {
        return s == null ? "" : s.replaceAll("[^0-9]", "");
    }

    /** "Chapter Name", "chapter_name", "CHAPTER NAME" -> "chaptername". */
    static String key(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-]", "");
    }

    private static String column(Map<String, String> columns, String... names) {
        for (String n : names) if (columns.containsKey(n)) return columns.get(n);
        return null;
    }

    private static String value(CSVRecord row, String column) {
        if (column == null || !row.isSet(column)) return null;
        String v = row.get(column).trim();
        return v.isEmpty() ? null : v;
    }

    private static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
