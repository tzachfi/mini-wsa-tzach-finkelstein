package com.akamai.miniwsa.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleEmbeddable {

    @Column(name = "rule_id")
    private String id;

    @Column(name = "rule_name")
    private String name;

    @Column(name = "rule_message")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_severity")
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_category")
    private RuleCategory category;
}
