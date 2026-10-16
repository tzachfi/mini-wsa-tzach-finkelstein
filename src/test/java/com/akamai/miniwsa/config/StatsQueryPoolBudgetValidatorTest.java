package com.akamai.miniwsa.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class StatsQueryPoolBudgetValidatorTest {

    @Test
    void failsWhenQueryThreadsAreNotSmallerThanPool() {
        var hikari = mock(HikariDataSource.class);
        when(hikari.getMaximumPoolSize()).thenReturn(6);
        var validator = new StatsQueryPoolBudgetValidator(hikari, new StatsProperties(Duration.ofDays(31), 6, 6));

        assertThrows(IllegalStateException.class, () -> validator.run(null));
    }

    @Test
    void allowsQueryThreadsSmallerThanPool() {
        var hikari = mock(HikariDataSource.class);
        when(hikari.getMaximumPoolSize()).thenReturn(10);
        var validator = new StatsQueryPoolBudgetValidator(hikari, new StatsProperties(Duration.ofDays(31), 6, 6));

        assertDoesNotThrow(() -> validator.run(null));
    }

    @Test
    void skipsNonHikariDataSource() {
        var validator = new StatsQueryPoolBudgetValidator(
                mock(DataSource.class), new StatsProperties(Duration.ofDays(31), 32, 6));

        assertDoesNotThrow(() -> validator.run(null));
    }
}
