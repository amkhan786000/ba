package com.rahbar.service;

import com.rahbar.entity.ActivityLog;
import com.rahbar.entity.User;
import com.rahbar.repository.ActivityLogRepository;
import com.rahbar.repository.UserRepository;
import com.rahbar.util.Rows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** Activity log: records who did what (API changes and sign-ins) and lets admins search it. */
@Service
public class ActivityLogService {

    private static final Logger log = LoggerFactory.getLogger(ActivityLogService.class);

    private final ActivityLogRepository activityLogRepository;
    private final UserRepository userRepository;

    public ActivityLogService(ActivityLogRepository activityLogRepository, UserRepository userRepository) {
        this.activityLogRepository = activityLogRepository;
        this.userRepository = userRepository;
    }

    /** Saves one entry; never throws (logging must not break the request). */
    public void record(User user, String action, String method, String path, Integer statusCode, String ip) {
        try {
            ActivityLog entry = new ActivityLog();
            if (user != null) {
                entry.setUserId(user.getId());
                entry.setUserName(user.getName());
                entry.setRoleId(user.getRoleId());
            }
            entry.setAction(truncate(action, 255));
            entry.setMethod(method);
            entry.setPath(truncate(path, 255));
            entry.setStatusCode(statusCode);
            entry.setIpAddress(truncate(ip, 64));
            entry.setCreatedAt(LocalDateTime.now());
            activityLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("Could not write activity log entry '{}': {}", action, e.getMessage());
        }
    }

    /**
     * One page of the log, newest first (DataTables-like shape: total, data). Dates are inclusive days.
     * userCode filters by the user's code (users.user_id); each row carries user_code next to user_id (users.id).
     */
    public Map<String, Object> search(String userCode, String search, LocalDate from, LocalDate to, int page, int size) {
        int pageSize = Math.min(Math.max(size, 1), 200);
        Long userId = null;
        if (blankToNull(userCode) != null) {
            userId = userRepository.findByUserId(userCode.trim()).map(User::getId).orElse(-1L); // unknown code: no rows
        }
        Page<ActivityLog> result = activityLogRepository.search(
                userId,
                blankToNull(search) == null ? null : "%" + search.trim().toLowerCase(Locale.ROOT) + "%",
                from == null ? null : from.atStartOfDay(),
                to == null ? null : to.plusDays(1).atStartOfDay(),
                PageRequest.of(Math.max(page, 0), pageSize));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", result.getTotalElements());
        body.put("page", result.getNumber());
        body.put("size", pageSize);
        Set<Long> ids = new HashSet<>();
        result.getContent().forEach(a -> { if (a.getUserId() != null) ids.add(a.getUserId()); });
        Map<Long, String> codes = new HashMap<>();
        userRepository.findAllById(ids).forEach(u -> codes.put(u.getId(), u.getUserId()));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ActivityLog a : result.getContent()) {
            Map<String, Object> row = Rows.of(a);
            row.put("user_code", codes.get(a.getUserId()));
            rows.add(row);
        }
        body.put("data", rows);
        return body;
    }

    /** Entries between two days (inclusive), newest first, for the activity report. */
    public List<ActivityLog> between(LocalDate from, LocalDate to) {
        LocalDateTime start = (from == null ? LocalDate.now().minusDays(30) : from).atStartOfDay();
        LocalDateTime end = (to == null ? LocalDate.now() : to).plusDays(1).atStartOfDay();
        return activityLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end);
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static String truncate(String v, int max) {
        return v == null || v.length() <= max ? v : v.substring(0, max);
    }
}
