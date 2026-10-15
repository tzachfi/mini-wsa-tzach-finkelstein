package com.akamai.miniwsa.repository;

import com.akamai.miniwsa.model.SecurityEvent;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SecurityEventRepository extends JpaRepository<SecurityEvent, Long> {

    @Query(
            """
            SELECT e.timestamp
            FROM SecurityEvent e
            WHERE e.clientIp = :clientIp
              AND e.timestamp BETWEEN :from AND :to
            """)
    List<Instant> findTimestampsInRange(
            @Param("clientIp") String clientIp, @Param("from") Instant from, @Param("to") Instant to);
}
