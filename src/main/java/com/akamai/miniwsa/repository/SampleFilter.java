package com.akamai.miniwsa.repository;

import com.akamai.miniwsa.model.Action;
import com.akamai.miniwsa.model.RuleCategory;
import java.time.Instant;

public record SampleFilter(
        Integer configId, Instant from, Instant to, RuleCategory category, Action action) {}
