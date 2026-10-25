package com.rahbar.service;

import com.rahbar.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Coaching centers (RCC) a sponsor runs / pays for: several per sponsor, chosen on Edit Sponsor, shown on the
 * Coaching Centers page.
 */
@Service
public class SponsorCoachingCenterService {

    private final JdbcTemplate jdbc;

    public SponsorCoachingCenterService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** rcc_center_id of the centers this sponsor supports. */
    public List<Long> centersOf(Long sponsorId) {
        return jdbc.queryForList("SELECT rcc_center_id FROM sponsor_coaching_centers WHERE sponsor_user_id = ? ORDER BY rcc_center_id",
                Long.class, sponsorId);
    }

    /** Replaces the sponsor's centers with these (empty: none). */
    @Transactional
    public void set(Long sponsorId, Collection<Long> centerIds) {
        Set<Long> ids = new LinkedHashSet<>(centerIds);
        for (Long id : ids) {
            Long n = jdbc.queryForObject("SELECT COUNT(*) FROM rcc_centers WHERE rcc_center_id = ?", Long.class, id);
            if (n == null || n == 0) throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown coaching center: " + id);
        }
        jdbc.update("DELETE FROM sponsor_coaching_centers WHERE sponsor_user_id = ?", sponsorId);
        for (Long id : ids) jdbc.update("INSERT INTO sponsor_coaching_centers (sponsor_user_id, rcc_center_id) VALUES (?, ?)", sponsorId, id);
    }

    /** rcc_center_id -> its sponsors [{ id, user_id, name, care_of }], by name. */
    public Map<Long, List<Map<String, Object>>> sponsorsByCenter() {
        Map<Long, List<Map<String, Object>>> out = new LinkedHashMap<>();
        for (Map<String, Object> r : jdbc.queryForList("""
                SELECT s.rcc_center_id, u.id, u.user_id, u.name, u.care_of FROM sponsor_coaching_centers s
                JOIN users u ON u.id = s.sponsor_user_id ORDER BY u.name""")) {
            Map<String, Object> sponsor = new LinkedHashMap<>();
            sponsor.put("id", r.get("id"));
            sponsor.put("user_id", r.get("user_id"));
            sponsor.put("name", r.get("name"));
            sponsor.put("care_of", r.get("care_of"));
            out.computeIfAbsent(((Number) r.get("rcc_center_id")).longValue(), k -> new ArrayList<>()).add(sponsor);
        }
        return out;
    }

    /** A deleted center no longer has sponsors. */
    public void forgetCenter(Long centerId) {
        jdbc.update("DELETE FROM sponsor_coaching_centers WHERE rcc_center_id = ?", centerId);
    }
}
