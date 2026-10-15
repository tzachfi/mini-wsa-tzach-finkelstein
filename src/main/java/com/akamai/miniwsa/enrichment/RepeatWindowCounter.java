package com.akamai.miniwsa.enrichment;

import com.akamai.miniwsa.model.SecurityEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Counts prior events in the inclusive window {@code [timestamp - 10m, timestamp]} per client IP.
 *
 * <p>Priors are DB history timestamps in the window plus earlier siblings in the same events list
 * that also fall in the window. Counting uses a stable timestamp sort; the returned {@code long[]}
 * stays index-aligned with the original list. History lists (and the in-batch sibling lists built
 * here) must be sorted ascending so {@link #countInWindow} can binary-search. The repeat-offender
 * bonus threshold lives on {@link ThreatScoreCalculator}, not here.
 */
@Component
public class RepeatWindowCounter {

    public static final Duration REPEAT_WINDOW = Duration.ofMinutes(10);

    public long[] priorCounts(List<SecurityEvent> events, Map<String, List<Instant>> history) {
        var priors = new long[events.size()];
        if (events.isEmpty()) {
            return priors;
        }

        record IndexedEvent(int index, SecurityEvent event) {}

        var indexed = new ArrayList<IndexedEvent>(events.size());
        for (var i = 0; i < events.size(); i++) {
            indexed.add(new IndexedEvent(i, events.get(i)));
        }
        indexed.sort(Comparator.comparing(item -> item.event().getTimestamp()));

        Map<String, List<Instant>> siblingsByIp = new HashMap<>();
        for (var item : indexed) {
            var event = item.event();
            var ip = event.getClientIp();
            var ts = event.getTimestamp();
            var windowStart = ts.minus(REPEAT_WINDOW);

            var dbPrior = countInWindow(history.getOrDefault(ip, List.of()), windowStart, ts);
            var siblingPrior = countInWindow(siblingsByIp.getOrDefault(ip, List.of()), windowStart, ts);
            priors[item.index()] = dbPrior + siblingPrior;
            siblingsByIp.computeIfAbsent(ip, ignored -> new ArrayList<>()).add(ts);
        }
        return priors;
    }

    /** Inclusive count of {@code [from, to]} over a list sorted ascending by timestamp. */
    private static long countInWindow(List<Instant> sorted, Instant from, Instant to) {
        return firstIndexAfter(sorted, to) - firstIndexAtOrAfter(sorted, from);
    }

    private static int firstIndexAtOrAfter(List<Instant> sorted, Instant t) {
        int lo = 0;
        int hi = sorted.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (sorted.get(mid).isBefore(t)) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private static int firstIndexAfter(List<Instant> sorted, Instant t) {
        int lo = 0;
        int hi = sorted.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (!sorted.get(mid).isAfter(t)) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }
}
