package com.rahbar.service;

import com.rahbar.entity.BroadcastAttachment;
import com.rahbar.entity.BroadcastMessage;
import com.rahbar.entity.BroadcastTemplate;
import com.rahbar.entity.Chapter;
import com.rahbar.entity.Role;
import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.BroadcastAttachmentRepository;
import com.rahbar.repository.BroadcastMessageRepository;
import com.rahbar.repository.ChapterRepository;
import com.rahbar.repository.RoleRepository;
import com.rahbar.repository.UserRepository;
import com.rahbar.util.Ids;
import com.rahbar.util.Rows;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Admin > Broadcast Messages: email one or more people, every user of some roles, or every chapter lead.
 * Users also get an in-app notification. Emails go out on a background thread (a large group can take
 * minutes); the stored message shows how many were sent, skipped (no usable email) and failed.
 */
@Service
public class BroadcastService {

    private static final Logger log = LoggerFactory.getLogger(BroadcastService.class);

    /** USERS: chosen people (userIds); ROLES: every active user of the roles (roleIds); CHAPTER_LEADS: lead of every active chapter. */
    public static final String USERS = "USERS", ROLES = "ROLES", CHAPTER_LEADS = "CHAPTER_LEADS";

    /** Attachments: at most this many files, together at most MAX_ATTACHMENT_BYTES (mail servers reject large emails). */
    static final int MAX_ATTACHMENTS = 5;
    static final long MAX_ATTACHMENT_BYTES = 15L * 1024 * 1024;
    static final Set<String> ATTACHMENT_TYPES = Set.of("pdf", "jpg", "jpeg", "png", "webp", "gif", "doc", "docx",
            "xls", "xlsx", "csv", "ppt", "pptx", "txt");

    /** One addressee: a user (userId set) or a chapter lead's own email address. */
    public record Recipient(Long userId, String name, String email) {}

    private final BroadcastMessageRepository broadcastRepository;
    private final BroadcastAttachmentRepository attachmentRepository;
    private final com.rahbar.repository.BroadcastTemplateRepository templateRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final FileStorageService fileStorageService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ChapterRepository chapterRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;
    /** One sender thread: broadcasts go out one after another. */
    private final ExecutorService sender = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "broadcast-sender");
        t.setDaemon(true);
        return t;
    });

    public BroadcastService(BroadcastMessageRepository broadcastRepository, BroadcastAttachmentRepository attachmentRepository,
                            com.rahbar.repository.BroadcastTemplateRepository templateRepository,
                            com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                            FileStorageService fileStorageService, UserRepository userRepository,
                            RoleRepository roleRepository, ChapterRepository chapterRepository,
                            EmailService emailService, NotificationService notificationService) {
        this.broadcastRepository = broadcastRepository;
        this.attachmentRepository = attachmentRepository;
        this.templateRepository = templateRepository;
        this.objectMapper = objectMapper;
        this.fileStorageService = fileStorageService;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.chapterRepository = chapterRepository;
        this.emailService = emailService;
        this.notificationService = notificationService;
    }

    @PreDestroy
    void stop() {
        sender.shutdown();
    }

    /** The latest 100 broadcasts, newest first. */
    public List<Map<String, Object>> history() {
        Map<Long, String> names = new HashMap<>();
        List<BroadcastMessage> list = broadcastRepository.findTop100ByOrderByBroadcastIdDesc();
        Set<Long> ids = new HashSet<>();
        list.forEach(b -> { if (b.getCreatedBy() != null) ids.add(b.getCreatedBy()); });
        userRepository.findAllById(ids).forEach(u -> names.put(u.getId(), u.getName()));
        Map<Long, List<Map<String, Object>>> files = new HashMap<>();
        attachmentRepository.findByBroadcastIdIn(list.stream().map(BroadcastMessage::getBroadcastId).toList())
                .forEach(a -> files.computeIfAbsent(a.getBroadcastId(), k -> new ArrayList<>())
                        .add(Rows.pick(a, "attachment_id", "file_name", "file_path", "size_bytes")));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BroadcastMessage b : list) {
            Map<String, Object> row = Rows.of(b);
            row.put("sent_by_name", names.get(b.getCreatedBy()));
            // The scheduled time as an instant (with zone), so browsers in any time zone show it correctly.
            row.put("scheduled_for", b.getScheduledAt() == null ? null
                    : b.getScheduledAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toString());
            row.put("attachments", files.getOrDefault(b.getBroadcastId(), List.of()));
            rows.add(row);
        }
        return rows;
    }

    /** People to pick as recipients (name, email or user code contains the text). */
    public List<Map<String, Object>> searchUsers(String q) {
        if (q == null || q.trim().length() < 2) return List.of();
        String pattern = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        Map<Integer, String> roleNames = new HashMap<>();
        roleRepository.findAll().forEach(r -> roleNames.put(r.getRoleId(), r.getRoleName()));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (User u : userRepository.searchByNameEmailOrCode(pattern, PageRequest.of(0, 20))) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", u.getId());
            row.put("user_id", u.getUserId());
            row.put("name", u.getName());
            row.put("email", u.getEmail());
            row.put("role_name", roleNames.get(u.getRoleId()));
            row.put("has_email", usable(u.getEmail()));
            rows.add(row);
        }
        return rows;
    }

    /** How many people an audience reaches, and how many of them can actually get an email. */
    public Map<String, Object> preview(Map<String, Object> audience) {
        List<Recipient> recipients = recipients(audience);
        long withEmail = recipients.stream().filter(r -> usable(r.email())).count();
        return Map.of("recipients", recipients.size(), "withEmail", withEmail,
                "withoutEmail", recipients.size() - withEmail, "audience", describe(audience));
    }

    /** Stores the message (and its attachments) and starts emailing it in the background; returns the stored message. */
    public Map<String, Object> send(String subject, String body, Map<String, Object> audience, List<MultipartFile> files) {
        return send(subject, body, audience, files, null);
    }

    /**
     * Stores the message and its attachments; sends it now in the background, or at scheduledAt (a time at least a
     * minute from now). Recipients of a scheduled message are worked out when it goes out.
     */
    public Map<String, Object> send(String subject, String body, Map<String, Object> audience, List<MultipartFile> files,
                                    LocalDateTime scheduledAt) {
        boolean later = scheduledAt != null;
        if (later && scheduledAt.isBefore(LocalDateTime.now().plusMinutes(1))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a time in the future to schedule the message.");
        }
        if (later && scheduledAt.isAfter(LocalDateTime.now().plusYears(1))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A message can be scheduled at most a year ahead.");
        }
        List<MultipartFile> attachments = files == null ? List.of() : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        checkAttachments(attachments);
        String s = subject == null ? "" : subject.trim();
        String b = body == null ? "" : body.trim();
        if (s.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Please enter a subject.");
        if (s.length() > 200) throw new ApiException(HttpStatus.BAD_REQUEST, "The subject can be at most 200 characters.");
        if (b.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Please enter the message.");
        List<Recipient> recipients = recipients(audience);
        if (recipients.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Nobody matches the chosen recipients.");

        BroadcastMessage message = new BroadcastMessage();
        message.setSubject(s);
        message.setBody(b);
        message.setAudience(describe(audience));
        message.setRecipients(recipients.size());
        message.setStatus(later ? BroadcastMessage.SCHEDULED : BroadcastMessage.SENDING);
        message.setScheduledAt(scheduledAt);
        message.setAudienceJson(toJson(audience));
        BroadcastMessage saved = broadcastRepository.save(message); // created_by = the signed-in sender

        // Store the files now: the upload is gone once this request ends, but sending happens later.
        List<EmailService.Attachment> mailFiles = new ArrayList<>();
        for (MultipartFile f : attachments) {
            String original = f.getOriginalFilename() == null ? "attachment" : Paths.get(f.getOriginalFilename()).getFileName().toString();
            String stored = fileStorageService.store(f, "broadcast_" + saved.getBroadcastId() + "_"
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "_" + fileStorageService.sanitizeFilename(original));
            BroadcastAttachment a = new BroadcastAttachment();
            a.setBroadcastId(saved.getBroadcastId());
            a.setFileName(original);
            a.setFilePath(stored);
            a.setSizeBytes(f.getSize());
            attachmentRepository.save(a);
            mailFiles.add(new EmailService.Attachment(original, fileStorageService.resolve(stored)));
        }
        if (!later) sender.submit(() -> deliver(saved.getBroadcastId(), s, b, recipients, mailFiles));
        return Rows.of(saved);
    }

    /** Cancels a message that is scheduled and hasn't gone out yet. */
    public void cancel(Long broadcastId) {
        BroadcastMessage m = broadcastRepository.findById(broadcastId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Message not found."));
        if (!BroadcastMessage.SCHEDULED.equals(m.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only a scheduled message that hasn't gone out can be cancelled.");
        }
        m.setStatus(BroadcastMessage.CANCELLED);
        broadcastRepository.save(m);
    }

    /** Every minute: sends the scheduled messages whose time has come. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void sendScheduled() {
        for (BroadcastMessage m : broadcastRepository.findByStatusAndScheduledAtLessThanEqual(BroadcastMessage.SCHEDULED, LocalDateTime.now())) {
            List<Recipient> recipients;
            try {
                recipients = recipients(fromJson(m.getAudienceJson()));
            } catch (Exception e) {
                recipients = List.of(); // e.g. the chosen people or roles no longer exist
            }
            List<EmailService.Attachment> files = new ArrayList<>();
            for (BroadcastAttachment a : attachmentRepository.findByBroadcastIdOrderByAttachmentIdAsc(m.getBroadcastId())) {
                files.add(new EmailService.Attachment(a.getFileName(), fileStorageService.resolve(a.getFilePath())));
            }
            m.setRecipients(recipients.size());
            m.setStatus(BroadcastMessage.SENDING);
            broadcastRepository.save(m);
            List<Recipient> to = recipients;
            sender.submit(() -> deliver(m.getBroadcastId(), m.getSubject(), m.getBody(), to, files));
            log.info("Scheduled broadcast {} is going out to {} recipient(s)", m.getBroadcastId(), recipients.size());
        }
    }

    // ------------------------------------------------------------------ templates

    public List<Map<String, Object>> templates() {
        return Rows.list(templateRepository.findAllByOrderByNameAsc());
    }

    /** Saves a template; a template with the same name is replaced. */
    public Map<String, Object> saveTemplate(String name, String subject, String body) {
        String n = name == null ? "" : name.trim();
        String s = subject == null ? "" : subject.trim();
        String b = body == null ? "" : body.trim();
        if (n.isEmpty() || s.isEmpty() || b.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A template needs a name, a subject and a message.");
        }
        if (n.length() > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "The template name can be at most 100 characters.");
        if (s.length() > 200) throw new ApiException(HttpStatus.BAD_REQUEST, "The subject can be at most 200 characters.");
        BroadcastTemplate t = templateRepository.findByNameIgnoreCase(n).orElseGet(BroadcastTemplate::new);
        t.setName(n);
        t.setSubject(s);
        t.setBody(b);
        return Rows.of(templateRepository.save(t));
    }

    public void deleteTemplate(Long id) {
        templateRepository.deleteById(id);
    }

    private String toJson(Map<String, Object> audience) {
        try {
            return objectMapper.writeValueAsString(audience);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> fromJson(String json) throws Exception {
        return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
    }

    private static void checkAttachments(List<MultipartFile> files) {
        if (files.size() > MAX_ATTACHMENTS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You can attach at most " + MAX_ATTACHMENTS + " files.");
        }
        long total = 0;
        for (MultipartFile f : files) {
            String name = f.getOriginalFilename() == null ? "" : f.getOriginalFilename();
            String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
            if (!ATTACHMENT_TYPES.contains(ext)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "'" + name + "' can't be attached. Allowed: "
                        + String.join(", ", new TreeSet<>(ATTACHMENT_TYPES)) + ".");
            }
            total += f.getSize();
        }
        if (total > MAX_ATTACHMENT_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Attachments can be at most 15 MB together.");
        }
    }

    /** Runs on the sender thread: email (and notify) each recipient, then store the counts. */
    private void deliver(Long broadcastId, String subject, String body, List<Recipient> recipients,
                         List<EmailService.Attachment> attachments) {
        int sent = 0, skipped = 0, failed = 0;
        for (Recipient r : recipients) {
            try {
                if (r.userId() != null) {
                    notificationService.notify(r.userId(), subject,
                            attachments.isEmpty() ? body : body + "\n\n(" + attachments.size() + " attachment(s) were sent by email.)",
                            NotificationService.ANNOUNCEMENT, null, false);
                }
                if (!usable(r.email())) { skipped++; continue; }
                String text = "Dear " + (r.name() == null ? "member" : r.name()) + ",\n\n" + body
                        + "\n\nRegards,\nRahbar - Bihar Anjuman";
                if (emailService.send(r.email(), "Rahbar: " + subject, text, attachments)) sent++; else failed++;
            } catch (Exception e) {
                failed++;
                log.warn("Broadcast {}: could not deliver to {}: {}", broadcastId, r.email(), e.getMessage());
            }
        }
        final int s = sent, k = skipped, f = failed;
        broadcastRepository.findById(broadcastId).ifPresent(m -> {
            m.setSent(s);
            m.setSkipped(k);
            m.setFailed(f);
            m.setStatus(BroadcastMessage.SENT);
            broadcastRepository.save(m);
        });
        log.info("Broadcast {} done: {} sent, {} skipped (no email), {} failed", broadcastId, s, k, f);
    }

    /** Recipients of an audience: active users only, each email address once. */
    private List<Recipient> recipients(Map<String, Object> audience) {
        String type = audience == null ? "" : String.valueOf(audience.get("type"));
        List<Recipient> list = new ArrayList<>();
        switch (type) {
            case USERS -> {
                List<Long> ids = Ids.toLongs(audience.get("userIds"));
                if (ids.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Please choose at least one person.");
                for (User u : userRepository.findAllById(ids)) list.add(new Recipient(u.getId(), u.getName(), u.getEmail()));
            }
            case ROLES -> {
                List<Integer> roleIds = Ids.toLongs(audience.get("roleIds")).stream().map(Long::intValue).toList();
                if (roleIds.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Please choose at least one role.");
                for (User u : userRepository.findByRoleIdIn(roleIds)) {
                    if (!"Inactive".equalsIgnoreCase(u.getStatus())) list.add(new Recipient(u.getId(), u.getName(), u.getEmail()));
                }
            }
            case CHAPTER_LEADS -> {
                for (Chapter c : chapterRepository.findAllByOrderByChapterNameAsc()) {
                    if (Boolean.FALSE.equals(c.getActive())) continue;
                    list.add(new Recipient(null, c.getLeadName() != null ? c.getLeadName() : c.getChapterName() + " chapter lead",
                            c.getLeadEmail()));
                }
            }
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Please choose who should receive the message.");
        }
        // The same email address only gets the message once (people without email are all kept, to be counted).
        Set<String> seen = new HashSet<>();
        List<Recipient> unique = new ArrayList<>();
        for (Recipient r : list) {
            String key = r.email() == null ? null : r.email().trim().toLowerCase(Locale.ROOT);
            if (key == null || key.isEmpty() || seen.add(key)) unique.add(r);
        }
        return unique;
    }

    /** Audience in words, stored with the message. */
    private String describe(Map<String, Object> audience) {
        String type = audience == null ? "" : String.valueOf(audience.get("type"));
        return switch (type) {
            case USERS -> {
                List<String> names = new ArrayList<>();
                userRepository.findAllById(Ids.toLongs(audience.get("userIds")))
                        .forEach(u -> names.add(u.getName() + " (" + u.getUserId() + ")"));
                yield truncate(String.join(", ", names));
            }
            case ROLES -> {
                List<String> names = new ArrayList<>();
                for (Long id : Ids.toLongs(audience.get("roleIds"))) {
                    roleRepository.findById(id.intValue()).map(Role::getRoleName).ifPresent(names::add);
                }
                yield truncate("All " + String.join(", ", names));
            }
            case CHAPTER_LEADS -> "All chapter leads";
            default -> "";
        };
    }

    /** A real address (bulk uploads give placeholder ...@rahbar.com addresses to people without one). */
    private static boolean usable(String email) {
        return email != null && email.contains("@") && !email.trim().toLowerCase(Locale.ROOT).endsWith("@rahbar.com");
    }

    private static String truncate(String s) {
        return s.length() <= 500 ? s : s.substring(0, 497) + "...";
    }
}
