package com.akamai.miniwsa.dto;

import java.time.Instant;
import java.util.List;

public record TimeSeriesResponse(
        Integer configId,
        TimeSeriesInterval interval,
        StatsSummaryResponse.TimeRange timeRange,
        List<Point> points) {

    public record Point(Instant timestamp, long count) {}
}
