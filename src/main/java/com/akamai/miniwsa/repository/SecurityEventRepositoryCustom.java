package com.akamai.miniwsa.repository;

import com.akamai.miniwsa.model.SecurityEvent;
import java.util.List;

public interface SecurityEventRepositoryCustom {

    /**
     * One query: {@code (ip = ? AND ts BETWEEN ? AND ?)} OR-ed per window, ordered by IP then
     * timestamp so callers can binary-search without sorting in Java.
     */
    List<Object[]> findTimestampsInWindows(List<IpTimeWindow> windows);

    List<SecurityEvent> findPage(SampleFilter filter, int offset, int limit);

    long countMatching(SampleFilter filter, int cap);
}
