package com.akamai.miniwsa.enrichment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.akamai.miniwsa.model.RuleCategory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AttackTypeClassifierTest {

    private final AttackTypeClassifier classifier = new AttackTypeClassifier();

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        "INJECTION, SQL/Command Injection",
        "XSS, Cross-Site Scripting",
        "PROTOCOL_VIOLATION, Protocol Anomaly",
        "DATA_LEAKAGE, Data Exfiltration",
        "BOT, Bot Activity",
        "DOS, Denial of Service",
        "RATE_LIMIT, Rate Limiting"
    })
    void classifyMapsCategoryToAttackType(RuleCategory category, String expectedAttackType) {
        assertEquals(expectedAttackType, classifier.classify(category));
    }
}
