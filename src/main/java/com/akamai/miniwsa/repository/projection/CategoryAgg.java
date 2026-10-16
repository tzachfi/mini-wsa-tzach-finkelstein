package com.akamai.miniwsa.repository.projection;

import com.akamai.miniwsa.model.RuleCategory;

public record CategoryAgg(RuleCategory category, Long count, Long threatScoreSum) {}
