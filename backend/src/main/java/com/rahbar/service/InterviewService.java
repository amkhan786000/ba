package com.rahbar.service;

import com.rahbar.entity.*;
import com.rahbar.exception.ApiException;
import com.rahbar.repository.*;
import com.rahbar.config.AuditConfig;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Interview scoring. Admins define the criteria; each interviewer scores every active criterion 1-10 and may
 * add a recommendation (approve / waitlist / reject) and a comment. An application's score is the average of its
 * interviewers' averages; the ranking lists interviewed applications best first.
 */
@Service
public class InterviewService {

    public static final int MIN_SCORE = 1;
    public static final int MAX_SCORE = 10;
    private static final Set<String> RECOMMENDATIONS =
            Set.of(InterviewReview.APPROVE, InterviewReview.WAITLIST, InterviewReview.REJECT);

    private final InterviewCriterionRepository criterionRepository;
    private final InterviewReviewRepository reviewRepository;
    private final InterviewScoreRepository scoreRepository;
    private final GranteeDetailsRepository granteeDetailsRepository;
    private final ApplicationStatusRepository applicationStatusRepository;
    private final UserRepository userRepository;

    public InterviewService(InterviewCriterionRepository criterionRepository, InterviewReviewRepository reviewRepository,
                            InterviewScoreRepository scoreRepository, GranteeDetailsRepository granteeDetailsRepository,
                            ApplicationStatusRepository applicationStatusRepository, UserRepository userRepository) {
        this.criterionRepository = criterionRepository;
        this.reviewRepository = reviewRepository;
        this.scoreRepository = scoreRepository;
        this.granteeDetailsRepository = granteeDetailsRepository;
        this.applicationStatusRepository = applicationStatusRepository;
        this.userRepository = userRepository;
    }

    // ------------------------------------------------------------------------------------ criteria

    public List<Map<String, Object>> criteria() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (InterviewCriterion c : criterionRepository.findAllByOrderBySortOrderAscNameAsc()) {
            Map<String, Object> row = criterionRow(c);
            row.put("used", scoreRepository.countByCriterionId(c.getCriterionId()));
            rows.add(row);
        }
        return rows;
    }

    public Map<String, Object> saveCriterion(Long id, String name, String description, Integer sortOrder, Boolean active) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "The criterion needs a name.");
        if (n.length() > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "The name can be at most 100 characters.");
        criterionRepository.findByNameIgnoreCase(n).filter(c -> !c.getCriterionId().equals(id)).ifPresent(c -> {
            throw new ApiException(HttpStatus.CONFLICT, "There is already a criterion called \"" + c.getName() + "\".");
        });
        InterviewCriterion c = id == null ? new InterviewCriterion() : criterionRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Criterion not found."));
        c.setName(n);
        String d = description == null ? null : description.trim();
        c.setDescription(d == null || d.isEmpty() ? null : (d.length() > 500 ? d.substring(0, 500) : d));
        c.setSortOrder(sortOrder == null ? 0 : sortOrder);
        c.setActive(active == null || active);
        return criterionRow(criterionRepository.save(c));
    }

    /** Deletes a criterion nobody has scored yet; a used one can only be switched off (inactive). */
    public void deleteCriterion(Long id) {
        InterviewCriterion c = criterionRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Criterion not found."));
        if (scoreRepository.countByCriterionId(id) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "\"" + c.getName() + "\" has been used in interviews, so it can't be "
                    + "deleted. Make it inactive instead; old scores are kept.");
        }
        criterionRepository.delete(c);
    }

    private static Map<String, Object> criterionRow(InterviewCriterion c) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("criterion_id", c.getCriterionId());
        row.put("name", c.getName());
        row.put("description", c.getDescription());
        row.put("sort_order", c.getSortOrder());
        row.put("active", c.getActive());
        return row;
    }

    // ------------------------------------------------------------------------------------ scoring

    /** The criteria to show, every interviewer's review and the averages; "mine" is the signed-in user's review. */
    @Transactional(readOnly = true)
    public Map<String, Object> forApplication(Long applicationId) {
        requireApplication(applicationId);
        List<InterviewReview> reviews = reviewRepository.findByGranteeDetailIdOrderByReviewIdAsc(applicationId);
        Map<Long, List<InterviewScore>> scores = scoresByReview(reviews);
        Set<Long> usedCriteria = new HashSet<>();
        scores.values().forEach(l -> l.forEach(s -> usedCriteria.add(s.getCriterionId())));
        List<InterviewCriterion> criteria = criterionRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .filter(c -> Boolean.TRUE.equals(c.getActive()) || usedCriteria.contains(c.getCriterionId())).toList();

        Map<Long, String> names = ServiceSupport.userNames(userRepository, reviews.stream().map(InterviewReview::getInterviewerId).toList());
        Long me = AuditConfig.currentUserId();
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> mine = null;
        for (InterviewReview r : reviews) {
            Map<String, Object> row = reviewRow(r, scores.getOrDefault(r.getReviewId(), List.of()), names);
            rows.add(row);
            if (r.getInterviewerId().equals(me)) mine = row;
        }
        Map<String, Object> averages = new LinkedHashMap<>();
        for (InterviewCriterion c : criteria) {
            List<Integer> values = new ArrayList<>();
            scores.values().forEach(l -> l.stream().filter(s -> s.getCriterionId().equals(c.getCriterionId()))
                    .forEach(s -> values.add(s.getScore())));
            averages.put(String.valueOf(c.getCriterionId()), average(values));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("criteria", criteria.stream().map(InterviewService::criterionRow).toList());
        body.put("reviews", rows);
        body.put("averages", averages);
        body.put("overall", overall(reviews, scores));
        body.put("mine", mine);
        body.put("minScore", MIN_SCORE);
        body.put("maxScore", MAX_SCORE);
        return body;
    }

    /** Saves the signed-in interviewer's scores (every active criterion, 1-10), recommendation and comment. */
    @Transactional
    public Map<String, Object> saveMyReview(Long applicationId, Map<String, Object> scores, String recommendation, String comment) {
        requireApplication(applicationId);
        Long me = AuditConfig.currentUserId();
        if (me == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        List<InterviewCriterion> active = criterionRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .filter(c -> Boolean.TRUE.equals(c.getActive())).toList();
        if (active.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "There are no interview criteria. Ask an admin to add them.");

        Map<Long, Integer> values = new LinkedHashMap<>();
        for (InterviewCriterion c : active) {
            Object v = scores == null ? null : scores.get(String.valueOf(c.getCriterionId()));
            Integer score = v == null || String.valueOf(v).isBlank() ? null : toInt(v);
            if (score == null || score < MIN_SCORE || score > MAX_SCORE) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Give \"" + c.getName() + "\" a score from " + MIN_SCORE + " to " + MAX_SCORE + ".");
            }
            values.put(c.getCriterionId(), score);
        }
        String rec = recommendation == null || recommendation.isBlank() ? null : recommendation.trim().toUpperCase(Locale.ROOT);
        if (rec != null && !RECOMMENDATIONS.contains(rec)) throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown recommendation.");
        String text = comment == null ? null : comment.trim();
        if (text != null && text.length() > 2000) throw new ApiException(HttpStatus.BAD_REQUEST, "The comment can be at most 2,000 characters.");

        InterviewReview review = reviewRepository.findByGranteeDetailIdAndInterviewerId(applicationId, me).orElseGet(() -> {
            InterviewReview r = new InterviewReview();
            r.setGranteeDetailId(applicationId);
            r.setInterviewerId(me);
            return r;
        });
        review.setRecommendation(rec);
        review.setComment(text == null || text.isEmpty() ? null : text);
        review = reviewRepository.saveAndFlush(review);

        Map<Long, InterviewScore> existing = new HashMap<>();
        scoreRepository.findByReviewId(review.getReviewId()).forEach(s -> existing.put(s.getCriterionId(), s));
        for (Map.Entry<Long, Integer> e : values.entrySet()) {
            InterviewScore s = existing.computeIfAbsent(e.getKey(), k -> {
                InterviewScore n = new InterviewScore();
                n.setCriterionId(k);
                return n;
            });
            s.setReviewId(review.getReviewId());
            s.setScore(e.getValue());
            scoreRepository.save(s);
        }
        return forApplication(applicationId);
    }

    /** Removes the signed-in interviewer's own review. */
    @Transactional
    public void deleteMyReview(Long applicationId) {
        Long me = AuditConfig.currentUserId();
        reviewRepository.findByGranteeDetailIdAndInterviewerId(applicationId, me).ifPresent(r -> {
            scoreRepository.deleteAll(scoreRepository.findByReviewId(r.getReviewId()));
            reviewRepository.delete(r);
        });
    }

    /** Interviewed applications, best average first, with the average per criterion and the recommendations. */
    @Transactional(readOnly = true)
    public Map<String, Object> ranking() {
        List<InterviewReview> reviews = reviewRepository.findAll();
        Map<Long, List<InterviewScore>> scores = scoresByReview(reviews);
        Map<Long, List<InterviewReview>> byApp = new LinkedHashMap<>();
        reviews.forEach(r -> byApp.computeIfAbsent(r.getGranteeDetailId(), k -> new ArrayList<>()).add(r));
        List<InterviewCriterion> criteria = criterionRepository.findAllByOrderBySortOrderAscNameAsc();

        Map<Long, GranteeDetails> apps = new HashMap<>();
        granteeDetailsRepository.findAllById(byApp.keySet()).forEach(a -> apps.put(a.getGranteeDetailId(), a));
        Map<Long, ApplicationStatus> latest = byApp.isEmpty() ? Map.of()
                : ServiceSupport.latestByApplication(applicationStatusRepository.findLatestFor(new ArrayList<>(byApp.keySet())));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<Long, List<InterviewReview>> e : byApp.entrySet()) {
            GranteeDetails a = apps.get(e.getKey());
            if (a == null) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("application_id", a.getGranteeDetailId());
            row.put("name", a.getName());
            row.put("course_applied", a.getCourseApplied());
            row.put("rcc_name", a.getRccName());
            ApplicationStatus s = latest.get(a.getGranteeDetailId());
            row.put("status", s == null ? null : s.getStatus());
            row.put("interviewers", e.getValue().size());
            row.put("overall", overall(e.getValue(), scores));
            Map<String, Object> perCriterion = new LinkedHashMap<>();
            for (InterviewCriterion c : criteria) {
                List<Integer> values = new ArrayList<>();
                e.getValue().forEach(r -> scores.getOrDefault(r.getReviewId(), List.of()).stream()
                        .filter(x -> x.getCriterionId().equals(c.getCriterionId())).forEach(x -> values.add(x.getScore())));
                if (!values.isEmpty()) perCriterion.put(String.valueOf(c.getCriterionId()), average(values));
            }
            row.put("averages", perCriterion);
            Map<String, Long> recs = new LinkedHashMap<>();
            for (String rec : List.of(InterviewReview.APPROVE, InterviewReview.WAITLIST, InterviewReview.REJECT)) {
                recs.put(rec, e.getValue().stream().filter(r -> rec.equals(r.getRecommendation())).count());
            }
            row.put("recommendations", recs);
            rows.add(row);
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> (BigDecimal) r.get("overall"),
                Comparator.nullsLast(Comparator.reverseOrder())));
        for (int i = 0; i < rows.size(); i++) rows.get(i).put("rank", i + 1);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("criteria", criteria.stream().map(InterviewService::criterionRow).toList());
        body.put("rows", rows);
        return body;
    }

    // ------------------------------------------------------------------------------------ helpers

    private void requireApplication(Long applicationId) {
        if (applicationId == null || !granteeDetailsRepository.existsById(applicationId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Application not found.");
        }
    }

    private Map<Long, List<InterviewScore>> scoresByReview(List<InterviewReview> reviews) {
        Map<Long, List<InterviewScore>> map = new HashMap<>();
        if (reviews.isEmpty()) return map;
        scoreRepository.findByReviewIdIn(reviews.stream().map(InterviewReview::getReviewId).toList())
                .forEach(s -> map.computeIfAbsent(s.getReviewId(), k -> new ArrayList<>()).add(s));
        return map;
    }

    private static Map<String, Object> reviewRow(InterviewReview r, List<InterviewScore> scores, Map<Long, String> names) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("review_id", r.getReviewId());
        row.put("interviewer_id", r.getInterviewerId());
        row.put("interviewer_name", names.get(r.getInterviewerId()));
        row.put("recommendation", r.getRecommendation());
        row.put("comment", r.getComment());
        row.put("updated_at", r.getUpdatedAt());
        Map<String, Integer> s = new LinkedHashMap<>();
        scores.forEach(x -> s.put(String.valueOf(x.getCriterionId()), x.getScore()));
        row.put("scores", s);
        row.put("average", average(scores.stream().map(InterviewScore::getScore).toList()));
        return row;
    }

    /** Average of the interviewers' own averages (each interviewer counts once). */
    private static BigDecimal overall(List<InterviewReview> reviews, Map<Long, List<InterviewScore>> scores) {
        List<BigDecimal> perReview = new ArrayList<>();
        for (InterviewReview r : reviews) {
            BigDecimal a = average(scores.getOrDefault(r.getReviewId(), List.of()).stream().map(InterviewScore::getScore).toList());
            if (a != null) perReview.add(a);
        }
        if (perReview.isEmpty()) return null;
        return perReview.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(perReview.size()), 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal average(List<Integer> values) {
        if (values.isEmpty()) return null;
        return BigDecimal.valueOf(values.stream().mapToInt(Integer::intValue).sum())
                .divide(BigDecimal.valueOf(values.size()), 1, RoundingMode.HALF_UP);
    }

    private static Integer toInt(Object v) {
        try {
            return Integer.valueOf(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
