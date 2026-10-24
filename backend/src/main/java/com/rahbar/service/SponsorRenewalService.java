package com.rahbar.service;

import com.rahbar.entity.GrantorGrantee;
import com.rahbar.entity.RenewalCampaign;
import com.rahbar.entity.RenewalResponse;
import com.rahbar.entity.User;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.GrantorGranteeRepository;
import com.rahbar.repository.RenewalCampaignRepository;
import com.rahbar.repository.RenewalResponseRepository;
import com.rahbar.repository.UserRepository;
import com.rahbar.security.Access;
import com.rahbar.security.StudentScope;
import com.rahbar.util.Rows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sponsors & Payments > Sponsor Renewal: before a session the office asks every active sponsor who has students now
 * whether they will continue (same students / a different number / stop). Sponsors are emailed and answer in their
 * portal; the office sees who answered, reminds the rest, and can record an answer given on the phone.
 */
@Service
public class SponsorRenewalService {

    private static final Logger log = LoggerFactory.getLogger(SponsorRenewalService.class);
    public static final String CONTINUE = "CONTINUE", CHANGE = "CHANGE", STOP = "STOP";
    static final Set<String> ANSWERS = Set.of(CONTINUE, CHANGE, STOP);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final RenewalCampaignRepository campaigns;
    private final RenewalResponseRepository responses;
    private final UserRepository users;
    private final GrantorGranteeRepository mappings;
    private final StudentScope studentScope;
    private final EmailService emailService;
    private final String siteUrl;
    /** Emails go out one after another on this thread (many sponsors can take minutes). */
    private final ExecutorService sender = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "renewal-sender");
        t.setDaemon(true);
        return t;
    });

    public SponsorRenewalService(RenewalCampaignRepository campaigns, RenewalResponseRepository responses, UserRepository users,
                                 GrantorGranteeRepository mappings, StudentScope studentScope, EmailService emailService,
                                 @Value("${app.cors.allowed-origins:}") String siteUrl) {
        this.campaigns = campaigns;
        this.responses = responses;
        this.users = users;
        this.mappings = mappings;
        this.studentScope = studentScope;
        this.emailService = emailService;
        this.siteUrl = siteUrl;
    }

    // ---------------------------------------------------------------- office

    /** Every campaign, newest first, with how many answered each way. */
    public Map<String, Object> campaigns() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RenewalCampaign c : campaigns.findAllByOrderByIdDesc()) {
            Map<String, Object> r = Rows.of(c);
            r.putAll(summary(visible(responses.findByCampaignId(c.getId()))));
            rows.add(r);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", rows);
        out.put("open", campaigns.findFirstByClosedAtIsNullOrderByIdDesc().isPresent());
        out.put("next_year", LocalDate.now().getYear() + 1);
        return out;
    }

    /** One campaign: every sponsor asked (those the user may see) and their answer, unanswered first. */
    public Map<String, Object> detail(Long id) {
        RenewalCampaign c = require(id);
        List<RenewalResponse> list = visible(responses.findByCampaignId(id));
        Set<Long> ids = new HashSet<>();
        list.forEach(r -> { ids.add(r.getSponsorId()); if (r.getAnsweredBy() != null) ids.add(r.getAnsweredBy()); });
        Map<Long, User> people = new HashMap<>();
        users.findAllById(ids).forEach(u -> people.put(u.getId(), u));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RenewalResponse r : list) {
            User s = people.get(r.getSponsorId());
            Map<String, Object> row = Rows.of(r);
            row.put("sponsor_name", s == null ? "?" : s.getName());
            row.put("sponsor_code", s == null ? null : s.getUserId());
            row.put("chapter_name", s == null ? null : s.getChapterName());
            row.put("has_email", s != null && usableEmail(s.getEmail()));
            row.put("answered_by_name", r.getAnsweredBy() == null ? null
                    : r.getAnsweredBy().equals(r.getSponsorId()) ? "Sponsor" : Optional.ofNullable(people.get(r.getAnsweredBy())).map(User::getName).orElse("Office"));
            rows.add(row);
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> r.get("answer") != null).thenComparing(r -> String.valueOf(r.get("sponsor_name")), String.CASE_INSENSITIVE_ORDER));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("campaign", Rows.of(c));
        out.put("summary", summary(list));
        out.put("rows", rows);
        return out;
    }

    /**
     * Starts a campaign for a session year (one open at a time): every active sponsor with students now is asked, and
     * emailed in the background.
     */
    @Transactional
    public Map<String, Object> start(Map<String, Object> b) {
        requireOffice();
        if (campaigns.findFirstByClosedAtIsNullOrderByIdDesc().isPresent()) throw bad("A renewal campaign is already open. Close it first.");
        Integer year = ServiceSupport.toInteger(b.get("sessionYear"));
        if (year == null || year < 2020 || year > 2100) throw bad("Choose the session year, e.g. " + (LocalDate.now().getYear() + 1) + ".");
        LocalDate deadline = CoachingService.date(b.get("deadline"), "Answer by");
        if (deadline != null && deadline.isBefore(LocalDate.now())) throw bad("The answer-by date is in the past.");
        RenewalCampaign c = new RenewalCampaign();
        c.setSessionYear(year);
        c.setDeadline(deadline);
        c.setMessage(CoachingService.text(b.get("message"), "Message", 2000, false));
        campaigns.save(c);

        Map<Long, Integer> studentsOf = studentsPerSponsor();
        Long unassigned = ServiceSupport.unassignedGrantorId(users);
        List<RenewalResponse> asked = new ArrayList<>();
        for (User s : users.findByRoleId(5)) {
            if ("Inactive".equalsIgnoreCase(s.getStatus()) || s.getMergedIntoId() != null || s.getId().equals(unassigned)) continue;
            int n = studentsOf.getOrDefault(s.getId(), 0);
            if (n == 0) continue;
            RenewalResponse r = new RenewalResponse();
            r.setCampaignId(c.getId());
            r.setSponsorId(s.getId());
            r.setCurrentStudents(n);
            asked.add(responses.save(r));
        }
        if (asked.isEmpty()) throw bad("No active sponsor has students right now, so there is nobody to ask.");
        List<Long> ids = asked.stream().map(RenewalResponse::getId).toList();
        afterCommit(() -> email(c.getId(), ids, false));
        return Map.of("id", c.getId(), "message", asked.size() + " sponsor(s) asked; the emails are going out now.");
    }

    /** Emails everyone in the campaign who hasn't answered yet (in the background). */
    @Transactional
    public Map<String, Object> remind(Long id) {
        requireOffice();
        RenewalCampaign c = requireOpen(id);
        List<Long> waiting = visible(responses.findByCampaignId(id)).stream().filter(r -> r.getAnswer() == null).map(RenewalResponse::getId).toList();
        if (waiting.isEmpty()) throw bad("Everyone has answered.");
        afterCommit(() -> email(c.getId(), waiting, true));
        return Map.of("message", "Reminding " + waiting.size() + " sponsor(s).");
    }

    /** The office records an answer given on the phone: { sponsorId, answer, studentsWanted, note }. */
    @Transactional
    public Map<String, Object> record(Long id, Map<String, Object> b) {
        requireOpen(id); // chapter leads may record their chapter's sponsors' answers
        Long sponsorId = ServiceSupport.toLong(b.get("sponsorId"));
        RenewalResponse r = responses.findByCampaignIdAndSponsorId(id, sponsorId == null ? -1L : sponsorId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "That sponsor isn't in this campaign."));
        if (visible(List.of(r)).isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "That sponsor isn't in this campaign.");
        answer(r, b);
        return Map.of("message", "Answer recorded.");
    }

    @Transactional
    public Map<String, Object> close(Long id) {
        requireOffice();
        RenewalCampaign c = requireOpen(id);
        c.setClosedAt(LocalDateTime.now());
        campaigns.save(c);
        return Map.of("message", "Campaign closed.");
    }

    // ---------------------------------------------------------------- sponsor portal

    /** The open campaign the signed-in sponsor is asked in, with their answer so far (null when there is none). */
    public Map<String, Object> mine() {
        Long me = Access.current().getUser().getId();
        Map<String, Object> out = new LinkedHashMap<>();
        RenewalCampaign c = campaigns.findFirstByClosedAtIsNullOrderByIdDesc().orElse(null);
        RenewalResponse r = c == null ? null : responses.findByCampaignIdAndSponsorId(c.getId(), me).orElse(null);
        out.put("campaign", r == null ? null : Rows.of(c));
        out.put("response", r == null ? null : Rows.of(r));
        return out;
    }

    /** The sponsor answers (or changes their answer) while the campaign is open. */
    @Transactional
    public Map<String, Object> answerMine(Map<String, Object> b) {
        Long me = Access.current().getUser().getId();
        RenewalCampaign c = campaigns.findFirstByClosedAtIsNullOrderByIdDesc().orElseThrow(() -> bad("There is no renewal question open right now."));
        RenewalResponse r = responses.findByCampaignIdAndSponsorId(c.getId(), me).orElseThrow(() -> bad("You aren't asked in this renewal."));
        answer(r, b);
        return Map.of("message", CONTINUE.equals(r.getAnswer()) || CHANGE.equals(r.getAnswer())
                ? "Thank you for continuing with Rahbar!" : "Thank you for letting us know.");
    }

    // ---------------------------------------------------------------- helpers

    private void answer(RenewalResponse r, Map<String, Object> b) {
        String a = b.get("answer") == null ? "" : String.valueOf(b.get("answer")).trim().toUpperCase(Locale.ROOT);
        if (!ANSWERS.contains(a)) throw bad("Choose an answer.");
        Integer wanted = null;
        if (CHANGE.equals(a)) {
            wanted = ServiceSupport.isBlank(b.get("studentsWanted")) ? null : ServiceSupport.toInteger(b.get("studentsWanted"));
            if (wanted == null || wanted < 1 || wanted > 100) throw bad("How many students: a number from 1 to 100.");
        }
        r.setAnswer(a);
        r.setStudentsWanted(wanted);
        r.setNote(CoachingService.text(b.get("note"), "Note", 500, false));
        r.setAnsweredAt(LocalDateTime.now());
        r.setAnsweredBy(Access.current().getUser().getId());
        responses.save(r);
    }

    /** Counts: answered each way, waiting, and how many students are covered next session. */
    static Map<String, Object> summary(List<RenewalResponse> list) {
        long cont = 0, change = 0, stop = 0, waiting = 0, studentsNow = 0, studentsNext = 0;
        for (RenewalResponse r : list) {
            studentsNow += r.getCurrentStudents();
            if (r.getAnswer() == null) { waiting++; continue; }
            switch (r.getAnswer()) {
                case CONTINUE -> { cont++; studentsNext += r.getCurrentStudents(); }
                case CHANGE -> { change++; studentsNext += r.getStudentsWanted() == null ? 0 : r.getStudentsWanted(); }
                default -> stop++;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("asked", list.size());
        out.put("continuing", cont);
        out.put("changing", change);
        out.put("stopping", stop);
        out.put("waiting", waiting);
        out.put("students_now", studentsNow);
        out.put("students_next", studentsNext);
        return out;
    }

    private void email(Long campaignId, List<Long> responseIds, boolean reminder) {
        RenewalCampaign c = campaigns.findById(campaignId).orElse(null);
        if (c == null) return;
        int sent = 0;
        for (Long rid : responseIds) {
            try {
                RenewalResponse r = responses.findById(rid).orElse(null);
                if (r == null || (reminder && r.getAnswer() != null)) continue;
                User s = users.findById(r.getSponsorId()).orElse(null);
                if (s == null || !usableEmail(s.getEmail())) continue;
                boolean ok = emailService.send(EmailType.SPONSOR_RENEWAL, s.getEmail(), ServiceSupport.vars(
                        "name", s.getName(), "students", r.getCurrentStudents() == 1 ? "1 student" : r.getCurrentStudents() + " students",
                        "session_year", c.getSessionYear(), "deadline", c.getDeadline() == null ? "soon" : c.getDeadline().format(DAY),
                        "message", c.getMessage() == null ? "" : c.getMessage(), "answer_url", answerUrl()));
                if (ok) {
                    if (reminder) r.setRemindedAt(LocalDateTime.now()); else r.setEmailedAt(LocalDateTime.now());
                    responses.save(r);
                    sent++;
                }
            } catch (RuntimeException e) {
                log.warn("Renewal email failed for response {}: {}", rid, e.getMessage());
            }
        }
        log.info("Renewal campaign {}: {} {} email(s) sent", campaignId, sent, reminder ? "reminder" : "request");
    }

    /** Sends in the background once the saved rows are committed (the sender thread reads them). */
    private void afterCommit(Runnable job) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            sender.submit(job);
                        }
                    });
        } else {
            sender.submit(job);
        }
    }

    private String answerUrl() {
        String first = siteUrl == null ? "" : siteUrl.split(",")[0].trim();
        return first.isEmpty() ? "your Rahbar sponsor page (Renewal)" : first + "/sponsor/renewal";
    }

    private Map<Long, Integer> studentsPerSponsor() {
        Map<Long, Integer> out = new HashMap<>();
        for (GrantorGrantee g : mappings.findAll()) if (g.getGrantorId() != null) out.merge(g.getGrantorId(), 1, Integer::sum);
        return out;
    }

    /** The responses of sponsors the user may see (chapter leads: their chapter's). */
    private List<RenewalResponse> visible(List<RenewalResponse> list) {
        StudentScope.Scope scope = studentScope.current();
        if (scope.all()) return list;
        Map<Long, User> people = new HashMap<>();
        users.findAllById(list.stream().map(RenewalResponse::getSponsorId).toList()).forEach(u -> people.put(u.getId(), u));
        return list.stream().filter(r -> scope.includesSponsor(people.get(r.getSponsorId()))).toList();
    }

    /** Starting, reminding and closing a campaign cover every sponsor, so only the office (not a chapter lead) does it. */
    private void requireOffice() {
        if (!studentScope.current().all()) throw Access.forbidden("Only the office runs renewal campaigns.");
    }

    private RenewalCampaign require(Long id) {
        return campaigns.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Campaign not found."));
    }

    private RenewalCampaign requireOpen(Long id) {
        RenewalCampaign c = require(id);
        if (c.getClosedAt() != null) throw bad("This campaign is closed.");
        return c;
    }

    private static boolean usableEmail(String email) {
        return email != null && email.contains("@") && !email.trim().toLowerCase(Locale.ROOT).endsWith("@rahbar.com");
    }

    private static ApiException bad(String m) {
        return new ApiException(HttpStatus.BAD_REQUEST, m);
    }
}
