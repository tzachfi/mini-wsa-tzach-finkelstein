package com.akamai.miniwsa.repository.projection;

import java.time.Instant;

public interface TimeBucketAgg {

    Instant getBucketStart();

    Long getEventCount();
}
