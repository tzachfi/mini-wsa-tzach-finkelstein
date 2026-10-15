package com.akamai.miniwsa.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "security_events",
        uniqueConstraints = @UniqueConstraint(name = "uk_se_event_id", columnNames = "event_id"),
        indexes = {
            // stats + samples + timeseries, when configId is given
            @Index(name = "idx_se_config_ts", columnList = "config_id, event_timestamp, id"),
            // same queries without configId
            @Index(name = "idx_se_ts", columnList = "event_timestamp, id"),
            // repeat-offender check at ingestion
            @Index(name = "idx_se_ip_ts", columnList = "client_ip, event_timestamp"),
            // category filter without configId (alerting bonus)
            @Index(name = "idx_se_cat_ts", columnList = "rule_category, event_timestamp")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "security_event_seq")
    @SequenceGenerator(name = "security_event_seq", sequenceName = "security_event_seq", allocationSize = 50)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "event_timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "config_id", nullable = false)
    private Integer configId;

    @Column(name = "policy_id")
    private String policyId;

    @Column(name = "client_ip", nullable = false, length = 64)
    private String clientIp;

    private String hostname;

    @Column(nullable = false, length = 2048)
    private String path;

    private String method;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "user_agent", length = 2048)
    private String userAgent;

    @Embedded
    private RuleEmbeddable rule;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Action action;

    @Embedded
    private GeoLocationEmbeddable geoLocation;

    @Column(name = "request_size")
    private Integer requestSize;

    @Column(name = "response_size")
    private Integer responseSize;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    // Nullable until enrichment lands (TDD gates); then set nullable = false
    @Column(name = "attack_type")
    private String attackType;

    @Column(name = "threat_score")
    private Integer threatScore;
}
