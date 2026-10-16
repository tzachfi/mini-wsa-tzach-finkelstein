package com.akamai.miniwsa.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StatsQueryPoolBudgetValidator implements ApplicationRunner {

    private final DataSource dataSource;
    private final StatsProperties statsProperties;

    @Override
    public void run(ApplicationArguments args) {
        if (!(dataSource instanceof HikariDataSource hikari)) {
            return;
        }
        var poolSize = hikari.getMaximumPoolSize();
        var queryThreads = statsProperties.queryThreads();
        if (queryThreads >= poolSize) {
            throw new IllegalStateException(
                    "app.stats.query-threads (%d) must be smaller than spring.datasource.hikari.maximum-pool-size (%d)"
                            .formatted(queryThreads, poolSize));
        }
    }
}
