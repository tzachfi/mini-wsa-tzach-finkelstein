package com.akamai.miniwsa.controller;

import com.akamai.miniwsa.dto.StatsSummaryResponse;
import com.akamai.miniwsa.dto.TimeSeriesInterval;
import com.akamai.miniwsa.dto.TimeSeriesResponse;
import com.akamai.miniwsa.service.StatsService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/summary")
    public StatsSummaryResponse summary(
            @RequestParam(required = false) Integer configId,
            @RequestParam Instant from,
            @RequestParam Instant to) {
        return statsService.summarize(configId, from, to);
    }

    @GetMapping("/timeseries")
    public TimeSeriesResponse timeseries(
            @RequestParam(required = false) Integer configId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam TimeSeriesInterval interval) {
        return statsService.timeseries(configId, from, to, interval);
    }
}
