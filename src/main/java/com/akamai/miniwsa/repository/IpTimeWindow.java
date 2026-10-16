package com.akamai.miniwsa.repository;

import java.time.Instant;

public record IpTimeWindow(String clientIp, Instant from, Instant to) {}
