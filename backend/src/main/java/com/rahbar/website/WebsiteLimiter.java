package com.rahbar.website;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory limit for one public website form: a few per hour per visitor, and a cap for the whole site per day.
 * Enough to stop scripts from flooding the review queues.
 */
final class WebsiteLimiter {

    private final Map<String, Deque<Instant>> byVisitor = new ConcurrentHashMap<>();
    private final Deque<Instant> all = new ArrayDeque<>();
    private final int perVisitorPerHour;
    private final int sitePerDay;

    WebsiteLimiter(int perVisitorPerHour, int sitePerDay) {
        this.perVisitorPerHour = perVisitorPerHour;
        this.sitePerDay = sitePerDay;
    }

    synchronized boolean allow(String visitor) {
        Instant now = Instant.now();
        prune(all, now.minus(Duration.ofDays(1)));
        Deque<Instant> mine = byVisitor.computeIfAbsent(visitor == null ? "?" : visitor, k -> new ArrayDeque<>());
        prune(mine, now.minus(Duration.ofHours(1)));
        if (mine.size() >= perVisitorPerHour || all.size() >= sitePerDay) return false;
        mine.addLast(now);
        all.addLast(now);
        if (byVisitor.size() > 10_000) byVisitor.entrySet().removeIf(e -> e.getValue().isEmpty());
        return true;
    }

    private static void prune(Deque<Instant> q, Instant cutoff) {
        while (!q.isEmpty() && q.peekFirst().isBefore(cutoff)) q.pollFirst();
    }
}
