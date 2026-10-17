package com.akamai.miniwsa.dto;

import java.util.List;

public record SamplesResponse(
        List<EventResponse> events, long total, boolean totalExact, int limit, int offset) {}
