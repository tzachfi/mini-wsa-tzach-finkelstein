package com.akamai.miniwsa.repository.projection;

import com.akamai.miniwsa.model.Action;

public record ActionAgg(Action action, Long count) {}
