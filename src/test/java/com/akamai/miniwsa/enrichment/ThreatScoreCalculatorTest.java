package com.akamai.miniwsa.enrichment;

import static org.assertj.core.api.Assertions.assertThat;

import com.akamai.miniwsa.model.Action;
import com.akamai.miniwsa.model.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ThreatScoreCalculatorTest {

    private ThreatScoreCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new ThreatScoreCalculator();
    }

    @ParameterizedTest(name = "{0} {1} path={2} prior={3} → {4}")
    @CsvSource({
        "CRITICAL, DENY,    /api/items,           0, 60",
        "CRITICAL, DENY,    /admin,               0, 75",
        "CRITICAL, ALERT,   /api/items,           0, 50",
        "CRITICAL, ALERT,   /login,               0, 65",
        "CRITICAL, MONITOR, /api/items,           0, 40",
        "CRITICAL, MONITOR, /api/admin/users,     0, 55",
        "HIGH,     DENY,    /api/items,           0, 50",
        "HIGH,     DENY,    /admin,               0, 65",
        "HIGH,     ALERT,   /api/items,           0, 40",
        "HIGH,     ALERT,   /user/login,          0, 55",
        "HIGH,     MONITOR, /api/items,           0, 30",
        "HIGH,     MONITOR, /login,               0, 45",
        "MEDIUM,   DENY,    /api/items,           0, 40",
        "MEDIUM,   DENY,    /admin,               0, 55",
        "MEDIUM,   ALERT,   /api/items,           0, 30",
        "MEDIUM,   ALERT,   /login,               0, 45",
        "MEDIUM,   MONITOR, /api/items,           0, 20",
        "MEDIUM,   MONITOR, /admin,               0, 35",
        "LOW,      DENY,    /api/items,           0, 30",
        "LOW,      DENY,    /login,               0, 45",
        "LOW,      ALERT,   /api/items,           0, 20",
        "LOW,      ALERT,   /admin,               0, 35",
        "LOW,      MONITOR, /api/items,           0, 10",
        "LOW,      MONITOR, /login,               0, 25",
        "LOW,      MONITOR, /Admin,               0, 10",
        "LOW,      MONITOR, /LOGIN,               0, 10",
        "LOW,      MONITOR, /dashboard,           0, 10"
    })
    void calculate_severityActionPathMatrix(
            Severity severity, Action action, String path, long priorEventCount, int expectedScore) {
        assertThat(calculator.calculate(severity, action, path, priorEventCount)).isEqualTo(expectedScore);
    }

    @ParameterizedTest(name = "priorEventCount={0} → {1}")
    @CsvSource({
        "4, 10",
        "5, 25"
    })
    void calculate_repeatOffenderBoundary(long priorEventCount, int expectedScore) {
        assertThat(calculator.calculate(Severity.LOW, Action.MONITOR, "/api/items", priorEventCount))
                .isEqualTo(expectedScore);
    }

    @Test
    void calculate_maxRealisticScoreIs90() {
        assertThat(calculator.calculate(Severity.CRITICAL, Action.DENY, "/admin", 5)).isEqualTo(90);
    }

    @ParameterizedTest(name = "applyCap({0}) → {1}")
    @CsvSource({
        "90,  90",
        "100, 100",
        "101, 100"
    })
    void applyCap_capsAt100(int score, int expected) {
        assertThat(calculator.applyCap(score)).isEqualTo(expected);
    }
}
