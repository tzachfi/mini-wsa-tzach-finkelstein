package com.akamai.miniwsa.repository.projection;

import com.akamai.miniwsa.model.Action;
import com.akamai.miniwsa.model.RuleCategory;

public record CategoryActionAgg(RuleCategory category, Action action, Long count, Long threatScoreSum) {}
