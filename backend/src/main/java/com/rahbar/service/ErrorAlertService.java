package com.rahbar.service;

import com.rahbar.entity.Role;
import com.rahbar.entity.User;
import com.rahbar.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Unexpected errors (a request that failed with a server error, or a background job that crashed) are logged with
 * a short reference and, at most every 15 minutes, emailed to the people who look after Rahbar: ALERT_EMAILS
 * (comma-separated), else every active Super Admin. Errors in between are grouped into one email, so a bad hour
 * doesn't flood anyone's inbox. Nothing is sent when no error happened.
 */
@Service
public class ErrorAlertService {

    private static final Logger log = LoggerFactory.getLogger(ErrorAlertService.class);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss").withZone(ZoneId.of("Asia/Kolkata"));
    private static final char[] REF_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    /** At most this many different errors are listed in one email (the rest are counted). */
    static final int MAX_LISTED = 20;
    static final Duration MIN_GAP = Duration.ofMinutes(15);

    /** One kind of error (same exception and place) since the last email. */
    static final class Seen {
        final String signature;
        final String firstRef;
        final String where;
        final String detail;
        final Instant first;
        Instant last;
        int count;

        Seen(String signature, String ref, String where, String detail, Instant at) {
            this.signature = signature;
            this.firstRef = ref;
            this.where = where;
            this.detail = detail;
            this.first = at;
            this.last = at;
        }
    }

    private final EmailService emailService;
    private final UserRepository users;
    private final String configuredRecipients;
    private final Map<String, Seen> pending = new LinkedHashMap<>();
    private Instant lastSent = Instant.EPOCH;

    public ErrorAlertService(EmailService emailService, UserRepository users,
                             @Value("${app.alerts.emails:}") String configuredRecipients) {
        this.emailService = emailService;
        this.users = users;
        this.configuredRecipients = configuredRecipients;
    }

    /** A short reference shown to the user and written in the log and the alert, e.g. "K7Q2ZP". */
    static String newRef() {
        char[] c = new char[6];
        for (int i = 0; i < c.length; i++) c[i] = REF_CHARS[RANDOM.nextInt(REF_CHARS.length)];
        return new String(c);
    }

    /**
     * Records an unexpected error: logs it with its stack trace and queues it for the next alert email.
     * where: e.g. "POST /api/admin/students" or "Scheduled job". Returns the reference.
     */
    public String record(Throwable error, String where) {
        String ref = newRef();
        log.error("Unexpected error {} at {}", ref, where, error);
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        StackTraceElement top = Arrays.stream(error.getStackTrace()).filter(e -> e.getClassName().startsWith("com.rahbar")).findFirst()
                .orElse(error.getStackTrace().length > 0 ? error.getStackTrace()[0] : null);
        String place = top == null ? "" : top.getClassName().replace("com.rahbar.", "") + "." + top.getMethodName() + ":" + top.getLineNumber();
        String signature = root.getClass().getName() + "@" + place + "@" + where;
        String detail = root.getClass().getSimpleName() + (root.getMessage() == null ? "" : ": " + abbreviate(root.getMessage(), 300))
                + (place.isEmpty() ? "" : "\n    at " + place);
        Instant now = Instant.now();
        synchronized (pending) {
            Seen s = pending.computeIfAbsent(signature, k -> new Seen(k, ref, where, detail, now));
            s.count++;
            s.last = now;
        }
        return ref;
    }

    private static String abbreviate(String s, int max) {
        String oneLine = s.replaceAll("\\s+", " ").strip();
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max) + "…";
    }

    /** Every minute: emails what was recorded, if anything, and if the last email was at least 15 minutes ago. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void sendPending() {
        sendPending(Instant.now());
    }

    /** Returns true when an email went out. */
    boolean sendPending(Instant now) {
        List<Seen> batch;
        synchronized (pending) {
            if (pending.isEmpty() || Duration.between(lastSent, now).compareTo(MIN_GAP) < 0) return false;
            batch = new ArrayList<>(pending.values());
            pending.clear();
            lastSent = now;
        }
        List<String> to = recipients();
        if (to.isEmpty()) {
            log.warn("{} unexpected error(s) not emailed: set ALERT_EMAILS or give a Super Admin an email address.", batch.size());
            return false;
        }
        String subject = "Rahbar: " + total(batch) + " unexpected error" + (total(batch) == 1 ? "" : "s");
        String body = body(batch);
        boolean any = false;
        for (String email : to) {
            try {
                any |= emailService.send(email, subject, body);
            } catch (RuntimeException e) {
                log.warn("Could not send the error alert to {}: {}", email, e.getMessage()); // never alert about the alert
            }
        }
        return any;
    }

    static int total(List<Seen> batch) {
        return batch.stream().mapToInt(s -> s.count).sum();
    }

    static String body(List<Seen> batch) {
        StringBuilder b = new StringBuilder();
        b.append("Rahbar had unexpected errors. Users saw \"Something went wrong\" with the reference shown below; ")
         .append("search the backend log for the reference to see the full details.\n\n");
        int listed = 0;
        for (Seen s : batch) {
            if (listed++ == MAX_LISTED) {
                b.append("… and ").append(batch.size() - MAX_LISTED).append(" other kinds of error.\n");
                break;
            }
            b.append("• ").append(s.where).append(" - ").append(s.count).append(s.count == 1 ? " time" : " times")
             .append(" (first ").append(TIME.format(s.first)).append(", reference ").append(s.firstRef)
             .append(s.count > 1 ? "; last " + TIME.format(s.last) : "").append(")\n  ")
             .append(s.detail.replace("\n", "\n  ")).append("\n\n");
        }
        b.append("You get at most one of these emails every 15 minutes. Recipients: ALERT_EMAILS on the server, ")
         .append("or every active Super Admin when it isn't set.");
        return b.toString();
    }

    /** ALERT_EMAILS, else the email addresses of the active Super Admins. */
    List<String> recipients() {
        if (configuredRecipients != null && !configuredRecipients.isBlank()) {
            return Arrays.stream(configuredRecipients.split("[,;|]")).map(String::strip).filter(e -> e.contains("@")).distinct().toList();
        }
        return users.findByRoleId(Role.SUPER_ADMIN).stream()
                .filter(u -> !"Inactive".equalsIgnoreCase(u.getStatus()))
                .map(User::getEmail)
                .filter(e -> e != null && e.contains("@") && !e.toLowerCase(Locale.ROOT).endsWith("@rahbar.com"))
                .distinct().toList();
    }
}
