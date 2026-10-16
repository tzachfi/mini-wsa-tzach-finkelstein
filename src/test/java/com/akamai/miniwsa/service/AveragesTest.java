package com.akamai.miniwsa.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class AveragesTest {

    @Test
    void halfUpBoundaryRoundsToOneDecimal() {
        assertEquals(72.5, Averages.of(145, 2));
    }

    @Test
    void zeroCountIsNull() {
        assertNull(Averages.of(0, 0));
        assertNull(Averages.of(100, 0));
    }

    @Test
    void exactTenthIsUnchanged() {
        assertEquals(75.0, Averages.of(150, 2));
    }
}
