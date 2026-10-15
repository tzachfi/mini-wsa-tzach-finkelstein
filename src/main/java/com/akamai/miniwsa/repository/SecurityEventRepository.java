package com.akamai.miniwsa.repository;

import com.akamai.miniwsa.model.SecurityEvent;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SecurityEventRepository extends JpaRepository<SecurityEvent, Long> {

    /**
     * Returns {@code [clientIp, timestamp]} rows ordered by IP then timestamp ascending so callers
     * can binary-search each IP's window without sorting in Java.
     */
    @Query(
            """
            SELECT e.clientIp, e.timestamp
            FROM SecurityEvent e
            WHERE e.clientIp IN :clientIps
              AND e.timestamp BETWEEN :from AND :to
            ORDER BY e.clientIp ASC, e.timestamp ASC
            """)
    List<Object[]> findTimestampsInRange(
            @Param("clientIps") Collection<String> clientIps,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
