package com.akamai.miniwsa.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.akamai.miniwsa.dto.StatsSummaryResponse.CategoryStats;
import com.akamai.miniwsa.model.Action;
import com.akamai.miniwsa.model.RuleCategory;
import com.akamai.miniwsa.repository.projection.ActionAgg;
import com.akamai.miniwsa.repository.projection.AttackerAgg;
import com.akamai.miniwsa.repository.projection.CategoryAgg;
import com.akamai.miniwsa.repository.projection.PathAgg;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatsRollupTest {

    private static final Instant FROM = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-06-02T00:00:00Z");

    @Test
    void emptyInputZeroFillsMapsAndNullsAverages() {
        var summary = StatsRollup.assemble(14227, FROM, TO, List.of(), List.of(), List.of(), List.of());

        assertEquals(14227, summary.configId());
        assertEquals(FROM, summary.timeRange().from());
        assertEquals(TO, summary.timeRange().to());
        assertEquals(0, summary.totalEvents());
        assertNull(summary.avgThreatScore());
        assertEquals(RuleCategory.values().length, summary.byCategory().size());
        for (var category : RuleCategory.values()) {
            var stats = summary.byCategory().get(category);
            assertEquals(0, stats.count());
            assertNull(stats.avgThreatScore());
        }
        assertEquals(Action.values().length, summary.byAction().size());
        for (var action : Action.values()) {
            assertEquals(0L, summary.byAction().get(action));
        }
        assertTrue(summary.topAttackers().isEmpty());
        assertTrue(summary.topTargetedPaths().isEmpty());
        assertEquals(List.of(RuleCategory.values()), List.copyOf(summary.byCategory().keySet()));
        assertEquals(List.of(Action.values()), List.copyOf(summary.byAction().keySet()));
    }

    @Test
    void overlaysGroupRowsAndKeepsActionCategoryTotalsEqual() {
        var summary = StatsRollup.assemble(
                null,
                FROM,
                TO,
                List.of(
                        new CategoryAgg(RuleCategory.INJECTION, 2L, 150L),
                        new CategoryAgg(RuleCategory.BOT, 1L, 40L)),
                List.of(new ActionAgg(Action.DENY, 2L), new ActionAgg(Action.ALERT, 1L)),
                List.of(new AttackerAgg("203.0.113.42", 2L, 150L)),
                List.of(new PathAgg("/api/v1/login", 2L)));

        assertNull(summary.configId());
        assertEquals(3, summary.totalEvents());
        assertEquals(63.3, summary.avgThreatScore());
        assertEquals(new CategoryStats(2, 75.0), summary.byCategory().get(RuleCategory.INJECTION));
        assertEquals(new CategoryStats(1, 40.0), summary.byCategory().get(RuleCategory.BOT));
        assertEquals(new CategoryStats(0, null), summary.byCategory().get(RuleCategory.XSS));
        assertEquals(2L, summary.byAction().get(Action.DENY));
        assertEquals(1L, summary.byAction().get(Action.ALERT));
        assertEquals(0L, summary.byAction().get(Action.MONITOR));

        long categorySum = summary.byCategory().values().stream().mapToLong(CategoryStats::count).sum();
        long actionSum = summary.byAction().values().stream().mapToLong(Long::longValue).sum();
        assertEquals(summary.totalEvents(), categorySum);
        assertEquals(summary.totalEvents(), actionSum);

        assertEquals(1, summary.topAttackers().size());
        assertEquals("203.0.113.42", summary.topAttackers().getFirst().clientIp());
        assertEquals(2, summary.topAttackers().getFirst().count());
        assertEquals(75.0, summary.topAttackers().getFirst().avgThreatScore());
        assertEquals("/api/v1/login", summary.topTargetedPaths().getFirst().path());
    }
}
