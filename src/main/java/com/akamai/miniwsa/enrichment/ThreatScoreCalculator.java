package com.akamai.miniwsa.enrichment;

import com.akamai.miniwsa.model.Action;
import com.akamai.miniwsa.model.Severity;
import org.springframework.stereotype.Component;

@Component
public class ThreatScoreCalculator {

    private static final int REPEAT_OFFENDER_THRESHOLD = 5;
    private static final int REPEAT_OFFENDER_BONUS = 15;
    private static final int SENSITIVE_PATH_BONUS = 15;

    public int calculate(Severity severity, Action action, String path, long priorEventCount) {
        var score = severityBase(severity) + actionBonus(action) + pathBonus(path) + repeatBonus(priorEventCount);
        return applyCap(score);
    }

    public int applyCap(int score) {
        return Math.min(100, score);
    }

    private static int severityBase(Severity severity) {
        return switch (severity) {
            case CRITICAL -> 40;
            case HIGH -> 30;
            case MEDIUM -> 20;
            case LOW -> 10;
        };
    }

    private static int actionBonus(Action action) {
        return switch (action) {
            case DENY -> 20;
            case ALERT -> 10;
            case MONITOR -> 0;
        };
    }

    private static int pathBonus(String path) {
        if (path.contains("/admin") || path.contains("/login")) {
            return SENSITIVE_PATH_BONUS;
        }
        return 0;
    }

    private static int repeatBonus(long priorEventCount) {
        if (priorEventCount + 1 > REPEAT_OFFENDER_THRESHOLD) {
            return REPEAT_OFFENDER_BONUS;
        }
        return 0;
    }
}
