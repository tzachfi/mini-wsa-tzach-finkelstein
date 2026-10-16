package com.akamai.miniwsa.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Averages {

    private Averages() {}

    public static Double of(long sum, long count) {
        if (count == 0) {
            return null;
        }
        return BigDecimal.valueOf(sum)
                .divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
