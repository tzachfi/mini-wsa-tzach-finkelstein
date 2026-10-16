package com.akamai.miniwsa.repository.projection;

public record AttackerAgg(String clientIp, Long count, Long threatScoreSum) {}
