package com.akamai.miniwsa.enrichment;

import static org.assertj.core.api.Assertions.assertThat;

import com.akamai.miniwsa.model.SecurityEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RepeatWindowCounterTest {

    private static final String IP = "203.0.113.10";
    private static final Instant BASE = Instant.parse("2026-05-20T14:00:00Z");

    private RepeatWindowCounter counter;

    @BeforeEach
    void setUp() {
        counter = new RepeatWindowCounter();
    }

    @Test
    void tenFreshIpEvents_indicesSixThroughTenHavePriorAtLeastFive() {
        var events = IntStream.range(0, 10)
                .mapToObj(i -> event("e" + i, IP, BASE.plusSeconds(i)))
                .toList();

        var priors = counter.priorCounts(events, Map.of());

        assertThat(priors).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        for (int i = 5; i < 10; i++) {
            assertThat(priors[i]).as("1-based index %d", i + 1).isGreaterThanOrEqualTo(5);
        }
    }

    @Test
    void shuffledInput_samePriorsPerEvent_alignedToOriginalOrder() {
        var chronological = IntStream.range(0, 10)
                .mapToObj(i -> event("e" + i, IP, BASE.plusSeconds(i)))
                .toList();
        var shuffled = new ArrayList<>(chronological);
        Collections.shuffle(shuffled, new Random(42));

        var priors = counter.priorCounts(shuffled, Map.of());

        assertThat(priors).hasSize(10);
        for (int i = 0; i < shuffled.size(); i++) {
            var chronologicalIndex = Integer.parseInt(shuffled.get(i).getEventId().substring(1));
            assertThat(priors[i])
                    .as("event %s at shuffled index %d", shuffled.get(i).getEventId(), i)
                    .isEqualTo(chronologicalIndex);
        }
    }

    @Test
    void dbHistoryThreePlusBatchThree_thirdEventHasPriorAtLeastFive() {
        var history = List.of(BASE.minusSeconds(30), BASE.minusSeconds(20), BASE.minusSeconds(10));
        var batch = List.of(
                event("b0", IP, BASE),
                event("b1", IP, BASE.plusSeconds(1)),
                event("b2", IP, BASE.plusSeconds(2)));

        var priors = counter.priorCounts(batch, Map.of(IP, history));

        assertThat(priors).containsExactly(3, 4, 5);
        assertThat(priors[2]).isGreaterThanOrEqualTo(5);
    }

    @Test
    void siblingOneMillisecondBeforeWindow_isExcluded() {
        var ts = BASE.plus(RepeatWindowCounter.REPEAT_WINDOW);
        var justOutside = ts.minus(RepeatWindowCounter.REPEAT_WINDOW).minusMillis(1);
        var events = List.of(event("older", IP, justOutside), event("current", IP, ts));

        var priors = counter.priorCounts(events, Map.of());

        assertThat(priors).containsExactly(0L, 0L);
    }

    @Test
    void siblingExactlyAtWindowStart_isIncluded() {
        var ts = BASE.plus(RepeatWindowCounter.REPEAT_WINDOW);
        var windowStart = ts.minus(RepeatWindowCounter.REPEAT_WINDOW);
        var events = List.of(event("older", IP, windowStart), event("current", IP, ts));

        var priors = counter.priorCounts(events, Map.of());

        assertThat(priors).containsExactly(0L, 1L);
    }

    private static SecurityEvent event(String eventId, String clientIp, Instant timestamp) {
        return SecurityEvent.builder()
                .eventId(eventId)
                .clientIp(clientIp)
                .timestamp(timestamp)
                .build();
    }
}
