package com.akamai.miniwsa.repository;

import com.akamai.miniwsa.model.SecurityEvent;
import com.akamai.miniwsa.repository.projection.AttackerAgg;
import com.akamai.miniwsa.repository.projection.CategoryActionAgg;
import com.akamai.miniwsa.repository.projection.PathAgg;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SecurityEventRepository extends JpaRepository<SecurityEvent, Long>, SecurityEventRepositoryCustom {

    @Query(
            """
            SELECT new com.akamai.miniwsa.repository.projection.CategoryActionAgg(
                    e.rule.category, e.action, COUNT(e), COALESCE(SUM(e.threatScore), 0))
            FROM SecurityEvent e
            WHERE e.timestamp >= :from AND e.timestamp < :to
            GROUP BY e.rule.category, e.action
            """)
    List<CategoryActionAgg> aggregateByCategoryAndAction(@Param("from") Instant from, @Param("to") Instant to);

    @Query(
            """
            SELECT new com.akamai.miniwsa.repository.projection.CategoryActionAgg(
                    e.rule.category, e.action, COUNT(e), COALESCE(SUM(e.threatScore), 0))
            FROM SecurityEvent e
            WHERE e.configId = :configId
              AND e.timestamp >= :from AND e.timestamp < :to
            GROUP BY e.rule.category, e.action
            """)
    List<CategoryActionAgg> aggregateByCategoryAndActionForConfig(
            @Param("configId") Integer configId, @Param("from") Instant from, @Param("to") Instant to);

    @Query(
            """
            SELECT new com.akamai.miniwsa.repository.projection.AttackerAgg(
                    e.clientIp, COUNT(e), COALESCE(SUM(e.threatScore), 0))
            FROM SecurityEvent e
            WHERE e.timestamp >= :from AND e.timestamp < :to
            GROUP BY e.clientIp
            ORDER BY COUNT(e) DESC, AVG(e.threatScore) DESC, e.clientIp ASC
            """)
    List<AttackerAgg> topAttackers(@Param("from") Instant from, @Param("to") Instant to, Limit limit);

    @Query(
            """
            SELECT new com.akamai.miniwsa.repository.projection.AttackerAgg(
                    e.clientIp, COUNT(e), COALESCE(SUM(e.threatScore), 0))
            FROM SecurityEvent e
            WHERE e.configId = :configId
              AND e.timestamp >= :from AND e.timestamp < :to
            GROUP BY e.clientIp
            ORDER BY COUNT(e) DESC, AVG(e.threatScore) DESC, e.clientIp ASC
            """)
    List<AttackerAgg> topAttackersForConfig(
            @Param("configId") Integer configId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Limit limit);

    @Query(
            """
            SELECT new com.akamai.miniwsa.repository.projection.PathAgg(e.path, COUNT(e))
            FROM SecurityEvent e
            WHERE e.timestamp >= :from AND e.timestamp < :to
            GROUP BY e.path
            ORDER BY COUNT(e) DESC, e.path ASC
            """)
    List<PathAgg> topTargetedPaths(@Param("from") Instant from, @Param("to") Instant to, Limit limit);

    @Query(
            """
            SELECT new com.akamai.miniwsa.repository.projection.PathAgg(e.path, COUNT(e))
            FROM SecurityEvent e
            WHERE e.configId = :configId
              AND e.timestamp >= :from AND e.timestamp < :to
            GROUP BY e.path
            ORDER BY COUNT(e) DESC, e.path ASC
            """)
    List<PathAgg> topTargetedPathsForConfig(
            @Param("configId") Integer configId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Limit limit);
}
