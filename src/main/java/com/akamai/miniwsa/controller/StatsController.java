package com.akamai.miniwsa.controller;

import com.akamai.miniwsa.dto.ApiProblemDetail;
import com.akamai.miniwsa.dto.StatsSummaryResponse;
import com.akamai.miniwsa.dto.TimeSeriesInterval;
import com.akamai.miniwsa.dto.TimeSeriesResponse;
import com.akamai.miniwsa.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/stats")
@RequiredArgsConstructor
@Tag(name = "Stats")
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/summary")
    @Operation(
            summary = "Aggregate event stats",
            description =
                    "Aggregates stored events for an optional configId and a required half-open window [from, to) on event timestamp. The window may be at most app.stats.max-range (default 31d).")
    @ApiResponse(responseCode = "200", description = "Summary for the window, including empty zero-filled maps.")
    @ApiResponse(
            responseCode = "400",
            description = "Missing or invalid from/to, from not before to, or range above the cap.",
            content = @Content(schema = @Schema(implementation = ApiProblemDetail.class)))
    @ApiResponse(
            responseCode = "503",
            description = "Stats and samples query pool is full.",
            headers =
                    @Header(
                            name = "Retry-After",
                            description = "Seconds to wait before retrying",
                            schema = @Schema(type = "string", example = "2")),
            content = @Content(schema = @Schema(implementation = ApiProblemDetail.class)))
    public StatsSummaryResponse summary(
            @Parameter(description = "Limit to one configuration. Omit to aggregate every configuration.")
                    @RequestParam(required = false)
                    Integer configId,
            @Parameter(description = "Window start (inclusive). ISO-8601 instant with an offset.", required = true)
                    @RequestParam
                    Instant from,
            @Parameter(description = "Window end (exclusive). ISO-8601 instant with an offset.", required = true)
                    @RequestParam
                    Instant to) {
        return statsService.summarize(configId, from, to);
    }

    @GetMapping("/timeseries")
    @Operation(
            summary = "Bucketed event counts",
            description =
                    "Event counts on UTC calendar intervals for the same half-open [from, to) window. Interval caps: 1m 24h, 5m 7d, 1h 31d.")
    @ApiResponse(responseCode = "200", description = "Every overlapping bucket in ascending order, including count 0.")
    @ApiResponse(
            responseCode = "400",
            description = "Missing or invalid parameters, from not before to, or a range above a cap.",
            content = @Content(schema = @Schema(implementation = ApiProblemDetail.class)))
    @ApiResponse(
            responseCode = "503",
            description = "Stats and samples query pool is full.",
            headers =
                    @Header(
                            name = "Retry-After",
                            description = "Seconds to wait before retrying",
                            schema = @Schema(type = "string", example = "2")),
            content = @Content(schema = @Schema(implementation = ApiProblemDetail.class)))
    public TimeSeriesResponse timeseries(
            @Parameter(description = "Limit to one configuration. Omit to cover every configuration.")
                    @RequestParam(required = false)
                    Integer configId,
            @Parameter(description = "Window start (inclusive). ISO-8601 instant with an offset.", required = true)
                    @RequestParam
                    Instant from,
            @Parameter(description = "Window end (exclusive). ISO-8601 instant with an offset.", required = true)
                    @RequestParam
                    Instant to,
            @Parameter(description = "UTC calendar bucket size", required = true) @RequestParam
                    TimeSeriesInterval interval) {
        return statsService.timeseries(configId, from, to, interval);
    }
}
