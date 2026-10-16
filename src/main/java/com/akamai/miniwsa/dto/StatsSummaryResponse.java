package com.akamai.miniwsa.dto;

import com.akamai.miniwsa.model.Action;
import com.akamai.miniwsa.model.RuleCategory;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record StatsSummaryResponse(
        Integer configId,
        TimeRange timeRange,
        long totalEvents,
        Double avgThreatScore,
        Map<RuleCategory, CategoryStats> byCategory,
        Map<Action, Long> byAction,
        List<TopAttacker> topAttackers,
        List<TopTargetedPath> topTargetedPaths) {

    public record TimeRange(Instant from, Instant to) {}

    public record CategoryStats(long count, Double avgThreatScore) {}

    public record TopAttacker(String clientIp, long count, Double avgThreatScore) {}

    public record TopTargetedPath(String path, long count) {}
}
