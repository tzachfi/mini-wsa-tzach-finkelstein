package com.akamai.miniwsa.repository;

import java.util.List;

public interface SecurityEventRepositoryCustom {

    /**
     * One query: {@code (ip = ? AND ts BETWEEN ? AND ?)} OR-ed per window, ordered by IP then
     * timestamp so callers can binary-search without sorting in Java.
     */
    List<Object[]> findTimestampsInWindows(List<IpTimeWindow> windows);
}
