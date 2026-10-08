package com.rahbar.service;

import com.rahbar.entity.EmailTemplate;
import com.rahbar.entity.Role;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.EmailTemplateRepository;
import com.rahbar.repository.UserRepository;
import com.rahbar.security.Access;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Admin > Email Templates: the subject and text of every kind of email (EmailType). A saved template replaces the
 * built-in default; deleting it (Super Admin only) goes back to the default. {@link #render} fills in the
 * {{placeholders}} when an email is sent.
 */
@Service
public class EmailTemplateService {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-z_]+)\\s*}}");

    /** A ready-to-send email. */
    public record Rendered(String subject, String body) {}

    private final EmailTemplateRepository templateRepository;
    private final UserRepository userRepository;

    public EmailTemplateService(EmailTemplateRepository templateRepository, UserRepository userRepository) {
        this.templateRepository = templateRepository;
        this.userRepository = userRepository;
    }

    /** The email of this kind with the values filled in (the custom template if there is one, else the default). */
    public Rendered render(EmailType type, Map<String, ?> values) {
        EmailTemplate custom = templateRepository.findById(type.name()).orElse(null);
        String subject = custom == null ? type.defaultSubject : custom.getSubject();
        String body = custom == null ? type.defaultBody : custom.getBody();
        return new Rendered(fill(subject, values).replaceAll("\\s+", " ").trim(), fill(body, values));
    }

    /** Replaces {{name}} with values.get("name"); unknown placeholders become empty. */
    static String fill(String text, Map<String, ?> values) {
        Matcher m = PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            Object v = values == null ? null : values.get(m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(v == null ? "" : String.valueOf(v)));
        }
        m.appendTail(out);
        return out.toString();
    }

    // ------------------------------------------------------------------------------------ admin screen

    /** Every kind of email, with its current subject / text and whether it is customised. */
    public List<Map<String, Object>> list() {
        Map<String, EmailTemplate> custom = new HashMap<>();
        templateRepository.findAll().forEach(t -> custom.put(t.getTemplateKey(), t));
        Set<Long> editors = new HashSet<>();
        custom.values().forEach(t -> { if (t.getUpdatedBy() != null) editors.add(t.getUpdatedBy()); });
        Map<Long, String> names = new HashMap<>();
        userRepository.findAllById(editors).forEach(u -> names.put(u.getId(), u.getName()));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (EmailType type : EmailType.values()) rows.add(row(type, custom.get(type.name()), names));
        return rows;
    }

    public Map<String, Object> get(String key) {
        EmailType type = type(key);
        EmailTemplate t = templateRepository.findById(type.name()).orElse(null);
        Map<Long, String> names = new HashMap<>();
        if (t != null && t.getUpdatedBy() != null) userRepository.findById(t.getUpdatedBy()).ifPresent(u -> names.put(u.getId(), u.getName()));
        return row(type, t, names);
    }

    /** Saves the custom subject and text; required placeholders (e.g. the code) must be kept. */
    public Map<String, Object> save(String key, String subject, String body) {
        EmailType type = type(key);
        String s = subject == null ? "" : subject.trim();
        String b = body == null ? "" : body.strip();
        if (s.isEmpty() || b.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Subject and text are required.");
        if (s.length() > 300) throw new ApiException(HttpStatus.BAD_REQUEST, "The subject can be at most 300 characters.");
        if (b.length() > 20000) throw new ApiException(HttpStatus.BAD_REQUEST, "The text can be at most 20,000 characters.");
        for (String p : type.required) {
            if (!Pattern.compile("\\{\\{\\s*" + p + "\\s*}}").matcher(s + "\n" + b).find()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "This email must contain {{" + p + "}}; without it the email would be useless.");
            }
        }
        Set<String> unknown = new TreeSet<>();
        Matcher m = PLACEHOLDER.matcher(s + "\n" + b);
        while (m.find()) if (!type.placeholders.contains(m.group(1))) unknown.add("{{" + m.group(1) + "}}");
        if (!unknown.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown placeholder(s) " + String.join(", ", unknown)
                    + ". You can use: " + String.join(", ", type.placeholders.stream().map(p -> "{{" + p + "}}").toList()) + ".");
        }
        EmailTemplate t = templateRepository.findById(type.name()).orElseGet(() -> {
            EmailTemplate n = new EmailTemplate();
            n.setTemplateKey(type.name());
            return n;
        });
        t.setSubject(s);
        t.setBody(b);
        templateRepository.save(t);
        return get(key);
    }

    /** Removes the custom template so the built-in default is used again. Only the Super Admin may do this. */
    public void delete(String key) {
        EmailType type = type(key);
        if (!Integer.valueOf(Role.SUPER_ADMIN).equals(Access.current().getUser().getRoleId())) {
            throw Access.forbidden("Only the Super Admin can delete an email template.");
        }
        templateRepository.deleteById(type.name());
    }

    /** Subject and text with sample values, for the preview. */
    public Map<String, Object> preview(String key, String subject, String body) {
        EmailType type = type(key);
        Map<String, Object> sample = new HashMap<>();
        for (String p : type.placeholders) sample.put(p, SAMPLES.getOrDefault(p, "[" + p + "]"));
        return Map.of("subject", fill(subject == null ? type.defaultSubject : subject, sample).trim(),
                "body", fill(body == null ? type.defaultBody : body, sample));
    }

    private static final Map<String, String> SAMPLES = Map.ofEntries(
            Map.entry("name", "Ayesha Khan"), Map.entry("code", "123456"), Map.entry("minutes", "15"),
            Map.entry("password", "Temp#4821"), Map.entry("email", "admin@example.org"), Map.entry("site", " (https://rahbar.example.org)"),
            Map.entry("student_name", "Ayesha Khan"), Map.entry("student_code", "STU-1001"), Map.entry("sponsor_name", "Rahman Sponsor"),
            Map.entry("amount", "14000.00"), Map.entry("count", "2"), Map.entry("due_date", "10 Jan 2027"),
            Map.entry("reviewer", "Rahman Sponsor"), Map.entry("session", "Semester 2"), Map.entry("year", "2026"),
            Map.entry("comment", "Please upload the full marks sheet."), Map.entry("title", "Semester 2 results"),
            Map.entry("note", "Upload the marks sheet as a PDF."), Map.entry("application_id", "152"), Map.entry("status", "approved"),
            Map.entry("comments", "Note: Welcome to Rahbar."), Map.entry("interview_at", "12 Jan 2027, 10:30 AM"),
            Map.entry("venue", "Rahbar office, Patna"), Map.entry("subject", "Fee reminder"),
            Map.entry("message", "This is the text of the message."));

    private static EmailType type(String key) {
        try {
            return EmailType.valueOf(key == null ? "" : key.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Unknown email template.");
        }
    }

    private static Map<String, Object> row(EmailType type, EmailTemplate custom, Map<Long, String> names) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", type.name());
        row.put("group", type.group);
        row.put("label", type.label);
        row.put("description", type.description);
        row.put("placeholders", type.placeholders);
        row.put("required", type.required);
        row.put("customised", custom != null);
        row.put("subject", custom == null ? type.defaultSubject : custom.getSubject());
        row.put("body", custom == null ? type.defaultBody : custom.getBody());
        row.put("default_subject", type.defaultSubject);
        row.put("default_body", type.defaultBody);
        row.put("updated_at", custom == null ? null : custom.getUpdatedAt());
        row.put("updated_by_name", custom == null || custom.getUpdatedBy() == null ? null : names.get(custom.getUpdatedBy()));
        return row;
    }
}
